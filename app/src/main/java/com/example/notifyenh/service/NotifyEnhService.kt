package com.example.notifyenh.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.notifyenh.data.AppDatabase
import com.example.notifyenh.data.NotificationEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NotifyEnhService: NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var database: AppDatabase

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getDatabase(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)
        // 提取通知信息
        val pkg = sbn.packageName
        val notification = sbn.notification
        val extras = notification.extras
        val title = extras.getString(android.app.Notification.EXTRA_TITLE, "")
        val text = extras.getString(android.app.Notification.EXTRA_TEXT, "")
        val postTime = sbn.postTime

        // 存入数据库
        serviceScope.launch {
            val entity = NotificationEntity(
                packageName = pkg,
                title = title,
                content = text,
                postTime = postTime
            )
            database.notificationDao().insert(entity)
        }
    }

}