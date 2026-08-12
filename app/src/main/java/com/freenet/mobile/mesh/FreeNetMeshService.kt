package com.freenet.mobile.mesh

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder

class FreeNetMeshService : Service() {

    override fun onCreate() {
        super.onCreate()
        // Use the app-wide bridge (also used by MainActivity/CallActivity)
        // rather than a second instance — two bridges would both try to
        // bind the same Wi-Fi LAN/BLE ports and fight each other.
        (application as com.freenet.mobile.FreeNetApp).bridge.start()

        val channelId = "freenet_mesh"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "FreeNet mesh",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }

        val notification: Notification =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(this, channelId)
                    .setContentTitle("FreeNet")
                    .setContentText("Local mesh is running")
                    .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                    .build()
            } else {
                Notification.Builder(this)
                    .setContentTitle("FreeNet")
                    .setContentText("Local mesh is running")
                    .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                    .build()
            }

        startForeground(1001, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int =
        START_STICKY

    override fun onDestroy() {
        // Deliberately does not stop the shared bridge here — MainActivity
        // may still be using it. Whoever calls stop() last (app process
        // death) is fine since transports fail closed.
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
