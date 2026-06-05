package com.dansheng.notifyenh.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.dansheng.notifyenh.R
import com.dansheng.notifyenh.data.AppDatabase
import com.dansheng.notifyenh.data.TaskEntity
import com.dansheng.notifyenh.data.prefs.AppPreferences
import com.dansheng.notifyenh.data.prefs.ThemeMode
import com.dansheng.notifyenh.service.NotifyEnhService
import com.dansheng.notifyenh.ui.components.ChangelogDialog
import com.dansheng.notifyenh.util.AlarmUtils
import com.dansheng.notifyenh.util.BackupUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@SuppressLint("BatteryLife", "LocalContextGetResourceValueCall")
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val database = remember { AppDatabase.getDatabase(context) }
    val appPreferences = remember { AppPreferences(context) }

    val themeMode by appPreferences.themeModeFlow.collectAsState(initial = ThemeMode.SYSTEM)
    val persistentMode by appPreferences.persistentModeFlow.collectAsState(initial = false)
    val retentionDays by appPreferences.retentionDaysFlow.collectAsState(initial = 7)
    var isPermissionGranted by remember { mutableStateOf(isNotificationServiceEnabled(context)) }
    var isPostNotifGranted by remember { mutableStateOf(isPostNotificationsPermissionGranted(context)) }
    val isServiceRunning by NotifyEnhService.isServiceRunning.collectAsState()
    var isIgnoringBattery by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }
    val isAlarmRinging by AlarmUtils.isAlarmRinging.collectAsState()

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isPermissionGranted = isNotificationServiceEnabled(context)
                isPostNotifGranted = isPostNotificationsPermissionGranted(context)
                isIgnoringBattery = isIgnoringBatteryOptimizations(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val exportSuccessMsg = stringResource(R.string.export_success)
    val importCompletedMsg = stringResource(R.string.import_completed)
    val settingsSavedRestartMsg = stringResource(R.string.settings_saved_restart)

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
                        context.contentResolver.openOutputStream(it, "wt")?.use { stream ->
                            stream.write(json.toByteArray())
                        }
                    }
                    Toast.makeText(
                        context,
                        exportSuccessMsg,
                        Toast.LENGTH_SHORT
                    ).show()
                } catch (e: Exception) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.export_failed, e.message),
                        Toast.LENGTH_SHORT
                    ).show()
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
                    val newTasks = tasks.map { taskEntity ->
                        taskEntity.copy(id = 0)
                    }
                    database.taskDao().insertAll(newTasks)
                    Toast.makeText(
                        context,
                        importCompletedMsg,
                        Toast.LENGTH_SHORT
                    ).show()
                } catch (e: Exception) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.import_failed, e.message),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    val scrollState = rememberScrollState()

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier
                    .padding(16.dp)
                    .weight(1f)
            )

            if (isAlarmRinging) {
                IconButton(onClick = { AlarmUtils.stopAlarm(isUserDismissed = true) }) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = stringResource(R.string.stop_alarm),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.notif_service)) },
                supportingContent = {
                    val statusText = when {
                        !isPermissionGranted -> stringResource(R.string.service_unauthorized)
                        isServiceRunning -> stringResource(R.string.service_running)
                        else -> stringResource(R.string.service_auth_not_started)
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
                                    NotifyEnhService.tryReconnectService(context)
                                } else {
                                    NotifyEnhService.stopService()
                                }
                            }
                        }
                    )
                },
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.post_notif_permission)) },
                supportingContent = {
                    Text(
                        if (isPostNotifGranted) stringResource(R.string.permission_granted)
                        else stringResource(R.string.permission_not_granted)
                    )
                },
                trailingContent = {
                    Switch(
                        checked = isPostNotifGranted,
                        onCheckedChange = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                val intent =
                                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    }
                                context.startActivity(intent)
                            }
                        }
                    )
                },
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .clickable {
                        if (!isPostNotifGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            }
                            context.startActivity(intent)
                        }
                    }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text(
                text = stringResource(R.string.stability_settings),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.primary
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.persistent_mode)) },
                supportingContent = { Text(stringResource(R.string.persistent_mode_desc)) },
                trailingContent = {
                    Switch(
                        checked = persistentMode,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                appPreferences.setPersistentMode(enabled)
                                // 提示用户重启服务或自动处理
                                Toast.makeText(
                                    context,
                                    settingsSavedRestartMsg,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )
                },
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.ignore_battery)) },
                supportingContent = {
                    Text(
                        if (isIgnoringBattery) stringResource(R.string.ignored) else stringResource(
                            R.string.not_ignored
                        )
                    )
                },
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .clickable {
                        if (!isIgnoringBattery) {
                            try {
                                val intent =
                                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                        data = "package:${context.packageName}".toUri()
                                    }
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                val intent =
                                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                context.startActivity(intent)
                            }
                        }
                    }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text(
                text = stringResource(R.string.display_settings),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.primary
            )

            var showThemeDialog by remember { mutableStateOf(false) }
            val currentThemeText = when (themeMode) {
                ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
                ThemeMode.LIGHT -> stringResource(R.string.theme_light)
                ThemeMode.DARK -> stringResource(R.string.theme_dark)
            }

            ListItem(
                headlineContent = { Text(stringResource(R.string.theme_mode)) },
                supportingContent = { Text(currentThemeText) },
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .clickable { showThemeDialog = true }
            )

            if (showThemeDialog) {
                AlertDialog(
                    onDismissRequest = { showThemeDialog = false },
                    title = { Text(stringResource(R.string.select_theme_mode)) },
                    text = {
                        Column {
                            ThemeOptionItem(
                                title = stringResource(R.string.theme_system),
                                selected = themeMode == ThemeMode.SYSTEM,
                                onClick = {
                                    scope.launch {
                                        appPreferences.setThemeMode(ThemeMode.SYSTEM)
                                        showThemeDialog = false
                                    }
                                }
                            )
                            ThemeOptionItem(
                                title = stringResource(R.string.theme_light),
                                selected = themeMode == ThemeMode.LIGHT,
                                onClick = {
                                    scope.launch {
                                        appPreferences.setThemeMode(ThemeMode.LIGHT)
                                        showThemeDialog = false
                                    }
                                }
                            )
                            ThemeOptionItem(
                                title = stringResource(R.string.theme_dark),
                                selected = themeMode == ThemeMode.DARK,
                                onClick = {
                                    scope.launch {
                                        appPreferences.setThemeMode(ThemeMode.DARK)
                                        showThemeDialog = false
                                    }
                                }
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showThemeDialog = false }) {
                            Text(stringResource(R.string.close))
                        }
                    }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text(
                text = stringResource(R.string.storage_settings),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.primary
            )

            var showRetentionDialog by remember { mutableStateOf(false) }
            ListItem(
                headlineContent = { Text(stringResource(R.string.retention_time)) },
                supportingContent = {
                    Text(
                        stringResource(
                            R.string.current_retention_days,
                            retentionDays
                        )
                    )
                },
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .clickable { showRetentionDialog = true }
            )

            if (showRetentionDialog) {
                val options = listOf(1, 3, 7, 30, 999)
                AlertDialog(
                    onDismissRequest = { showRetentionDialog = false },
                    title = { Text(stringResource(R.string.select_retention_time)) },
                    text = {
                        Column {
                            options.forEach { days ->
                                ListItem(
                                    headlineContent = {
                                        Text(
                                            stringResource(
                                                R.string.days_count,
                                                days
                                            )
                                        )
                                    },
                                    trailingContent = {
                                        RadioButton(
                                            selected = retentionDays == days,
                                            onClick = null
                                        )
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
                            Text(stringResource(R.string.close))
                        }
                    }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text(
                text = stringResource(R.string.backup_restore),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.primary
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.export_tasks)) },
                supportingContent = { Text(stringResource(R.string.export_tasks_desc)) },
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .clickable {
                        val timestamp = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
                            .format(Date())
                        exportLauncher.launch("notify_enh_tasks_$timestamp.json")
                    }
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.import_tasks)) },
                supportingContent = { Text(stringResource(R.string.import_tasks_desc)) },
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

            ListItem(
                headlineContent = { Text(stringResource(R.string.restore_auto_backup)) },
                supportingContent = { Text(stringResource(R.string.restore_auto_backup_desc)) },
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .clickable {
                        scope.launch {
                            val success = BackupUtils.restoreFromAutoBackup(context)
                            if (success) {
                                Toast.makeText(context, importCompletedMsg, Toast.LENGTH_SHORT)
                                    .show()
                            } else {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.no_auto_backup_found),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text(
                text = stringResource(R.string.about),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.primary
            )

            var showChangelog by remember { mutableStateOf(false) }
            var showSoftwareDesc by remember { mutableStateOf(false) }

            ListItem(
                headlineContent = { Text(stringResource(R.string.software_desc)) },
                supportingContent = {
                    Text(
                        stringResource(R.string.software_desc_detail),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                },
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .clickable { showSoftwareDesc = true }
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.changelog)) },
                supportingContent = { Text(stringResource(R.string.changelog_desc)) },
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .clickable { showChangelog = true }
            )

            if (showChangelog) {
                ChangelogDialog(onDismiss = { showChangelog = false })
            }

            if (showSoftwareDesc) {
                AlertDialog(
                    onDismissRequest = { showSoftwareDesc = false },
                    title = { Text(stringResource(R.string.software_desc)) },
                    text = { Text(stringResource(R.string.software_desc_detail)) },
                    confirmButton = {
                        TextButton(onClick = { showSoftwareDesc = false }) {
                            Text(stringResource(R.string.confirm))
                        }
                    }
                )
            }
        }
    }
}

fun isPostNotificationsPermissionGranted(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
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

fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
    return powerManager.isIgnoringBatteryOptimizations(context.packageName)
}

@Composable
fun ThemeOptionItem(
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
