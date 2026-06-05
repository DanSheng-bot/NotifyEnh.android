package com.dansheng.notifyenh.ui

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dansheng.notifyenh.R
import com.dansheng.notifyenh.data.AppDatabase
import com.dansheng.notifyenh.data.TaskEntity
import com.dansheng.notifyenh.service.NotifyEnhService
import com.dansheng.notifyenh.ui.theme.NotifyEnhTheme
import com.dansheng.notifyenh.util.AlarmUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("AlarmActivity", "onCreate")
        turnScreenOnAndKeyguardOff()

        val taskId = intent.getLongExtra(AlarmUtils.EXTRA_TASK_ID, -1L)

        setContent {
            NotifyEnhTheme {
                val isRinging by AlarmUtils.isAlarmRinging.collectAsState()
                var task by remember { mutableStateOf<TaskEntity?>(null) }

                LaunchedEffect(taskId) {
                    if (taskId != -1L) {
                        val db = AppDatabase.getDatabase(this@AlarmActivity)
                        task = withContext(Dispatchers.IO) {
                            db.taskDao().getTaskById(taskId)
                        }
                    }
                }

                LaunchedEffect(isRinging) {
                    if (!isRinging) {
                        finish()
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    AlarmContent(
                        taskName = task?.name ?: stringResource(R.string.action_alarm),
                        onStop = {
                            stopAlarm()
                        }
                    )
                }
            }
        }
    }

    private fun stopAlarm() {
        val intent = Intent(this, NotifyEnhService::class.java).apply {
            action = "com.dansheng.notifyenh.ACTION_STOP_ALARM"
        }
        startService(intent)
        finish()
    }

    private fun turnScreenOnAndKeyguardOff() {
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        with(getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager) {
            requestDismissKeyguard(this@AlarmActivity, null)
        }
    }
}

@Composable
fun AlarmContent(taskName: String, onStop: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Notifications,
            contentDescription = null,
            modifier = Modifier.size(120.dp),
            tint = MaterialTheme.colorScheme.error
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.alarm_title),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onErrorContainer,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = taskName,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onErrorContainer
        )

        Spacer(modifier = Modifier.height(64.dp))

        Button(
            onClick = onStop,
            modifier = Modifier
                .size(width = 200.dp, height = 80.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
            )
        ) {
            Text(
                text = stringResource(R.string.stop_alarm),
                style = MaterialTheme.typography.headlineSmall
            )
        }
    }
}
