package com.dansheng.notifyenh.service

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import com.dansheng.notifyenh.App.Companion.CHANNEL_ID
import com.dansheng.notifyenh.App.Companion.NOTIFICATION_ID
import com.dansheng.notifyenh.MainActivity
import com.dansheng.notifyenh.R
import com.dansheng.notifyenh.data.AppDatabase
import com.dansheng.notifyenh.data.NotificationEntity
import com.dansheng.notifyenh.data.TaskEntity
import com.dansheng.notifyenh.data.prefs.AppPreferences
import com.dansheng.notifyenh.util.AlarmUtils
import com.dansheng.notifyenh.util.AlarmUtils.startAlarm
import com.dansheng.notifyenh.util.TTS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class NotifyEnhService : NotificationListenerService() {

    companion object {
        private const val TAG = "NotifyEnhService"

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

        private var instance: NotifyEnhService? = null

        fun stopService() {
            instance?.requestUnbind()
            _isServiceRunning.value = false
        }

        /**
         * 强制重启通知监听服务（解决系统杀死应用后服务无法自动恢复的问题）
         */
        fun tryReconnectService(context: Context) {
            val componentName = ComponentName(context, NotifyEnhService::class.java)
            val pm = context.packageManager

            // 方案：通过禁用并重新启用组件来“踢”一下系统服务管理器
            pm.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
            pm.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )

            // 额外尝试主动请求重连
            requestRebind(componentName)
            Log.d(TAG, "Attempted to hard reconnect NotificationListenerService")
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var database: AppDatabase
    private lateinit var appPreferences: AppPreferences

    // 内存中的通知查重缓存，Key: pkg|title|content, Value: postTime
    private val notificationCache = ConcurrentHashMap<String, Long>()

    private val mainHandler = Handler(Looper.getMainLooper())

    private val cleanNotificationCacheRunnable = Runnable {
        cleanNotificationCache()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            AlarmUtils.ACTION_STOP_ALARM -> {
                AlarmUtils.stopAlarm(isUserDismissed = true)
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

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
        tryReconnectService(this)
    }

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getDatabase(this)
        appPreferences = AppPreferences(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)

        // 1. 过滤常驻通知（如媒体播放、系统常驻服务等）
        if (!sbn.isClearable) {
            return
        }

        // 2. 过滤掉组摘要通知（Group Summary），避免重复记录
        if (sbn.notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY != 0) {
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

        // 存入数据库及处理任务
        serviceScope.launch {
            // 1. 先匹配任务，获取触发规则
            val triggeredTask = findTriggeredTask(sbn, title, content)

            // 2. 无论是否重复，如果任务要求取消通知，则立即执行取消
            if (triggeredTask?.actionCancel == true) {
                cancelNotification(sbn.key)
            }

            // 3. 查重逻辑：针对 数据库记录、TTS 和 响铃，2秒内视为重复
            val cacheKey = "$pkg|$title|$content"
            val lastTime = notificationCache[cacheKey]
            if (lastTime != null && (postTime - lastTime) < 2000) {
                Log.d(
                    TAG,
                    "Duplicate notification detected (in-memory), skipping recording/TTS/Alarm: $pkg"
                )
                return@launch
            }

            // 更新缓存并清理
            notificationCache[cacheKey] = postTime
            mainHandler.postDelayed(cleanNotificationCacheRunnable, 5000)

            // 4. 存入数据库
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

            // 5. 执行剩余操作 (TTS, 响铃)
            triggeredTask?.let {
                handleRemainingActions(it, title, content)
            }
        }
    }

    private fun handleRemainingActions(
        task: TaskEntity,
        title: String,
        content: String
    ) {
        if (task.actionTts) {
            val speechText = if (title.isNotBlank()) "$title: $content" else content
            TTS.speak(speechText)
        }

        if (task.actionAlarm) {
            startAlarm(task)
        }
    }

    private fun cleanNotificationCache() {
        // 定期清理超过 5 秒的缓存，防止内存无限增长
        val iterator = notificationCache.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (System.currentTimeMillis() - entry.value > 5000) {
                iterator.remove()
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
        // 匹配标题 (如果设置了标题模式)
        if (!task.titlePattern.isNullOrBlank()) {
            if (!checkPattern(task.titlePattern, title, task.isRegex)) {
                return false
            }
        }

        // 匹配内容 (如果设置了内容模式)
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
        } catch (_: Exception) {
            false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _isServiceRunning.value = false
        instance = null
        AlarmUtils.stopAlarm(true)
    }

    private fun startForegroundService() {
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
            .setContentTitle(getString(R.string.fg_service_title))
            .setContentText(getString(R.string.fg_service_text))
            .setSmallIcon(R.drawable.ic_notification)
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

}
