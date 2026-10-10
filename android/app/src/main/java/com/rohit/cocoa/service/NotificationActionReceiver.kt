package com.rohit.cocoa.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.rohit.cocoa.CocoaApplication

class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_ALLOW = "com.rohit.cocoa.action.ALLOW"
        const val ACTION_DENY = "com.rohit.cocoa.action.DENY"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        val id = intent?.getStringExtra("id") ?: return
        val linkClient = CocoaApplication.linkClient

        when (intent.action) {
            ACTION_ALLOW -> {
                linkClient.sendApproval(id, approved = true)
            }
            ACTION_DENY -> {
                linkClient.sendApproval(id, approved = false, reason = "Rejected from lock screen")
            }
        }
    }
}
