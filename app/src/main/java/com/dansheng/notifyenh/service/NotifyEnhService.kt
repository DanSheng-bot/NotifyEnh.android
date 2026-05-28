package com.dansheng.notifyenh.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import com.dansheng.notifyenh.MainActivity
import com.dansheng.notifyenh.R
import com.dansheng.notifyenh.data.AppDatabase
import com.dansheng.notifyenh.data.NotificationEntity
import com.dansheng.notifyenh.data.TaskEntity
import com.dansheng.notifyenh.data.prefs.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale

class NotifyEnhService : NotificationListenerService(), TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "NotifyEnhService"
        private const val CHANNEL_ID = "notify_enh_service_channel"
        private const val NOTIFICATION_ID = 1001

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

        private var instance: NotifyEnhService? = null

        fun stopService() {
            instance?.requestUnbind()
            _isServiceRunning.value = false
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var database: AppDatabase
    private lateinit var appPreferences: AppPreferences
    private var tts: TextToSpeech? = null
    private var isTtsInitialized = false

    override fun onListenerConnected() {
        super.onListenerConnected()
        _isServiceRunning.value = true
        instance = this
        Log.d(TAG, "Service connected")

        // 检查是否需要开启前台服务
        serviceScope.launch {
            if (appPreferences.persistentModeFlow.first()) {
                startForegroundService()
            }
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        _isServiceRunning.value = false
        instance = null
        Log.d(TAG, "Service disconnected")

        // 尝试重新绑定服务
        requestRebind(ComponentName(this, NotifyEnhService::class.java))
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

    private fun startForegroundService() {
        createNotificationChannel()

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("NotifyEnh 正在运行")
            .setContentText("监听通知中...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "服务运行通知",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "确保应用在后台稳定运行"
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)

        // 过滤常驻通知（如媒体播放、系统常驻服务等）
        if (!sbn.isClearable) {
            return
        }

        val pkg = sbn.packageName
        val notification = sbn.notification
        val extras = notification.extras
        val title = extras.getString(android.app.Notification.EXTRA_TITLE, "") ?: ""
        val content = extras.getString(android.app.Notification.EXTRA_TEXT, "") ?: ""
        val postTime = sbn.postTime

        if (title.isEmpty() && content.isEmpty()) {
            return
        }
        
        // 存入数据库
        serviceScope.launch {
            // 先处理任务以确定是否触发
            val triggeredTask = findTriggeredTask(sbn, title, content)

            val entity = NotificationEntity(
                packageName = pkg,
                title = title,
                content = content,
                postTime = postTime,
                triggeredTaskId = triggeredTask?.id
            )
            database.notificationDao().insert(entity)

            // 清理旧通知
            cleanupOldNotifications()

            // 如果触发了任务，执行操作
            triggeredTask?.let {
                handleAction(it, sbn, title, content)
            }
        }
    }

    private suspend fun findTriggeredTask(
        sbn: StatusBarNotification,
        title: String,
        content: String
    ): TaskEntity? {
        val enabledTasks = database.taskDao().getEnabledTasksForPackage(sbn.packageName)
        for (task in enabledTasks) {
            if (isMatch(task, title, content)) {
                return task
            }
        }
        return null
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

    private fun isMatch(task: TaskEntity, title: String, content: String): Boolean {
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

    private fun handleAction(
        task: TaskEntity,
        sbn: StatusBarNotification,
        title: String,
        content: String
    ) {
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
        _isServiceRunning.value = false
        instance = null
        tts?.stop()
        tts?.shutdown()
    }
}
