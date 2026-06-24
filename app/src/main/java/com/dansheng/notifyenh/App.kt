package com.dansheng.notifyenh

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.dansheng.notifyenh.util.CrashHandler
import com.dansheng.notifyenh.worker.ServiceCheckWorker

class App : Application() {

    companion object {
        lateinit var instance: App
            private set

        const val CHANNEL_ID = "notify_enh_service_channel"
        const val ALARM_CHANNEL_ID = "notify_enh_alarm_channel"
        const val NOTIFICATION_ID = 1001
        const val ALARM_NOTIFICATION_ID = 1002
    }


    override fun onCreate() {
        super.onCreate()
        instance = this
        CrashHandler(this)
        createNotificationChannel()
        ServiceCheckWorker.schedule(this)
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.fg_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.fg_channel_desc)
        }
        manager.createNotificationChannel(channel)

        // Using a different ID for alarm channel to ensure it's recreated with correct importance
        val alarmChannel = NotificationChannel(
            ALARM_CHANNEL_ID,
            getString(R.string.action_alarm),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            enableVibration(true)
            setBypassDnd(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(alarmChannel)
    }

}