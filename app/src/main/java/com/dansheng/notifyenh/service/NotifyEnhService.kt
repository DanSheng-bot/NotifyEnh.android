package com.dansheng.notifyenh.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
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
        private const val ALARM_CHANNEL_ID = "notify_enh_alarm_channel"
        private const val NOTIFICATION_ID = 1001
        private const val ALARM_NOTIFICATION_ID = 1002

        private const val ACTION_STOP_ALARM = "com.dansheng.notifyenh.ACTION_STOP_ALARM"
        private const val ACTION_SNOOZE_ALARM = "com.dansheng.notifyenh.ACTION_SNOOZE_ALARM"
        private const val EXTRA_TASK_NAME = "extra_task_name"

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

        private val _isAlarmRinging = MutableStateFlow(false)
        val isAlarmRinging: StateFlow<Boolean> = _isAlarmRinging.asStateFlow()

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
    private var tts: TextToSpeech? = null
    private var isTtsInitialized = false

    private var mediaPlayer: MediaPlayer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentAlarmTaskName: String? = null
    private var currentAlarmRingtone: String? = null

    private val snoozeRunnable = Runnable {
        currentAlarmTaskName?.let { startAlarm(it, currentAlarmRingtone) }
    }

    private val timeoutRunnable = Runnable {
        if (mediaPlayer?.isPlaying == true) {
            stopAlarm(isUserDismissed = false)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_ALARM -> {
                stopAlarm(isUserDismissed = true)
            }

            ACTION_SNOOZE_ALARM -> {
                val taskName = intent.getStringExtra(EXTRA_TASK_NAME) ?: "Task"
                startAlarm(taskName, currentAlarmRingtone)
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

        val alarmChannel = NotificationChannel(
            ALARM_CHANNEL_ID,
            getString(R.string.action_alarm),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            setSound(null, null) // We handle sound ourselves
            enableVibration(true)
        }
        manager.createNotificationChannel(alarmChannel)
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
        } catch (_: Exception) {
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

        if (task.actionAlarm) {
            startAlarm(task.name, task.alarmRingtone)
        }
    }

    private fun startAlarm(taskName: String, ringtoneUri: String? = null) {
        currentAlarmTaskName = taskName
        currentAlarmRingtone = ringtoneUri
        _isAlarmRinging.value = true
        mainHandler.removeCallbacks(snoozeRunnable)
        mainHandler.removeCallbacks(timeoutRunnable)

        if (mediaPlayer == null) {
            val alarmUri: Uri = if (ringtoneUri != null) {
                Uri.parse(ringtoneUri)
            } else {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            }
            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@NotifyEnhService, alarmUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
            }
        }

        if (mediaPlayer?.isPlaying == false) {
            mediaPlayer?.start()
        }

        // Vibrate
        val vibrator = getSystemService(Vibrator::class.java)
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 500), 0))

        showAlarmNotification(taskName)

        // Auto stop after 1 minute if no response, then snooze for 5 minutes
        mainHandler.postDelayed(timeoutRunnable, 60 * 1000)
    }

    private fun stopAlarm(isUserDismissed: Boolean) {
        _isAlarmRinging.value = false
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.stop()
                it.prepare() // Prepare for next time
            }
        }

        val vibrator = getSystemService(Vibrator::class.java)
        vibrator.cancel()

        if (isUserDismissed) {
            mainHandler.removeCallbacks(snoozeRunnable)
            mainHandler.removeCallbacks(timeoutRunnable)
            val manager = getSystemService(NotificationManager::class.java)
            manager.cancel(ALARM_NOTIFICATION_ID)
        } else {
            // No response, schedule snooze for 5 minutes
            mainHandler.postDelayed(snoozeRunnable, 5 * 60 * 1000)
        }
    }

    private fun showAlarmNotification(taskName: String) {
        val stopIntent = Intent(this, NotifyEnhService::class.java).apply {
            action = ACTION_STOP_ALARM
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val fullScreenIntent =
            Intent(this, com.dansheng.notifyenh.ui.AlarmActivity::class.java).apply {
                putExtra(EXTRA_TASK_NAME, taskName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION
            }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            0,
            fullScreenIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, ALARM_CHANNEL_ID)
            .setContentTitle(getString(R.string.action_alarm))
            .setContentText(getString(R.string.alarm_active, taskName))
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.stop_alarm),
                stopPendingIntent
            )
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(ALARM_NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        _isServiceRunning.value = false
        instance = null
        tts?.stop()
        tts?.shutdown()
        mediaPlayer?.release()
        mediaPlayer = null
        mainHandler.removeCallbacksAndMessages(null)
    }
}
