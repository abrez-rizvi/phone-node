package com.androidbot.node.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.androidbot.node.AndroidBotApp
import com.androidbot.node.MainActivity
import com.androidbot.node.util.EventLogger
import com.androidbot.node.util.NetworkUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class NodeService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val notificationId = 1001
    private val channelId = "androidbot_node_service_channel"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        when (action) {
            ACTION_START -> {
                val ip = NetworkUtils.getLocalIpAddress()
                val port = 8080
                val notification = buildNotification("Server running at http://$ip:$port")
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        notificationId,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                } else {
                    startForeground(notificationId, notification)
                }

                serviceScope.launch {
                    try {
                        AndroidBotApp.instance.ktorServerManager.start(port)
                        EventLogger.log("SERVICE", "NodeService started foreground server.")
                    } catch (e: Exception) {
                        EventLogger.error("SERVICE", "Failed to launch server in service", e)
                        stopSelf()
                    }
                }
            }
            ACTION_STOP -> {
                serviceScope.launch {
                    AndroidBotApp.instance.ktorServerManager.stop()
                    EventLogger.log("SERVICE", "NodeService received STOP action.")
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.launch {
            AndroidBotApp.instance.ktorServerManager.stop()
        }
        serviceScope.cancel()
        EventLogger.log("SERVICE", "NodeService destroyed.")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "AndroidBot Server Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the embedded NAS and Agent runtime active in background"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("AndroidBot Node Active")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        const val ACTION_START = "com.androidbot.node.START"
        const val ACTION_STOP = "com.androidbot.node.STOP"

        fun startService(context: Context) {
            val intent = Intent(context, NodeService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, NodeService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
