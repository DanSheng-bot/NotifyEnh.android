package com.dansheng.notifyenh.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Device rebooted, NotifyEnh is ready.")
            // NotificationListenerService is automatically started by the system 
            // if it was enabled by the user. 
            // This receiver can be used to trigger other background tasks if needed.
        }
    }
}
