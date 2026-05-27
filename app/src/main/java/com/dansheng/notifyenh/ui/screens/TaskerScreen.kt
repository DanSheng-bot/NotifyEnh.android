package com.dansheng.notifyenh.ui.screens

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
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
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.unit.dp
import com.dansheng.notifyenh.data.AppDatabase
import com.dansheng.notifyenh.data.TaskEntity
import kotlinx.coroutines.launch

@Composable
fun TaskerScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }
    val tasks by database.taskDao().getAllTasks().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    
    var showAddDialog by remember { mutableStateOf(false) }
    var taskToEdit by remember { mutableStateOf<TaskEntity?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Text(
                text = "任务管理",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(16.dp)
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "添加任务")
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
                    text = "暂无任务",
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
                items(tasks, key = { it.id }) { task ->
                    TaskItem(
                        task = task,
                        onEdit = { taskToEdit = it },
                        onToggle = { enabled ->
                            scope.launch {
                                database.taskDao().update(task.copy(isEnabled = enabled))
                            }
                        },
                        onDelete = {
                            scope.launch {
                                database.taskDao().delete(task)
                            }
                        }
                    )
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
                if (!task.packageName.isNullOrBlank()) {
                    Text(
                        text = "应用: ${task.packageName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                Text(
                    text = "${if (task.isRegex) "正则" else "关键词"}: ${
                        listOfNotNull(
                            task.titlePattern?.let { "标题($it)" },
                            task.contentPattern?.let { "内容($it)" },
                            task.pattern.takeIf { it.isNotBlank() }?.let { "通用($it)" }
                        ).joinToString(" & ")
                    }",
                    style = MaterialTheme.typography.bodySmall
                )
                Row(modifier = Modifier.padding(top = 4.dp)) {
                    if (task.actionCancel) {
                        Text(
                            "取消通知 ", 
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (task.actionTts) {
                        Text(
                            "TTS朗读", 
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
            IconButton(onClick = { onEdit(task) }) {
                Icon(Icons.Default.Edit, contentDescription = "编辑")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "删除")
            }
            Switch(checked = task.isEnabled, onCheckedChange = onToggle)
        }
    }
}

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
    var pattern by remember { mutableStateOf(task?.pattern ?: "") }
    var isRegex by remember { mutableStateOf(task?.isRegex ?: false) }
    var actionCancel by remember { mutableStateOf(task?.actionCancel ?: false) }
    var actionTts by remember { mutableStateOf(task?.actionTts ?: false) }

    var showAppPicker by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (task == null) "添加任务" else "编辑任务") },
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
                    label = { Text("任务名称") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = packageName,
                        onValueChange = { packageName = it },
                        label = { Text("限制应用包名 (可选)") },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = { showAppPicker = true }) {
                                Icon(Icons.AutoMirrored.Filled.List, contentDescription = "选择应用")
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
                    label = { Text("标题匹配模式 (可选)") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = contentPattern,
                    onValueChange = { contentPattern = it },
                    label = { Text("内容匹配模式 (可选)") },
                    modifier = Modifier.fillMaxWidth()
                )

                if (pattern.isNotBlank()) {
                    OutlinedTextField(
                        value = pattern,
                        onValueChange = { pattern = it },
                        label = { Text("通用匹配模式 (旧版)") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = false
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isRegex, onCheckedChange = { isRegex = it })
                    Text("使用正则表达式")
                }
                Text("触发操作:", style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = actionCancel, onCheckedChange = { actionCancel = it })
                    Text("取消通知")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = actionTts, onCheckedChange = { actionTts = it })
                    Text("TTS朗读")
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
                            packageName = if (packageName.isBlank()) null else packageName,
                            titlePattern = if (titlePattern.isBlank()) null else titlePattern,
                            contentPattern = if (contentPattern.isBlank()) null else contentPattern,
                            pattern = pattern,
                            isRegex = isRegex,
                            actionCancel = actionCancel,
                            actionTts = actionTts,
                            isEnabled = task?.isEnabled ?: true
                        )
                    )
                },
                enabled = name.isNotBlank() && (titlePattern.isNotBlank() || contentPattern.isNotBlank() || pattern.isNotBlank())
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
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
    val apps = remember {
        val pm = context.packageManager
        pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 || (it.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0 }
            .sortedBy { it.loadLabel(pm).toString().lowercase() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择应用") },
        text = {
            LazyColumn(modifier = Modifier.height(400.dp)) {
                items(apps) { app ->
                    val pm = context.packageManager
                    ListItem(
                        headlineContent = { Text(app.loadLabel(pm).toString()) },
                        supportingContent = { Text(app.packageName) },
                        modifier = Modifier.clickable { onAppSelected(app.packageName) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
