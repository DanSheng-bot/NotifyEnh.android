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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.dansheng.notifyenh.R
import com.dansheng.notifyenh.data.AppDatabase
import com.dansheng.notifyenh.data.TaskEntity
import com.dansheng.notifyenh.util.AlarmUtils
import com.dansheng.notifyenh.util.BackupUtils
import kotlinx.coroutines.launch

@Composable
fun TaskerScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }
    val tasks by database.taskDao().getAllTasks().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    
    var showAddDialog by remember { mutableStateOf(false) }
    var taskToEdit by remember { mutableStateOf<TaskEntity?>(null) }
    
    val groupedTasks = remember(tasks) {
        tasks.groupBy { it.packageName ?: "通用" }
            .toList()
            .sortedWith(compareBy { (packageName, _) ->
                if (packageName == "通用") "" else packageName
            })
    }

    val generalGroupTitle = stringResource(R.string.general_group)
    
    var expandedPackage by remember(groupedTasks) {
        mutableStateOf(groupedTasks.firstOrNull()?.first)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Text(
                text = stringResource(R.string.task_management),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(16.dp)
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_task))
            }
        }
    ) { padding ->
        if (tasks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.no_tasks),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                groupedTasks.forEach { (packageName, tasksInGroup) ->
                    val isExpanded = expandedPackage == packageName
                    val groupTitle = if (packageName == "通用") generalGroupTitle else packageName
                    
                    item(key = "header_$packageName") {
                        Card(
                            onClick = {
                                expandedPackage = if (isExpanded) null else packageName
                            },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                val context = LocalContext.current
                                val appName = remember(packageName) {
                                    if (packageName == "通用") null
                                    else {
                                        try {
                                            val pm = context.packageManager
                                            val info = pm.getApplicationInfo(packageName, 0)
                                            pm.getApplicationLabel(info).toString()
                                        } catch (_: Exception) {
                                            null
                                        }
                                    }
                                }
                                Text(
                                    text = if (appName != null) "$appName ($packageName)" else groupTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Icon(
                                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = if (isExpanded) stringResource(R.string.collapse) else stringResource(
                                        R.string.expand
                                    )
                                )
                            }
                        }
                    }

                    if (isExpanded) {
                        items(tasksInGroup, key = { it.id }) { task ->
                            TaskItem(
                                task = task,
                                onEdit = { taskToEdit = it },
                                onToggle = { enabled ->
                                    scope.launch {
                                        database.taskDao().update(task.copy(isEnabled = enabled))
                                        BackupUtils.autoBackup(context)
                                    }
                                },
                                onDelete = {
                                    scope.launch {
                                        database.taskDao().delete(task)
                                        BackupUtils.autoBackup(context)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog || taskToEdit != null) {
        TaskEditDialog(
            task = taskToEdit,
            onDismiss = {
                showAddDialog = false
                taskToEdit = null
            },
            onConfirm = { newTask ->
                scope.launch {
                    if (taskToEdit != null) {
                        database.taskDao().update(newTask)
                    } else {
                        database.taskDao().insert(newTask)
                    }
                    BackupUtils.autoBackup(context)
                    showAddDialog = false
                    taskToEdit = null
                }
            }
        )
    }
}

@Composable
fun TaskItem(
    task: TaskEntity,
    onEdit: (TaskEntity) -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (task.isEnabled) MaterialTheme.colorScheme.surfaceVariant 
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = task.name, style = MaterialTheme.typography.titleMedium)
                Row(modifier = Modifier.padding(top = 4.dp)) {
                    if (task.actionCancel) {
                        Text(
                            stringResource(R.string.action_cancel_notif) + " ", 
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (task.actionTts) {
                        Text(
                            stringResource(R.string.action_tts) + " ", 
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    if (task.actionAlarm) {
                        Text(
                            stringResource(R.string.action_alarm),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }
            IconButton(onClick = { onEdit(task) }) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
            }
            Switch(checked = task.isEnabled, onCheckedChange = onToggle)
        }
    }
}


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
            val uri =
                result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            alarmRingtone = uri?.toString()
        }
    }

    val currentRingtoneName = remember(alarmRingtone) {
        if (alarmRingtone == null) {
            context.getString(R.string.default_ringtone)
        } else {
            try {
                val ringtone = RingtoneManager.getRingtone(context, Uri.parse(alarmRingtone))
                ringtone.getTitle(context)
            } catch (e: Exception) {
                context.getString(R.string.default_ringtone)
            }
        }
    }

    if (showPermissionDialog) {
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

    if (showPermissionDialog) {
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

    AlertDialog(
        onDismissRequest = onDismiss,
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
                                if (!actionAlarm && !isPostNotificationsPermissionGranted(context)) {
                                    showPermissionDialog = true
                                } else {
                                    actionAlarm = !actionAlarm
                                }
                            }
                    ) {
                        Checkbox(
                            checked = actionAlarm,
                            onCheckedChange = {
                                if (it && !isPostNotificationsPermissionGranted(context)) {
                                    showPermissionDialog = true
                                } else {
                                    actionAlarm = it
                                }
                            }
                        )
                        Text(stringResource(R.string.action_alarm))
                    }

                    if (actionAlarm) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 32.dp, top = 4.dp)
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
                                if (!isPostNotificationsPermissionGranted(context)) {
                                    showPermissionDialog = true
                                } else {
                                    val testTask = TaskEntity(
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

    AlertDialog(
        onDismissRequest = onDismiss,
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

                LazyColumn(modifier = Modifier.height(400.dp)) {
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
