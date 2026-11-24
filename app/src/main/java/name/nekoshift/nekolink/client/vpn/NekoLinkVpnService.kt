package name.nekoshift.nekolink.client.vpn
import name.nekoshift.nekolink.client.xray.XrayCoreManager

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

import name.nekoshift.nekolink.client.MainActivity
import name.nekoshift.nekolink.client.R
import name.nekoshift.nekolink.client.xray.XrayTestConfig

import libXray.LibXray

class NekoLinkVpnService : VpnService() {

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "nekolink_vpn"
        private const val NOTIFICATION_ID = 1
        const val ACTION_DISCONNECT = "name.nekoshift.nekolink.client.vpn.DISCONNECT"
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

    override fun onCreate() {
        super.onCreate()
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

        try {
            Log.i("NekoLinkVpnService", "libXray API version: ${XrayCoreManager.getApiVersion()}")
            Log.i("NekoLinkVpnService", "Xray core version: ${XrayCoreManager.getVersion()}")

            registerDialerController()
            createVpnInterface()
            val tunFd = vpnInterface?.fd
                ?: throw IllegalStateException(
                    "Файловый дескриптор TUN недоступен"
                )

            Log.i("NekoLinkVpnService", "TUN file descriptor: $tunFd")

            val xrayConfig = XrayTestConfig.createTunConfig(tunFd)

            XrayCoreManager.testConfig(xrayConfig)
            Log.i("NekoLinkVpnService", "Xray configuration test passed")

            XrayCoreManager.start(xrayConfig)
            Log.i("NekoLinkVpnService", "Xray core start requested")

            val isRunning = XrayCoreManager.isRunning()
            Log.i("NekoLinkVpnService", "Xray core running: $isRunning")

            if (!isRunning) {
                throw IllegalStateException("Ядро Xray не запущено")
            }
            VpnStateManager.updateState(VpnState.CONNECTED)
        } catch (e: Exception) {
            Log.e("NekoLinkVpnService", "Failed to initialize VPN service", e)
            VpnStateManager.updateState(VpnState.ERROR)
            stopSelf()
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

    override fun onDestroy() {
        stopXrayCore()
        try {
            vpnInterface?.let {
                it.close()
                Log.i("NekoLinkVpnService", "TUN interface closed")
            }
        } catch (e: Exception) {
            Log.e("NekoLinkVpnService", "Failed to close TUN interface", e)
        } finally {
            vpnInterface = null
        }

        Log.i("NekoLinkVpnService", "VPN service destroyed")

        if (VpnStateManager.state.value != VpnState.ERROR) {
            VpnStateManager.updateState(VpnState.DISCONNECTED)
        }

        super.onDestroy()
    }

    private fun stopXrayCore() {
        try {
            if (XrayCoreManager.isRunning()) {
                XrayCoreManager.stop()
                Log.i("NekoLinkVpnService", "Xray core stopped")
            }
        } catch (e: Exception) {
            Log.e("NekoLinkVpnService", "Failed to stop Xray core", e)
        }
    }

    private fun createVpnInterface() {
        vpnInterface = Builder()
            .setSession("NekoLink")
            .setMtu(1500)
            .addAddress("10.255.255.1", 30)
            .establish()

        if (vpnInterface == null) {
            throw IllegalStateException(
                "Не удалось создать виртуальный сетевой интерфейс"
            )
        }

        Log.i("NekoLinkVpnService", "TUN interface created")
    }

    private fun disconnectVpn() {
        VpnStateManager.updateState(VpnState.DISCONNECTING)

        Log.i("NekoLinkVpnService", "Disconnecting VPN")
        stopXrayCore()

        try {
            vpnInterface?.close()
            Log.i("NekoLinkVpnService", "TUN interface closed")
        } catch (e: Exception) {
            Log.e("NekoLinkVpnService", "Failed to close TUN interface", e)
        } finally {
            vpnInterface = null
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
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