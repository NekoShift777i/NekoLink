package name.nekoshift.nekolink.client.vpn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.IBinder
import android.util.Log
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.ParcelFileDescriptor

import androidx.core.app.NotificationCompat

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.cancelAndJoin

import name.nekoshift.nekolink.client.MainActivity
import name.nekoshift.nekolink.client.R
import name.nekoshift.nekolink.client.xray.XrayTestConfig
import name.nekoshift.nekolink.client.network.NetworkDnsResolver
import name.nekoshift.nekolink.client.xray.XrayCoreManager

import libXray.LibXray

class NekoLinkVpnService : VpnService() {

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "nekolink_vpn"
        private const val NOTIFICATION_ID = 1
        const val ACTION_DISCONNECT = "name.nekoshift.nekolink.client.vpn.DISCONNECT"

        private val xrayMutex = Mutex()
        // Область сопрограмм для завершения операций после уничтожения службы
        private val cleanupScope = CoroutineScope(
            SupervisorJob() + Dispatchers.Main
        )
        // Текущий экземпляр VPN-службы
        private var activeService: NekoLinkVpnService? = null
        // Задача отложенного освобождения ресурсов предыдущей службы
        private var cleanupJob: Job? = null
        // Результат освобождения ресурсов предыдущего экземпляра службы
        private var cleanupSuccessful = true
    }

    private fun registerDialerController() {
        LibXray.registerDialerController { fd ->
            val protected = protect(fd.toInt())

            if (protected) {
                Log.d(
                    "NekoLinkVpnService",
                    "Socket successfully protected: $fd"
                )
            } else {
                Log.e(
                    "NekoLinkVpnService",
                    "Failed to protect socket: $fd"
                )
            }

            protected
        }

        Log.i("NekoLinkVpnService", "Xray dialer controller registered")
    }

    private var vpnInterface: ParcelFileDescriptor? = null

    private val serviceScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main
    )

    private var connectionJob: Job? = null
    private var disconnectionJob: Job? = null
    private var resourcesReleased = false
    // Признак получения ответственности за ресурсы VPN
    private var ownsVpnResources = false

    override fun onCreate() {
        super.onCreate()
        activeService = this
        VpnStateManager.updateState(VpnState.CONNECTING)

        Log.i("NekoLinkVpnService", "VPN service created")

        createNotificationChannel()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                createNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(
                NOTIFICATION_ID,
                createNotification()
            )
        }

        connectionJob = serviceScope.launch {
            try {
                // Ожидание освобождения ресурсов предыдущего экземпляра службы
                cleanupJob?.let { previousCleanupJob ->
                    Log.i("NekoLinkVpnService", "Waiting for previous VPN service cleanup")

                    previousCleanupJob.join()

                    Log.i("NekoLinkVpnService", "Previous VPN service cleanup finished")
                }
                // Запрет инициализации при неудачной очистке предыдущей службы
                if (!cleanupSuccessful) {
                    Log.e("NekoLinkVpnService", "Previous VPN service cleanup failed")

                    VpnStateManager.updateState(VpnState.ERROR)

                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()

                    return@launch
                }
                ownsVpnResources = true
                Log.i(
                    "NekoLinkVpnService",
                    "libXray API version: ${XrayCoreManager.getApiVersion()}"
                )
                val xrayVersion = xrayMutex.withLock {
                    withContext(Dispatchers.IO) {
                        XrayCoreManager.getVersion()
                    }
                }

                Log.i("NekoLinkVpnService", "Xray core version: $xrayVersion")

                val outboundConfig = withContext(Dispatchers.IO) {
                    XrayTestConfig.loadOutboundConfig(
                        this@NekoLinkVpnService
                    )
                }

                val server = outboundConfig
                    .getJSONObject("settings")
                    .getJSONArray("vnext")
                    .getJSONObject(0)

                val hostname = server.getString("address")

                Log.i("NekoLinkVpnService", "Resolving VPN server: $hostname")
                // TODO: Удалить испытательную задержку после завершения шага 6.3
                delay(5000L)

                val serverIp = NetworkDnsResolver.resolveIpv4(
                    context = this@NekoLinkVpnService,
                    hostname = hostname
                )

                Log.i("NekoLinkVpnService", "VPN server resolved: $hostname -> $serverIp")

                server.put("address", serverIp)

                registerDialerController()
                createVpnInterface()

                val tunFd = vpnInterface?.fd
                    ?: throw IllegalStateException(
                        "Файловый дескриптор TUN недоступен"
                    )

                Log.i("NekoLinkVpnService", "TUN file descriptor: $tunFd")

                val xrayConfig = XrayTestConfig.createVlessTunConfig(
                    outboundConfig = outboundConfig,
                    tunFd = tunFd
                )

                xrayMutex.withLock {
                    withContext(Dispatchers.IO) {
                        XrayCoreManager.testConfig(xrayConfig)
                    }
                }
                Log.i("NekoLinkVpnService", "Xray configuration test passed")

                XrayCoreManager.start(xrayConfig)
                Log.i("NekoLinkVpnService", "Xray core start requested")

                val isRunning = XrayCoreManager.isRunning()
                Log.i("NekoLinkVpnService", "Xray core running: $isRunning")

                if (!isRunning) {
                    throw IllegalStateException("Ядро Xray не запущено")
                }

                VpnStateManager.updateState(VpnState.CONNECTED)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("NekoLinkVpnService", "Failed to initialize VPN service", e)

                if (!releaseVpnResources()) {
                    Log.e("NekoLinkVpnService", "VPN initialization cleanup failed")
                    VpnStateManager.updateState(VpnState.DISCONNECT_ERROR)
                } else {
                    VpnStateManager.updateState(VpnState.ERROR)

                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISCONNECT -> disconnectVpn()
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return super.onBind(intent)
    }

    override fun onRevoke() {
        Log.w("NekoLinkVpnService", "VPN permission revoked")

        serviceScope.launch {
            disconnectVpn()
        }
    }

    override fun onDestroy() {
        Log.i("NekoLinkVpnService", "VPN service destruction started")

        serviceScope.cancel()

        if (ownsVpnResources && !resourcesReleased) {
            cleanupSuccessful = false

            cleanupJob = cleanupScope.launch {
                Log.i("NekoLinkVpnService", "Deferred VPN resource cleanup started")

                connectionJob?.join()
                disconnectionJob?.join()

                cleanupSuccessful = releaseVpnResources()

                if (!cleanupSuccessful) {
                    Log.e("NekoLinkVpnService", "Deferred VPN resource cleanup failed")
                } else {
                    Log.i("NekoLinkVpnService", "Deferred VPN resource cleanup completed")
                }

                if (activeService == null) {
                    if (!cleanupSuccessful) {
                        VpnStateManager.updateState(VpnState.ERROR)
                    } else if (VpnStateManager.state.value != VpnState.ERROR) {
                        VpnStateManager.updateState(VpnState.DISCONNECTED)
                    }
                }
            }
        } else if (activeService === this) {
            if (VpnStateManager.state.value != VpnState.ERROR) {
                VpnStateManager.updateState(VpnState.DISCONNECTED)
            }
        }

        if (activeService === this) {
            activeService = null
        }

        Log.i("NekoLinkVpnService", "VPN service destroyed")

        super.onDestroy()
    }

    private fun stopXrayCore(): Boolean {
        return try {
            if (XrayCoreManager.isRunning()) {
                XrayCoreManager.stop()

                if (XrayCoreManager.isRunning()) {
                    throw IllegalStateException("Ядро Xray продолжает работать после остановки")
                }

                Log.i("NekoLinkVpnService", "Xray core stopped")
                Log.i("NekoLinkVpnService", "Xray core stop verified")
            }

            true
        } catch (e: Exception) {
            Log.e("NekoLinkVpnService", "Failed to stop Xray core", e)
            false
        }
    }

    private fun closeVpnInterface(): Boolean {
        return try {
            vpnInterface?.let {
                it.close()
                Log.i("NekoLinkVpnService", "TUN interface closed")
            }

            true
        } catch (e: Exception) {
            Log.e("NekoLinkVpnService", "Failed to close TUN interface", e)
            false
        } finally {
            vpnInterface = null
        }
    }

    private suspend fun releaseVpnResources(): Boolean = xrayMutex.withLock {
        if (resourcesReleased) {
            return@withLock true
        }
        // Служба не получала ответственность за ресурсы VPN
        if (!ownsVpnResources) {
            Log.i("NekoLinkVpnService", "VPN resource cleanup skipped: resources not owned")
            return@withLock true
        }

        var coreStopped = withContext(Dispatchers.IO) {
            stopXrayCore()
        }

        if (!coreStopped) {
            Log.w("NekoLinkVpnService", "Retrying Xray core stop")

            coreStopped = withContext(Dispatchers.IO) {
                stopXrayCore()
            }
        }

        if (!coreStopped) {
            Log.e("NekoLinkVpnService", "Xray core stop attempts exhausted")
            return@withLock false
        }

        val interfaceClosed = closeVpnInterface()

        resourcesReleased = interfaceClosed

        if (resourcesReleased) {
            ownsVpnResources = false
            Log.i("NekoLinkVpnService", "VPN resource cleanup completed")
        } else {
            Log.e("NekoLinkVpnService", "VPN resource cleanup failed")
        }

        return@withLock resourcesReleased
    }

    private fun createVpnInterface() {
        vpnInterface = Builder()
            .setSession("NekoLink")
            .setMtu(1500)
            .addAddress("10.255.255.1", 30)
            .addDnsServer("1.1.1.1")
            .addRoute("0.0.0.0", 0)
            .establish()

        if (vpnInterface == null) {
            throw IllegalStateException(
                "Не удалось создать виртуальный сетевой интерфейс"
            )
        }

        Log.i("NekoLinkVpnService", "TUN interface created")
    }

    private fun disconnectVpn() {
        if (disconnectionJob != null) {
            return
        }

        connectionJob?.cancel()
        VpnStateManager.updateState(VpnState.DISCONNECTING)

        disconnectionJob = serviceScope.launch {
            Log.i("NekoLinkVpnService", "Disconnecting VPN")

            connectionJob?.cancelAndJoin()
            connectionJob = null

            Log.i("NekoLinkVpnService", "VPN connection job completed")

            if (!releaseVpnResources()) {
                Log.e("NekoLinkVpnService", "VPN disconnection failed")
                VpnStateManager.updateState(VpnState.DISCONNECT_ERROR)
                disconnectionJob = null
                return@launch
            }

            Log.i("NekoLinkVpnService", "Removing foreground notification")
            stopForeground(STOP_FOREGROUND_REMOVE)
            Log.i("NekoLinkVpnService", "Foreground notification removed")

            Log.i("NekoLinkVpnService", "Requesting VPN service stop")
            stopSelf()
            Log.i("NekoLinkVpnService", "VPN service stop requested")
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "VPN-соединение",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Состояние VPN-соединения NekoLink"
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification() =
        NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("NekoLink")
            .setContentText("Служба VPN запущена")
            .setOngoing(true)
            .setContentIntent(createContentIntent())
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

    private fun createContentIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)

        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}