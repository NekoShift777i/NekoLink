package name.nekoshift.nekolink.client.vpn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import name.nekoshift.nekolink.client.MainActivity
import name.nekoshift.nekolink.client.R
import android.content.pm.ServiceInfo
import android.os.Build

class NekoLinkVpnService : VpnService() {

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "nekolink_vpn"
        private const val NOTIFICATION_ID = 1
    }

    override fun onCreate() {
        super.onCreate()

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
    }

    override fun onBind(intent: Intent?): IBinder? {
        return super.onBind(intent)
    }

    override fun onDestroy() {
        Log.i("NekoLinkVpnService", "VPN service destroyed")
        super.onDestroy()
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