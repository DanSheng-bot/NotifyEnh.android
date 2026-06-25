package com.dansheng.notifyenh.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.insertSeparators
import androidx.paging.map
import com.dansheng.notifyenh.R
import com.dansheng.notifyenh.data.AppDatabase
import com.dansheng.notifyenh.data.NotificationEntity
import com.dansheng.notifyenh.data.TaskControlCrossRef
import com.dansheng.notifyenh.data.TaskEntity
import com.dansheng.notifyenh.service.NotifyEnhService
import com.dansheng.notifyenh.ui.components.VerticalScrollbar
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NotificationListScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val notifications = remember(searchQuery) {
        Pager(
            config = PagingConfig(
                pageSize = 20,
                prefetchDistance = 10,
                enablePlaceholders = true,
                initialLoadSize = 40
            )
        ) {
            if (searchQuery.isBlank()) {
                database.notificationDao().getAllNotificationsPaging()
            } else {
                database.notificationDao().searchNotificationsPaging(searchQuery)
            }
        }.flow.map { pagingData: PagingData<NotificationEntity> ->
            pagingData.map { NotificationUiModel.Item(it) as NotificationUiModel }
                .insertSeparators { before: NotificationUiModel?, after: NotificationUiModel? ->
                    val beforeItem = before as? NotificationUiModel.Item
                    val afterItem = after as? NotificationUiModel.Item

                    val beforeDate = beforeItem?.let {
                        SimpleDateFormat(
                            "yyyy-MM-dd",
                            Locale.getDefault()
                        ).format(Date(it.notification.postTime))
                    }
                    val afterDate = afterItem?.let {
                        SimpleDateFormat(
                            "yyyy-MM-dd",
                            Locale.getDefault()
                        ).format(Date(it.notification.postTime))
                    }

                    if (afterDate != null && (beforeDate == null || beforeDate != afterDate)) {
                        NotificationUiModel.Separator(afterDate)
                    } else {
                        null
                    }
                }
        }
    }.collectAsLazyPagingItems()

    // 使用 remember 而非 rememberLazyListState (默认用 rememberSaveAble)
    // 这样应用关闭重新打开时，滚动位置会自动重置到顶端
    val listState = remember { LazyListState() }
    val showBackToTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 2
        }
    }
    var notificationToTask by remember { mutableStateOf<NotificationEntity?>(null) }
    var menuNotification by remember { mutableStateOf<NotificationEntity?>(null) }
    var showSnoozeOptions by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        placeholder = {
                            Text(
                                stringResource(R.string.search_placeholder),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
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
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.more_options)
                            )
                        }
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.clear_all_notifications)) },
                                onClick = {
                                    showMoreMenu = false
                                    NotifyEnhService.clearAllNotifications()
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Clear,
                                        contentDescription = null
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.delete_all_records)) },
                                onClick = {
                                    showMoreMenu = false
                                    showClearConfirm = true
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = null
                                    )
                                }
                            )
                        }
                    }
                },
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (notifications.itemCount == 0) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (searchQuery.isEmpty()) stringResource(R.string.no_notifications) else stringResource(
                        R.string.no_matching_notifications
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        } else {
            Box(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = 16.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val itemCount = notifications.itemCount
                    for (index in 0 until itemCount) {
                        when (val item = notifications.peek(index)) {
                            is NotificationUiModel.Separator -> {
                                stickyHeader(key = "sep_${item.date}") {
                                    DateHeader(item.date)
                                }
                            }

                            is NotificationUiModel.Item -> {
                                item(key = item.notification.id) {
                                    // Trigger loading the actual item
                                    val actualItem =
                                        notifications[index] as? NotificationUiModel.Item
                                    if (actualItem != null) {
                                        NotificationItem(
                                            notification = actualItem.notification,
                                            onLongClick = {
                                                menuNotification = it
                                            }
                                        )
                                    }
                                }
                            }

                            null -> {
                                item(key = "placeholder_$index") {
                                    // 触发加载该索引的数据
                                    notifications[index]
                                    // 显示占位占位符（类似骨架屏）
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .size(height = 100.dp, width = 0.dp)
                                            .drawWithContent {
                                                drawRoundRect(
                                                    color = Color.LightGray.copy(alpha = 0.3f),
                                                    cornerRadius = CornerRadius(12.dp.toPx())
                                                )
                                            }
                                    )
                                }
                            }
                        }
                    }
                }

                // 可拖动的滚动条区域
                VerticalScrollbar(
                    state = listState,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )

                this@Column.AnimatedVisibility(
                    visible = showBackToTop,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                ) {
                    FloatingActionButton(
                        onClick = {
                            scope.launch {
                                listState.scrollToItem(0)
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowUp,
                            contentDescription = stringResource(R.string.back_to_top)
                        )
                    }
                }
            }
        }
    }
    }

    if (menuNotification != null) {
        ModalBottomSheet(
            onDismissRequest = {
                menuNotification = null
                showSnoozeOptions = false
            },
            sheetState = sheetState
        ) {
            val notification = menuNotification!!
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (showSnoozeOptions) stringResource(R.string.view_later) else (notification.title
                            ?: stringResource(R.string.no_title)),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 8.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (showSnoozeOptions) {
                        IconButton(onClick = { showSnoozeOptions = false }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                if (!showSnoozeOptions) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.view_later)) },
                        leadingContent = {
                            Icon(
                                Icons.Default.Notifications,
                                contentDescription = null
                            )
                        },
                        modifier = Modifier.combinedClickable(onClick = {
                            showSnoozeOptions = true
                        })
                    )
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.open_apk)) },
                        leadingContent = {
                            Icon(
                                Icons.AutoMirrored.Filled.ExitToApp,
                                contentDescription = null
                            )
                        },
                        modifier = Modifier.combinedClickable(onClick = {
                            val launchIntent =
                                context.packageManager.getLaunchIntentForPackage(notification.packageName)
                            if (launchIntent != null) context.startActivity(launchIntent)
                            menuNotification = null
                        })
                    )
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.create_task)) },
                        leadingContent = { Icon(Icons.Default.Add, contentDescription = null) },
                        modifier = Modifier.combinedClickable(onClick = {
                            notificationToTask = notification
                            menuNotification = null
                        })
                    )
                    ListItem(
                        headlineContent = {
                            Text(
                                stringResource(R.string.delete_record),
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        leadingContent = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        modifier = Modifier.combinedClickable(onClick = {
                            scope.launch { database.notificationDao().delete(notification) }
                            menuNotification = null
                        })
                    )
                } else {
                    val snoozeOptions = listOf(
                        R.string.snooze_15m to 15 * 60 * 1000L,
                        R.string.snooze_1h to 60 * 60 * 1000L,
                        R.string.snooze_3h to 3 * 60 * 60 * 1000L,
                        R.string.snooze_12h to 12 * 60 * 60 * 1000L,
                        R.string.snooze_tomorrow to 24 * 60 * 60 * 1000L
                    )
                    snoozeOptions.forEach { (stringRes, duration) ->
                        ListItem(
                            headlineContent = { Text(stringResource(stringRes)) },
                            leadingContent = {
                                Icon(
                                    Icons.Default.Notifications,
                                    contentDescription = null
                                )
                            },
                            modifier = Modifier.combinedClickable(onClick = {
                                notification.notificationKey?.let { key ->
                                    NotifyEnhService.snoozeNotification(key, duration)
                                }
                                menuNotification = null
                                showSnoozeOptions = false
                            })
                        )
                    }
                }
            }
        }
    }

    if (notificationToTask != null) {
        val defaultTaskName = stringResource(R.string.new_task)
        val initialTask = remember(notificationToTask, defaultTaskName) {
            TaskEntity(
                name = notificationToTask?.title ?: defaultTaskName,
                packageName = notificationToTask?.packageName,
                titlePattern = notificationToTask?.title ?: "",
                contentPattern = notificationToTask?.content ?: "",
                isRegex = false
            )
        }
        TaskEditDialog(
            task = initialTask,
            onDismiss = { notificationToTask = null },
            onConfirm = { newTask, selectedControlIds ->
                scope.launch {
                    val maxOrder = database.taskDao().getMaxSortOrder(newTask.packageName) ?: 0
                    val taskId = database.taskDao().insert(newTask.copy(sortOrder = maxOrder + 1))

                    selectedControlIds.forEach { controlId ->
                        database.controlDao().insertCrossRef(TaskControlCrossRef(taskId, controlId))
                    }
                    notificationToTask = null
                }
            }
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(stringResource(R.string.confirm_clear_title)) },
            text = { Text(stringResource(R.string.confirm_clear_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            database.notificationDao().deleteAll()
                            showClearConfirm = false
                        }
                    }
                ) {
                    Text(stringResource(R.string.confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
fun DateHeader(date: String) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 8.dp)
        ) {
            Text(
                text = date,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp)
            )
            HorizontalDivider(
                modifier = Modifier.padding(top = 4.dp),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.primaryContainer
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NotificationItem(
    notification: NotificationEntity,
    onLongClick: (NotificationEntity) -> Unit
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val timeString = timeFormat.format(Date(notification.postTime))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { /* Could expand or do nothing */ },
                onLongClick = { onLongClick(notification) }
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = notification.packageName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                if (notification.triggeredTaskId != null) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = stringResource(R.string.task_triggered),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(14.dp),
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                }
                Text(
                    text = timeString,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            Text(
                text = notification.title ?: stringResource(R.string.no_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            Text(
                text = notification.content ?: stringResource(R.string.no_content),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

sealed class NotificationUiModel {
    data class Item(val notification: NotificationEntity) : NotificationUiModel()
    data class Separator(val date: String) : NotificationUiModel()
}
