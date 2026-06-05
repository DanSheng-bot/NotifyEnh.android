package com.dansheng.notifyenh.util

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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

    const val ACTION_STOP_ALARM = "com.dansheng.notifyenh.ACTION_STOP_ALARM"
    const val ACTION_SNOOZE_ALARM = "com.dansheng.notifyenh.ACTION_SNOOZE_ALARM"
    const val EXTRA_TASK_ID = "extra_task_id"

    private var mediaPlayer: MediaPlayer? = null
    private var currentAlarmTaskId: Long? = null


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
                currentAlarmTaskId = taskId
                startAlarm(it)
            }
        }
    }

    fun startAlarm(taskEntity: TaskEntity) {
        currentAlarmTaskId = taskEntity.id
        Log.d(TAG, "Starting alarm for task: ${taskEntity.name}")
        _isAlarmRinging.value = true
        mainHandler.removeCallbacks(snoozeRunnable)
        mainHandler.removeCallbacks(timeoutRunnable)

        try {
            if (mediaPlayer == null) {
                val alarmUri: Uri = taskEntity.alarmRingtone?.toUri()
                    ?: (RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(App.instance, alarmUri)
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
                Log.d(TAG, "MediaPlayer started")
            }

            // Vibrate
            val vibrator = App.instance.getSystemService(Vibrator::class.java)
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 500), 0))
            Log.d(TAG, "Vibration started")

            showAlarmNotification(taskEntity)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting alarm", e)
        }

        // Auto stop after 1 minute if no response, then snooze for 5 minutes
        mainHandler.postDelayed(timeoutRunnable, 60 * 1000)
    }

    fun stopAlarm(isUserDismissed: Boolean) {
        _isAlarmRinging.value = false
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.stop()
                it.prepare() // Prepare for next time
            }
        }

        val vibrator = App.instance.getSystemService(Vibrator::class.java)
        vibrator.cancel()

        if (isUserDismissed) {
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
    fun showAlarmNotification(taskEntity: TaskEntity) {
        val taskName = taskEntity.name
        Log.d(TAG, "Showing alarm notification for: $taskName")
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
                putExtra(EXTRA_TASK_ID, taskEntity.id)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION
            }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            App.instance,
            0,
            fullScreenIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(App.instance, ALARM_CHANNEL_ID)
            .setContentTitle(App.instance.getString(R.string.alarm_title))
            .setContentText(App.instance.getString(R.string.alarm_active, taskName))
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
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