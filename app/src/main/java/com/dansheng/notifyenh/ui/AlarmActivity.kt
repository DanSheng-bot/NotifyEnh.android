package com.dansheng.notifyenh.ui

import android.app.KeyguardManager
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dansheng.notifyenh.R
import com.dansheng.notifyenh.ui.theme.NotifyEnhTheme
import com.dansheng.notifyenh.util.AlarmUtils

class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("AlarmActivity", "onCreate")
        turnScreenOnAndKeyguardOff()
        handleIntent(intent)

        setContent {
            NotifyEnhTheme {
                val alarmMsgList by AlarmUtils.alarmMsgList.collectAsState()

                LaunchedEffect(alarmMsgList) {
                    if (alarmMsgList.isEmpty()) {
                        finish()
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    val packageName by AlarmUtils.currentAlarmPackageName.collectAsState()
                    AlarmContent(
                        onStop = {
                            stopAlarm()
                        },
                        onOpenApp = packageName?.let {
                            { stopAndOpenApp(it) }
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == AlarmUtils.ACTION_STOP_AND_OPEN_APP) {
            val pkg = intent.getStringExtra(AlarmUtils.EXTRA_PACKAGE_NAME)
            pkg?.let { stopAndOpenApp(it) }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            stopAlarm()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun stopAlarm() {
        AlarmUtils.stopAlarm(isUserDismissed = true)
        finish()
    }

    private fun stopAndOpenApp(packageName: String) {
        AlarmUtils.stopAlarm(isUserDismissed = true)
        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            launchIntent?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(this)
            }
        } catch (e: Exception) {
            Log.e("AlarmActivity", "Failed to open app: $packageName", e)
        }
        finish()
    }

    private fun turnScreenOnAndKeyguardOff() {
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        with(getSystemService(KEYGUARD_SERVICE) as KeyguardManager) {
            requestDismissKeyguard(this@AlarmActivity, null)
        }
    }
}

@Composable
fun AlarmContent(onStop: () -> Unit, onOpenApp: (() -> Unit)? = null) {
    val alarmMsgList by AlarmUtils.alarmMsgList.collectAsState()

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
            text = alarmMsgList.joinToString("\n"),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onErrorContainer,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(64.dp))

        onOpenApp?.let {
            Button(
                onClick = it,
                modifier = Modifier
                    .size(width = 200.dp, height = 80.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Text(
                    text = stringResource(R.string.open_trigger_app),
                    style = MaterialTheme.typography.headlineSmall
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

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
