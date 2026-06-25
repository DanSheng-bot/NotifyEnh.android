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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.dansheng.notifyenh.R
import com.dansheng.notifyenh.data.ControlEntity

@Composable
fun ControlEditDialog(
    control: ControlEntity?,
    onDismiss: () -> Unit,
    onConfirm: (ControlEntity) -> Unit
) {
    var name by remember { mutableStateOf(control?.name ?: "") }
    var checkDnd by remember { mutableStateOf(control?.checkDnd ?: false) }
    var dndBehavior by remember { mutableIntStateOf(control?.dndBehavior ?: 0) }
    var checkTime by remember { mutableStateOf(control?.checkTime ?: false) }
    var startTime by remember { mutableStateOf(control?.startTime ?: "00:00") }
    var endTime by remember { mutableStateOf(control?.endTime ?: "23:59") }

    var startTimeError by remember { mutableStateOf(false) }
    var endTimeError by remember { mutableStateOf(false) }

    val timeRegex = Regex("^([0-1]?[0-9]|2[0-3]):[0-5][0-9]$")

    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        title = {
            Text(
                if (control == null) stringResource(R.string.add_control) else stringResource(
                    R.string.edit_control
                )
            )
        },
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
                    label = { Text(stringResource(R.string.control_name)) },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { checkDnd = !checkDnd }
                ) {
                    Checkbox(checked = checkDnd, onCheckedChange = { checkDnd = it })
                    Text(stringResource(R.string.check_dnd))
                }

                if (checkDnd) {
                    Column(modifier = Modifier.padding(start = 32.dp)) {
                        Text(
                            stringResource(R.string.dnd_behavior),
                            style = MaterialTheme.typography.labelMedium
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = dndBehavior == 0, onClick = { dndBehavior = 0 })
                            Text(stringResource(R.string.dnd_not_execute))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = dndBehavior == 1, onClick = { dndBehavior = 1 })
                            Text(stringResource(R.string.dnd_execute))
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { checkTime = !checkTime }
                ) {
                    Checkbox(checked = checkTime, onCheckedChange = { checkTime = it })
                    Text(stringResource(R.string.time_control))
                }

                if (checkTime) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 32.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = startTime,
                            onValueChange = {
                                startTime = it
                                startTimeError = !timeRegex.matches(it)
                            },
                            label = { Text(stringResource(R.string.start_time)) },
                            placeholder = { Text("00:00") },
                            isError = startTimeError,
                            supportingText = {
                                if (startTimeError) {
                                    Text(
                                        stringResource(R.string.invalid_time_format),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = endTime,
                            onValueChange = {
                                endTime = it
                                endTimeError = !timeRegex.matches(it)
                            },
                            label = { Text(stringResource(R.string.end_time)) },
                            placeholder = { Text("23:59") },
                            isError = endTimeError,
                            supportingText = {
                                if (endTimeError) {
                                    Text(
                                        stringResource(R.string.invalid_time_format),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            val isTimeValid =
                !checkTime || (timeRegex.matches(startTime) && timeRegex.matches(endTime))
            Button(
                onClick = {
                    onConfirm(
                        ControlEntity(
                            id = control?.id ?: 0,
                            name = name,
                            isEnabled = control?.isEnabled ?: true,
                            checkDnd = checkDnd,
                            dndBehavior = dndBehavior,
                            checkTime = checkTime,
                            startTime = startTime,
                            endTime = endTime,
                            sortOrder = control?.sortOrder ?: 0
                        )
                    )
                },
                enabled = name.isNotBlank() && isTimeValid
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
