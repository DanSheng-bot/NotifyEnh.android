package com.dansheng.notifyenh.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dansheng.notifyenh.R

data class ChangelogEntry(
    val versionName: String,
    val changes: List<String>
)

val changelogs = listOf(
    ChangelogEntry(
        "25.06.05",
        listOf(
            "新增：持续响铃（Alarm）功能，支持全屏提醒及自定义铃声",
            "新增：通知显示权限检查逻辑，确保响铃功能正常运行",
            "优化：任务编辑界面增加响铃测试及重置默认铃声功能",
            "优化：任务列表状态管理，编辑任务后保持当前的展开/折叠状态",
            "优化：设置界面增加快捷停止响铃按钮，防止误响无法关闭"
        )
    ),
    ChangelogEntry(
        "25.05.29",
        listOf(
            "优化通知记录显示,分页加载,按日期分组",
            "记录保持天数增加999天",
            "修复：强制重连逻辑，解决服务意外断开问题",
            "优化：界面国际化适配，支持中英文切换",
            "优化：移除扩展图标库，APK 体积大幅瘦身",
            "优化：首页搜索框交互，防止长文本换行"
        )
    ),
    ChangelogEntry(
        "25.05.22",
        listOf(
            "新增：任务按应用分组显示及手风琴折叠效果",
            "新增：通知触发任务后显示视觉标记",
            "新增：通知记录自动清理及保留天数设置",
            "新增：任务导出与导入功能",
            "优化：配置界面支持滚动，标题固定不动"
        )
    )
)

@Composable
fun ChangelogDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "更新日志",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(
                        text = "关注公众号: 开源阅读 获取软件更新",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                changelogs.forEachIndexed { index, entry ->
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        Text(
                            text = entry.versionName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        entry.changes.forEach { change ->
                            Text(
                                text = "• $change",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 8.dp, bottom = 2.dp)
                            )
                        }
                    }
                    if (index < changelogs.size - 1) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.confirm))
            }
        }
    )
}
