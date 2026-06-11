package com.dansheng.notifyenh.util

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import com.dansheng.notifyenh.App
import com.dansheng.notifyenh.App.Companion.ALARM_CHANNEL_ID
import com.dansheng.notifyenh.App.Companion.ALARM_NOTIFICATION_ID
import com.dansheng.notifyenh.R
import com.dansheng.notifyenh.data.AppDatabase
import com.dansheng.notifyenh.data.TaskEntity
import com.dansheng.notifyenh.service.NotifyEnhService
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object AlarmUtils {

    private val TAG = AlarmUtils::class.java.simpleName

    private val mainHandler = Handler(Looper.getMainLooper())
    private val alarmScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _isAlarmRinging = MutableStateFlow(false)
    val isAlarmRinging: StateFlow<Boolean> = _isAlarmRinging.asStateFlow()
    private val _alarmMsgList = MutableStateFlow<PersistentList<String>>(persistentListOf())
    val alarmMsgList: StateFlow<PersistentList<String>> = _alarmMsgList.asStateFlow()

    const val ACTION_STOP_ALARM = "com.dansheng.notifyenh.ACTION_STOP_ALARM"

    private var mediaPlayer: MediaPlayer? = null
    private var currentAlarmTaskId: Long? = null

    /**
     * 音量键按下广播接收器
     */
    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "android.media.VOLUME_CHANGED_ACTION") {
                if (_isAlarmRinging.value) {
                    Log.d(TAG, "Volume key pressed (detected via broadcast), stopping alarm")
                    stopAlarm(isUserDismissed = true)
                }
            }
        }
    }


    private val snoozeRunnable = Runnable {
        currentAlarmTaskId?.let { startAlarm(it) }
    }

    private val timeoutRunnable = Runnable {
        if (mediaPlayer?.isPlaying == true) {
            stopAlarm(isUserDismissed = false)
        }
    }

    fun startAlarm(taskId: Long) {
        alarmScope.launch {
            val database = AppDatabase.getDatabase(App.instance)
            val taskEntity = withContext(Dispatchers.IO) {
                database.taskDao().getTaskById(taskId)
            }
            taskEntity?.let {
                startAlarm(it)
            }
        }
    }

    fun startAlarm(taskEntity: TaskEntity) {
        currentAlarmTaskId = taskEntity.id
        if (!_alarmMsgList.value.contains(taskEntity.name)) {
            _alarmMsgList.value = _alarmMsgList.value.add(taskEntity.name)
        }
        Log.d(TAG, "Starting alarm for task: ${taskEntity.name}")
        _isAlarmRinging.value = true
        mainHandler.removeCallbacks(snoozeRunnable)
        mainHandler.removeCallbacks(timeoutRunnable)

        try {
            if (mediaPlayer == null) {
                mediaPlayer = MediaPlayer()
            } else {
                mediaPlayer?.reset()
            }

            val alarmUri: Uri = taskEntity.alarmRingtone?.toUri()
                ?: (RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))

            mediaPlayer?.apply {
                setDataSource(App.instance, alarmUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
                Log.d(TAG, "MediaPlayer started with URI: $alarmUri")
            }

            // Vibrate
            val vibrator = App.instance.getSystemService(Vibrator::class.java)
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 500), 0))
            Log.d(TAG, "Vibration started")

            showAlarmNotification()

            // Register volume observer when alarm starts
            val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
            App.instance.registerReceiver(volumeReceiver, filter)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting alarm", e)
        }

        // Auto stop after 1 minute if no response, then snooze for 5 minutes
        mainHandler.postDelayed(timeoutRunnable, 60 * 1000)
    }

    fun stopAlarm(isUserDismissed: Boolean) {
        if (_isAlarmRinging.value) {
            try {
                App.instance.unregisterReceiver(volumeReceiver)
            } catch (_: Exception) {
            }
        }

        _isAlarmRinging.value = false

        mediaPlayer?.release()
        mediaPlayer = null

        val vibrator = App.instance.getSystemService(Vibrator::class.java)
        vibrator.cancel()

        if (isUserDismissed) {
            _alarmMsgList.value = _alarmMsgList.value.clear()
            currentAlarmTaskId = null
            mainHandler.removeCallbacks(snoozeRunnable)
            mainHandler.removeCallbacks(timeoutRunnable)
            val manager = App.instance.getSystemService(NotificationManager::class.java)
            manager.cancel(ALARM_NOTIFICATION_ID)
        } else {
            // No response, schedule snooze for 5 minutes
            mainHandler.postDelayed(snoozeRunnable, 5 * 60 * 1000)
        }
    }

    @SuppressLint("FullScreenIntentPolicy")
    fun showAlarmNotification() {
        val stopIntent = Intent(App.instance, NotifyEnhService::class.java).apply {
            action = ACTION_STOP_ALARM
        }
        val stopPendingIntent = PendingIntent.getService(
            App.instance,
            0,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val fullScreenIntent =
            Intent(App.instance, com.dansheng.notifyenh.ui.AlarmActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION
            }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            App.instance,
            0,
            fullScreenIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(App.instance, ALARM_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(App.instance.getString(R.string.alarm_title))
            .setContentText(
                App.instance.getString(
                    R.string.alarm_active,
                    alarmMsgList.value.joinToString(",")
                )
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                App.instance.getString(R.string.stop_alarm),
                stopPendingIntent
            )
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        val manager = App.instance.getSystemService(NotificationManager::class.java)
        manager.notify(ALARM_NOTIFICATION_ID, notification)
        Log.d(TAG, "Notification posted with ID: $ALARM_NOTIFICATION_ID")
    }

    fun getRingtoneName(context: Context, alarmRingtone: String?): String {
        return if (alarmRingtone == null) {
            context.getString(R.string.default_ringtone)
        } else {
            try {
                val uri = alarmRingtone.toUri()
                var title: String? = null

                try {
                    val ringtone = RingtoneManager.getRingtone(context, uri)
                    title = ringtone?.getTitle(context)
                } catch (_: Exception) {
                }

                // If title looks like a filename, try to query MediaStore for the real title
                val isFilename = title == null || title.contains(Regex("[._][0-9]{2,4}$")) ||
                        title.startsWith("ringtone_", true) ||
                        title.startsWith("alarm_", true) ||
                        title.startsWith("notification_", true)

                if (isFilename && uri.scheme == "content") {
                    try {
                        context.contentResolver.query(
                            uri,
                            null,
                            null,
                            null,
                            null
                        )?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                // Try common title columns
                                val titleColumn = cursor.getColumnIndex("title")
                                val nameColumn = cursor.getColumnIndex("_display_name")

                                val mediaTitle =
                                    if (titleColumn != -1) cursor.getString(titleColumn) else null
                                val displayName =
                                    if (nameColumn != -1) cursor.getString(nameColumn) else null

                                val candidate = mediaTitle ?: displayName
                                if (!candidate.isNullOrBlank()) {
                                    // Strip extension if it's a filename
                                    title = if (candidate.contains('.')) {
                                        candidate.substringBeforeLast('.')
                                    } else {
                                        candidate
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {
                    }
                }

                // Cleanup title: replace underscores with spaces and capitalize
                if (title != null
                    && (title.startsWith("ringtone_", true)
                            || title.startsWith("alarm_", true))
                ) {
                    title = title.replace('_', ' ').replaceFirstChar { it.uppercase() }
                }

                title ?: context.getString(R.string.default_ringtone)
            } catch (_: Exception) {
                context.getString(R.string.default_ringtone)
            }
        }
    }

}