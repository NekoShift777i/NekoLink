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

import name.nekoshift.nekolink.client.MainActivity
import name.nekoshift.nekolink.client.R

class NekoLinkVpnService : VpnService() {

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "nekolink_vpn"
        private const val NOTIFICATION_ID = 1
        const val ACTION_DISCONNECT = "name.nekoshift.nekolink.client.vpn.DISCONNECT"
    }

    private var vpnInterface: ParcelFileDescriptor? = null

    override fun onCreate() {
        super.onCreate()
        VpnStateManager.updateState(VpnState.CONNECTING)

        Log.i("NekoLinkVpnService", "VPN service created")

        createNotificationChannel()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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
            createVpnInterface()
        } catch (e: Exception) {
            Log.e("NekoLinkVpnService", "Failed to create TUN interface", e)
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