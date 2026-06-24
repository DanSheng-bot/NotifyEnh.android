package com.dansheng.notifyenh.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import com.dansheng.notifyenh.R
import com.dansheng.notifyenh.data.TaskEntity
import com.dansheng.notifyenh.util.AlarmUtils
import com.dansheng.notifyenh.util.PermissionUtils.isFullScreenIntentPermissionGranted
import com.dansheng.notifyenh.util.PermissionUtils.isPostNotificationsPermissionGranted
import com.dansheng.notifyenh.util.TTS

@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun TaskEditDialog(
    task: TaskEntity?,
    onDismiss: () -> Unit,
    onConfirm: (TaskEntity) -> Unit
) {
    var name by remember { mutableStateOf(task?.name ?: "") }
    var packageName by remember { mutableStateOf(task?.packageName ?: "") }
    var titlePattern by remember { mutableStateOf(task?.titlePattern ?: "") }
    var contentPattern by remember { mutableStateOf(task?.contentPattern ?: "") }
    var isRegex by remember { mutableStateOf(task?.isRegex ?: false) }
    var actionCancel by remember { mutableStateOf(task?.actionCancel ?: false) }
    var actionTts by remember { mutableStateOf(task?.actionTts ?: false) }
    var actionAlarm by remember { mutableStateOf(task?.actionAlarm ?: false) }
    var alarmRingtone by remember { mutableStateOf(task?.alarmRingtone) }

    var showAppPicker by remember { mutableStateOf(false) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    val ringtonePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                result.data?.getParcelableExtra(
                    RingtoneManager.EXTRA_RINGTONE_PICKED_URI,
                    Uri::class.java
                )
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            alarmRingtone = uri?.toString()
        }
    }

    val currentRingtoneName = remember(alarmRingtone) {
        AlarmUtils.getRingtoneName(context, alarmRingtone)
    }

    if (showPermissionDialog) {
        /**
         * show permission dialog
         */
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text(stringResource(R.string.post_notif_permission)) },
            text = { Text(stringResource(R.string.alarm_permission_required)) },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionDialog = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        }
                        context.startActivity(intent)
                    }
                }) {
                    Text(stringResource(R.string.go_to_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    /**
     * Task Edit Dialog
     */
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        title = { Text(if (task == null) stringResource(R.string.add_task) else stringResource(R.string.edit_task)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.task_name)) },
                    modifier = Modifier.fillMaxWidth()
                )

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = packageName,
                        onValueChange = { packageName = it },
                        label = { Text(stringResource(R.string.package_filter)) },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = { showAppPicker = true }) {
                                Icon(
                                    Icons.Default.Menu,
                                    contentDescription = stringResource(R.string.select_app)
                                )
                            }
                        }
                    )

                    if (showAppPicker) {
                        AppPickerLoader(
                            onAppSelected = {
                                packageName = it
                                showAppPicker = false
                            },
                            onDismiss = { showAppPicker = false }
                        )
                    }
                }

                OutlinedTextField(
                    value = titlePattern,
                    onValueChange = { titlePattern = it },
                    label = { Text(stringResource(R.string.title_pattern)) },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = contentPattern,
                    onValueChange = { contentPattern = it },
                    label = { Text(stringResource(R.string.content_pattern)) },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isRegex = !isRegex }
                ) {
                    Checkbox(checked = isRegex, onCheckedChange = { isRegex = it })
                    Text(stringResource(R.string.use_regex))
                }
                Text(
                    stringResource(R.string.actions_label),
                    style = MaterialTheme.typography.labelLarge
                )
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { actionCancel = !actionCancel }
                        ) {
                            Checkbox(
                                checked = actionCancel,
                                onCheckedChange = { actionCancel = it })
                            Text(stringResource(R.string.action_cancel_notif))
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { actionTts = !actionTts }
                        ) {
                            Checkbox(checked = actionTts, onCheckedChange = { actionTts = it })
                            Text(stringResource(R.string.action_tts))
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (!actionAlarm && (!isPostNotificationsPermissionGranted(context)
                                            || !isFullScreenIntentPermissionGranted(context))
                                ) {
                                    showPermissionDialog = true
                                } else {
                                    actionAlarm = !actionAlarm
                                }
                            }
                    ) {
                        Checkbox(
                            checked = actionAlarm,
                            onCheckedChange = {
                                if (it && (!isPostNotificationsPermissionGranted(context)
                                            || !isFullScreenIntentPermissionGranted(context))
                                ) {
                                    showPermissionDialog = true
                                } else {
                                    actionAlarm = it
                                }
                            }
                        )
                        Text(stringResource(R.string.action_alarm))
                    }

                    if (actionTts) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, top = 4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        TTS.speak(context.getString(R.string.test_tts_message))
                                    }
                            ) {
                                Text(
                                    stringResource(R.string.test_tts),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.width(48.dp))
                            IconButton(onClick = {
                                TTS.speak(context.getString(R.string.test_tts_message))
                            }) {
                                Icon(
                                    Icons.Default.Notifications,
                                    contentDescription = stringResource(R.string.test_tts),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    if (actionAlarm) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, top = 4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        val intent =
                                            Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                                putExtra(
                                                    RingtoneManager.EXTRA_RINGTONE_TYPE,
                                                    RingtoneManager.TYPE_ALARM
                                                )
                                                putExtra(
                                                    RingtoneManager.EXTRA_RINGTONE_TITLE,
                                                    context.getString(R.string.select_ringtone)
                                                )
                                                putExtra(
                                                    RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                                                    alarmRingtone?.toUri()
                                                )
                                                putExtra(
                                                    RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT,
                                                    true
                                                )
                                                putExtra(
                                                    RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT,
                                                    false
                                                )
                                            }
                                        ringtonePickerLauncher.launch(intent)
                                    }
                            ) {
                                Column {
                                    Text(
                                        stringResource(R.string.select_ringtone),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        currentRingtoneName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            IconButton(onClick = { alarmRingtone = null }) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = stringResource(R.string.reset_default),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            IconButton(onClick = {
                                if (!isPostNotificationsPermissionGranted(context)
                                    || !isFullScreenIntentPermissionGranted(context)
                                ) {
                                    showPermissionDialog = true
                                } else {
                                    val testTask = TaskEntity(
                                        id = task?.id ?: 0,
                                        name = name.ifBlank { context.getString(R.string.action_alarm) },
                                        alarmRingtone = alarmRingtone,
                                        actionAlarm = true
                                    )
                                    AlarmUtils.startAlarm(testTask)
                                }
                            }) {
                                Icon(
                                    Icons.Default.Notifications,
                                    contentDescription = stringResource(R.string.test_alarm),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        TaskEntity(
                            id = task?.id ?: 0,
                            name = name,
                            packageName = packageName.ifBlank { null },
                            titlePattern = titlePattern.ifBlank { null },
                            contentPattern = contentPattern.ifBlank { null },
                            isRegex = isRegex,
                            actionCancel = actionCancel,
                            actionTts = actionTts,
                            actionAlarm = actionAlarm,
                            alarmRingtone = alarmRingtone,
                            isEnabled = task?.isEnabled ?: true
                        )
                    )
                },
                enabled = name.isNotBlank() && (titlePattern.isNotBlank() || contentPattern.isNotBlank() || actionCancel || actionTts || actionAlarm)
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}


@Composable
fun AppPickerLoader(
    onAppSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }

    val allApps = remember {
        val pm = context.packageManager
        pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 || (it.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0 }
            .sortedBy { it.loadLabel(pm).toString().lowercase() }
    }

    val filteredApps = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            allApps
        } else {
            val pm = context.packageManager
            allApps.filter {
                it.loadLabel(pm).toString().contains(searchQuery, ignoreCase = true) ||
                        it.packageName.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    /**
     * App Picker Dialog
     */
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        title = { Text(stringResource(R.string.select_app)) },
        text = {
            Column {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text(stringResource(R.string.search_app_placeholder)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = stringResource(R.string.clear)
                                )
                            }
                        }
                    },
                    singleLine = true
                )

                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    items(filteredApps) { app ->
                        val pm = context.packageManager
                        ListItem(
                            headlineContent = { Text(app.loadLabel(pm).toString()) },
                            supportingContent = { Text(app.packageName) },
                            modifier = Modifier.clickable { onAppSelected(app.packageName) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun TaskEditDialogPreview() {
    MaterialTheme {
        TaskEditDialog(
            task = TaskEntity(
                name = "Example Task",
                packageName = "com.example.app",
                titlePattern = "Alert",
                actionAlarm = true
            ),
            onDismiss = {},
            onConfirm = {}
        )
    }
}
