package com.dansheng.notifyenh.ui.screens

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.TextUtils
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.dansheng.notifyenh.data.AppDatabase
import com.dansheng.notifyenh.data.TaskEntity
import com.dansheng.notifyenh.data.prefs.AppPreferences
import com.dansheng.notifyenh.data.prefs.ThemeMode
import com.dansheng.notifyenh.service.NotifyEnhService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val database = remember { AppDatabase.getDatabase(context) }
    val appPreferences = remember { AppPreferences(context) }

    val themeMode by appPreferences.themeModeFlow.collectAsState(initial = ThemeMode.SYSTEM)
    val retentionDays by appPreferences.retentionDaysFlow.collectAsState(initial = 7)
    var isPermissionGranted by remember { mutableStateOf(isNotificationServiceEnabled(context)) }
    var isServiceRunning by remember { mutableStateOf(NotifyEnhService.isServiceRunning) }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
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

    // 导出 Launcher
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                val tasks = database.taskDao().getAllTasksList()
                val json = Json.encodeToString(tasks)
                try {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(it)?.use { stream ->
                            stream.write(json.toByteArray())
                        }
                    }
                    Toast.makeText(context, "导出成功", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 导入 Launcher
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                try {
                    val content = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(it)?.use { stream ->
                            BufferedReader(InputStreamReader(stream)).readText()
                        }
                    }
                    val tasks = Json.decodeFromString<List<TaskEntity>>(content ?: "")
                    // 清除 ID 以便重新插入
                    val newTasks = tasks.map { it.copy(id = 0) }
                    database.taskDao().insertAll(newTasks)
                    Toast.makeText(context, "导入完成", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "导入失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
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
                                android.service.notification.NotificationListenerService.requestRebind(
                                    ComponentName(context, NotifyEnhService::class.java)
                                )
                                isServiceRunning = NotifyEnhService.isServiceRunning
                            } else {
                                NotifyEnhService.stopService()
                                isServiceRunning = false
                            }
                        }
                    }
                )
            },
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Text(
            text = "显示设置",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            color = MaterialTheme.colorScheme.primary
        )

        ThemeOption(
            title = "跟随系统",
            selected = themeMode == ThemeMode.SYSTEM,
            onClick = { scope.launch { appPreferences.setThemeMode(ThemeMode.SYSTEM) } }
        )
        ThemeOption(
            title = "浅色模式",
            selected = themeMode == ThemeMode.LIGHT,
            onClick = { scope.launch { appPreferences.setThemeMode(ThemeMode.LIGHT) } }
        )
        ThemeOption(
            title = "深色模式",
            selected = themeMode == ThemeMode.DARK,
            onClick = { scope.launch { appPreferences.setThemeMode(ThemeMode.DARK) } }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Text(
            text = "存储设置",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            color = MaterialTheme.colorScheme.primary
        )

        var showRetentionDialog by remember { mutableStateOf(false) }
        ListItem(
            headlineContent = { Text("记录保留时间") },
            supportingContent = { Text("当前：${retentionDays}天") },
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .clickable { showRetentionDialog = true }
        )

        if (showRetentionDialog) {
            val options = listOf(1, 3, 7, 30)
            AlertDialog(
                onDismissRequest = { showRetentionDialog = false },
                title = { Text("选择保留时间") },
                text = {
                    Column {
                        options.forEach { days ->
                            ListItem(
                                headlineContent = { Text("${days}天") },
                                trailingContent = {
                                    RadioButton(selected = retentionDays == days, onClick = null)
                                },
                                modifier = Modifier.clickable {
                                    scope.launch {
                                        appPreferences.setRetentionDays(days)
                                        showRetentionDialog = false
                                    }
                                }
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showRetentionDialog = false }) {
                        Text("关闭")
                    }
                }
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Text(
            text = "备份与恢复",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            color = MaterialTheme.colorScheme.primary
        )

        ListItem(
            headlineContent = { Text("导出任务") },
            supportingContent = { Text("将所有任务导出为 JSON 文件") },
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .clickable {
                    exportLauncher.launch("notify_enh_tasks.json")
                }
        )

        ListItem(
            headlineContent = { Text("导入任务") },
            supportingContent = { Text("从 JSON 文件恢复任务") },
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .clickable {
                    importLauncher.launch(
                        arrayOf(
                            "application/json",
                            "application/octet-stream",
                            "*/*"
                        )
                    )
                }
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

@Composable
fun ThemeOption(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        trailingContent = {
            RadioButton(selected = selected, onClick = null)
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

// 扩展属性，方便在 SettingsScreen 中使用 clickable
private fun Modifier.clickable(onClick: () -> Unit): Modifier = this.then(
    Modifier.clickable(onClick = onClick)
)
