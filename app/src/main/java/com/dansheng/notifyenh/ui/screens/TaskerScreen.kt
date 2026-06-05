package com.dansheng.notifyenh.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import com.dansheng.notifyenh.R
import com.dansheng.notifyenh.data.AppDatabase
import com.dansheng.notifyenh.data.TaskEntity
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

    var expandedPackage by remember { mutableStateOf<String?>(null) }
    var hasAutoExpanded by remember { mutableStateOf(false) }

    // Only auto-expand the first group when data is first loaded
    if (!hasAutoExpanded && groupedTasks.isNotEmpty()) {
        expandedPackage = groupedTasks.firstOrNull()?.first
        hasAutoExpanded = true
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
