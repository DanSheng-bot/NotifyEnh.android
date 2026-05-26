package com.dansheng.notifyenh.ui.screens

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.dansheng.notifyenh.service.NotifyEnhService

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var isPermissionGranted by remember { mutableStateOf(isNotificationServiceEnabled(context)) }
    var isServiceRunning by remember { mutableStateOf(NotifyEnhService.isServiceRunning) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isPermissionGranted = isNotificationServiceEnabled(context)
                isServiceRunning = NotifyEnhService.isServiceRunning
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = "设置",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(16.dp)
        )

        ListItem(
            headlineContent = { Text("通知监听服务") },
            supportingContent = {
                val statusText = when {
                    !isPermissionGranted -> "服务未授权，点击去授权"
                    isServiceRunning -> "服务正在运行"
                    else -> "服务已授权但未启动，点击尝试启动"
                }
                Text(statusText)
            },
            trailingContent = {
                Switch(
                    checked = isPermissionGranted && isServiceRunning,
                    onCheckedChange = { checked ->
                        if (!isPermissionGranted) {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        } else {
                            if (checked) {
                                // 尝试重新绑定服务
                                android.service.notification.NotificationListenerService.requestRebind(
                                    ComponentName(context, NotifyEnhService::class.java)
                                )
                                // 稍微延迟检查状态
                                isServiceRunning = NotifyEnhService.isServiceRunning
                            } else {
                                // 请求解绑并停止服务
                                NotifyEnhService.stopService()
                                isServiceRunning = false
                            }
                        }
                    }
                )
            },
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}

fun isNotificationServiceEnabled(context: Context): Boolean {
    val pkgName = context.packageName
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    if (!flat.isNullOrEmpty()) {
        val names = flat.split(":")
        for (name in names) {
            val cn = ComponentName.unflattenFromString(name)
            if (cn != null) {
                if (TextUtils.equals(pkgName, cn.packageName)) {
                    return true
                }
            }
        }
    }
    return false
}
