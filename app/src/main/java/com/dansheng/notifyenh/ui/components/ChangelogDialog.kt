package com.dansheng.notifyenh.ui.components

import android.text.util.Linkify
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.dansheng.notifyenh.R
import dev.jeziellago.compose.markdowntext.MarkdownText
import java.io.BufferedReader
import java.io.InputStreamReader

@Composable
fun ChangelogDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var changelogText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        try {
            val inputStream = context.assets.open("CHANGELOG.md")
            val reader = BufferedReader(InputStreamReader(inputStream))
            changelogText = reader.readText()
            reader.close()
        } catch (e: Exception) {
            changelogText = "Failed to load changelog: ${e.message}"
        }
    }

    AlertDialog(
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "关注公众号(开源阅读)",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                if (changelogText.isNotEmpty()) {
                    MarkdownText(
                        markdown = changelogText,
                        linkifyMask = Linkify.WEB_URLS,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
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
