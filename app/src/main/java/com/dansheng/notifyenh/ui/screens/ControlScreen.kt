package com.dansheng.notifyenh.ui.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dansheng.notifyenh.R
import com.dansheng.notifyenh.data.AppDatabase
import com.dansheng.notifyenh.data.ControlEntity
import com.dansheng.notifyenh.data.ControlType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }
    val controlsFromDb by database.controlDao().getAllControls()
        .collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var controls by remember { mutableStateOf(emptyList<ControlEntity>()) }

    LaunchedEffect(controlsFromDb) {
        controls = controlsFromDb
    }

    var showAddDialog by remember { mutableStateOf(false) }
    var controlToEdit by remember { mutableStateOf<ControlEntity?>(null) }

    var persistJob by remember { mutableStateOf<Job?>(null) }
    val lazyListState = rememberLazyListState()

    val reorderableLazyListState = rememberReorderableLazyListState(
        lazyListState = lazyListState,
        onMove = { from, to ->
            controls = controls.toMutableList().apply {
                add(to.index, removeAt(from.index))
            }

            persistJob?.cancel()
            persistJob = scope.launch {
                delay(1000)
                val updatedList = controls.mapIndexed { index, control ->
                    control.copy(sortOrder = index)
                }
                database.controlDao().updateAll(updatedList)
            }
        }
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.control_management),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.add_control),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        }
    ) { padding ->
        if (controlsFromDb.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.no_controls),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        } else {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(controls, key = { it.id }) { control ->
                    ReorderableItem(reorderableLazyListState, key = control.id) { isDragging ->
                        val elevation by animateDpAsState(
                            if (isDragging) 8.dp else 0.dp,
                            label = ""
                        )

                        ControlItem(
                            modifier = Modifier
                                .shadow(elevation)
                                .draggableHandle(),
                            control = control,
                            onEdit = { controlToEdit = it },
                            onToggle = { enabled ->
                                scope.launch {
                                    database.controlDao().update(control.copy(isEnabled = enabled))
                                }
                            },
                            onDelete = {
                                scope.launch {
                                    database.controlDao().delete(control)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog || controlToEdit != null) {
        ControlEditDialog(
            control = controlToEdit,
            onDismiss = {
                showAddDialog = false
                controlToEdit = null
            },
            onConfirm = { newControl ->
                scope.launch {
                    if (controlToEdit != null) {
                        database.controlDao().update(newControl)
                    } else {
                        database.controlDao().insert(newControl)
                    }
                    showAddDialog = false
                    controlToEdit = null
                }
            }
        )
    }
}

@Composable
fun ControlItem(
    modifier: Modifier = Modifier,
    control: ControlEntity,
    onEdit: (ControlEntity) -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (control.isEnabled) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = control.name,
                    style = MaterialTheme.typography.titleMedium
                )
                if (control.controlType == ControlType.DND) {
                    Text(
                        text = stringResource(R.string.check_dnd),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (control.controlType == ControlType.TIME) {
                    Text(
                        text = "${control.startTime} - ${control.endTime}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            if (control.controlType == ControlType.MANUAL) {
                Switch(
                    checked = control.isEnabled,
                    onCheckedChange = onToggle,
                    modifier = Modifier.scale(0.8f)
                )
            }

            IconButton(onClick = { onEdit(control) }) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit))
            }

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
