package com.rohit.cocoa

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.rohit.cocoa.network.CocoaLinkClient

class CocoaApplication : Application() {

    companion object {
        const val CHANNEL_MOCHI_LIVE = "cocoa_mochi_live"
        const val CHANNEL_APPROVALS = "cocoa_approvals"
        const val CHANNEL_QUESTIONS = "cocoa_questions"

        lateinit var instance: CocoaApplication
            private set

        val linkClient: CocoaLinkClient by lazy {
            CocoaLinkClient(instance)
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val liveChannel = NotificationChannel(
                CHANNEL_MOCHI_LIVE,
                "Cocoa Live Agent Activity",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows ongoing status and Mochi companion state while agent is running"
                setShowBadge(false)
            }

            val approvalChannel = NotificationChannel(
                CHANNEL_APPROVALS,
                "Approval Requests",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Urgent alerts when the agent requires permission to run bash or edit files"
                enableVibration(true)
                setShowBadge(true)
            }

            val questionChannel = NotificationChannel(
                CHANNEL_QUESTIONS,
                "Agent Questions",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications when the agent asks a clarifying question"
                enableVibration(true)
                setShowBadge(true)
            }

            manager.createNotificationChannels(listOf(liveChannel, approvalChannel, questionChannel))
        }
    }
}
