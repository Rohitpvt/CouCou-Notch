package com.rohit.cocoa.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.rohit.cocoa.CocoaApplication
import com.rohit.cocoa.MainActivity
import com.rohit.cocoa.R
import com.rohit.cocoa.model.MochiMood
import kotlinx.coroutines.*

class MochiNotificationService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private val linkClient by lazy { CocoaApplication.linkClient }

    companion object {
        private const val NOTIFICATION_ID = 4791
        const val ACTION_START = "com.rohit.cocoa.action.START_SERVICE"
        const val ACTION_STOP = "com.rohit.cocoa.action.STOP_SERVICE"

        fun start(context: Context) {
            val intent = Intent(context, MochiNotificationService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, MochiNotificationService::class.java).apply {
                action = ACTION_STOP
            }
            context.stopService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundNotification()
        observeState()
        return START_STICKY
    }

    private fun startForegroundNotification() {
        val initialNotification = buildNotification("Mochi is resting", "Connected to Cocoa Desktop", MochiMood.IDLE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                initialNotification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, initialNotification)
        }
    }

    private fun observeState() {
        serviceScope.launch {
            linkClient.activeSession.collect { session ->
                val approvals = linkClient.approvals.value
                val title: String
                val text: String
                val mood: MochiMood

                if (approvals.isNotEmpty()) {
                    val req = approvals.first()
                    title = "Approval Needed: ${req.title}"
                    text = req.command ?: req.description
                    mood = MochiMood.WARNING
                } else if (session != null) {
                    title = "Mochi: ${session.taskTitle.ifEmpty { "Working..." }}"
                    text = session.currentAction
                    mood = session.mood
                } else {
                    title = "Cocoa Companion Active"
                    text = "Mochi is waiting for next task..."
                    mood = MochiMood.IDLE
                }

                val notification = buildNotification(title, text, mood)
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(NOTIFICATION_ID, notification)
            }
        }
    }

    private fun buildNotification(title: String, text: String, mood: MochiMood): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val moodEmoji = when (mood) {
            MochiMood.IDLE -> "🐱"
            MochiMood.HAPPY -> "✨"
            MochiMood.THINKING -> "🧠"
            MochiMood.CODING -> "⚡"
            MochiMood.SLEEPING -> "💤"
            MochiMood.WARNING -> "⚠️"
            MochiMood.ERROR -> "💥"
            MochiMood.CELEBRATING -> "🎉"
        }

        val builder = NotificationCompat.Builder(this, CocoaApplication.CHANNEL_MOCHI_LIVE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("$moodEmoji $title")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)

        // Add Quick Action button if approval is pending
        val pendingApproval = linkClient.approvals.value.firstOrNull()
        if (pendingApproval != null) {
            val allowIntent = Intent(this, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_ALLOW
                putExtra("id", pendingApproval.id)
            }
            val allowPending = PendingIntent.getBroadcast(
                this, 101, allowIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val denyIntent = Intent(this, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_DENY
                putExtra("id", pendingApproval.id)
            }
            val denyPending = PendingIntent.getBroadcast(
                this, 102, denyIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            builder.addAction(R.drawable.ic_notification, "✓ Allow", allowPending)
            builder.addAction(R.drawable.ic_notification, "✕ Deny", denyPending)
        }

        return builder.build()
    }

    override fun onDestroy() {
        serviceJob.cancel()
        super.onDestroy()
    }
}
