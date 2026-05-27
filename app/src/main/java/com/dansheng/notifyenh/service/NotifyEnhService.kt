package com.dansheng.notifyenh.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.speech.tts.TextToSpeech
import android.util.Log
import com.dansheng.notifyenh.data.AppDatabase
import com.dansheng.notifyenh.data.NotificationEntity
import com.dansheng.notifyenh.data.TaskEntity
import com.dansheng.notifyenh.data.prefs.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale

class NotifyEnhService: NotificationListenerService(), TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "NotifyEnhService"
        var isServiceRunning = false
            private set
        
        private var instance: NotifyEnhService? = null

        fun stopService() {
            instance?.requestUnbind()
            isServiceRunning = false
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var database: AppDatabase
    private lateinit var appPreferences: AppPreferences
    private var tts: TextToSpeech? = null
    private var isTtsInitialized = false

    override fun onListenerConnected() {
        super.onListenerConnected()
        isServiceRunning = true
        instance = this
        Log.d(TAG, "Service connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isServiceRunning = false
        instance = null
        Log.d(TAG, "Service disconnected")
    }

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getDatabase(this)
        appPreferences = AppPreferences(this)
        tts = TextToSpeech(this, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.getDefault()
            isTtsInitialized = true
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)
        
        // 过滤常驻通知（如媒体播放、系统常驻服务等）
        if (sbn.isOngoing) {
            return
        }

        val pkg = sbn.packageName
        val notification = sbn.notification
        val extras = notification.extras
        val title = extras.getString(android.app.Notification.EXTRA_TITLE, "") ?: ""
        val content = extras.getString(android.app.Notification.EXTRA_TEXT, "") ?: ""
        val postTime = sbn.postTime

        // 存入数据库
        serviceScope.launch {
            val entity = NotificationEntity(
                packageName = pkg,
                title = title,
                content = content,
                postTime = postTime
            )
            database.notificationDao().insert(entity)
            
            // 清理旧通知
            cleanupOldNotifications()

            // 处理任务
            processTasks(sbn, title, content)
        }
    }

    private suspend fun cleanupOldNotifications() {
        try {
            val days = appPreferences.retentionDaysFlow.first()
            val cutoff = System.currentTimeMillis() - (days * 24L * 60L * 60L * 1000L)
            database.notificationDao().deleteOldNotifications(cutoff)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cleanup old notifications", e)
        }
    }

    private suspend fun processTasks(sbn: StatusBarNotification, title: String, content: String) {
        val enabledTasks = database.taskDao().getEnabledTasksForPackage(sbn.packageName)
        
        for (task in enabledTasks) {
            if (isMatch(task, title, content, sbn.packageName)) {
                handleAction(task, sbn, title, content)
            }
        }
    }

    private fun isMatch(task: TaskEntity, title: String, content: String, sbnPackage: String): Boolean {
        // 1. 如果标题和内容模式都为空，则认为不匹配（必须至少设置一个模式，或者包名匹配已经在外部过滤）
        // 实际上，如果包名匹配了，且没有设置任何模式，用户可能希望匹配该应用的所有通知。
        // 但根据之前的逻辑，至少要有一个匹配。
        if (task.titlePattern.isNullOrBlank() && task.contentPattern.isNullOrBlank()) {
            return true // 如果没有设置任何模式，只要包名匹配（已经在 DAO 过滤），就匹配所有
        }

        // 2. 匹配标题 (如果设置了标题模式)
        if (!task.titlePattern.isNullOrBlank()) {
            if (!checkPattern(task.titlePattern, title, task.isRegex)) {
                return false
            }
        }

        // 3. 匹配内容 (如果设置了内容模式)
        if (!task.contentPattern.isNullOrBlank()) {
            if (!checkPattern(task.contentPattern, content, task.isRegex)) {
                return false
            }
        }
        
        return true
    }

    private fun checkPattern(pattern: String, text: String, isRegex: Boolean): Boolean {
        return try {
            if (isRegex) {
                Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(text)
            } else {
                text.contains(pattern, ignoreCase = true)
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun handleAction(task: TaskEntity, sbn: StatusBarNotification, title: String, content: String) {
        if (task.actionCancel) {
            cancelNotification(sbn.key)
        }
        
        if (task.actionTts && isTtsInitialized) {
            val speechText = if (title.isNotBlank()) "$title: $content" else content
            tts?.speak(speechText, TextToSpeech.QUEUE_ADD, null, "notify_${sbn.postTime}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        instance = null
        tts?.stop()
        tts?.shutdown()
    }
}
