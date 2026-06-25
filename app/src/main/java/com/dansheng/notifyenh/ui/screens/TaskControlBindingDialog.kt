package com.dansheng.notifyenh.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dansheng.notifyenh.R
import com.dansheng.notifyenh.data.AppDatabase
import com.dansheng.notifyenh.data.ControlEntity
import com.dansheng.notifyenh.data.TaskEntity

@Composable
fun TaskControlBindingDialog(
    task: TaskEntity,
    onDismiss: () -> Unit,
    onConfirm: (List<Long>) -> Unit
) {
    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }
    var allControls by remember { mutableStateOf<List<ControlEntity>>(emptyList()) }
    var selectedControlIds by remember { mutableStateOf<List<Long>>(emptyList()) }
    val scrollState = rememberScrollState()

    LaunchedEffect(task) {
        allControls = database.controlDao().getAllControlsList()
        selectedControlIds = database.controlDao().getControlsForTaskList(task.id).map { it.id }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.bind_controls)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "${stringResource(R.string.task_name)}: ${task.name}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )

                if (allControls.isEmpty()) {
                    Text(
                        stringResource(R.string.no_controls),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    allControls.forEach { control ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedControlIds =
                                        if (selectedControlIds.contains(control.id)) {
                                            selectedControlIds - control.id
                                        } else {
                                            selectedControlIds + control.id
                                        }
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            Checkbox(
                                checked = selectedControlIds.contains(control.id),
                                onCheckedChange = { checked ->
                                    selectedControlIds = if (checked) {
                                        selectedControlIds + control.id
                                    } else {
                                        selectedControlIds - control.id
                                    }
                                }
                            )
                            Text(control.name)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedControlIds) }
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
