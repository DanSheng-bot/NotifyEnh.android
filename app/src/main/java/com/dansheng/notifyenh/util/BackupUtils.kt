package com.dansheng.notifyenh.util

import android.content.Context
import android.net.Uri
import android.util.Log
import com.dansheng.notifyenh.data.AppDatabase
import com.dansheng.notifyenh.data.TaskEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

object BackupUtils {
    private const val TAG = "BackupUtils"
    private const val AUTO_BACKUP_FILE_NAME = "tasks_auto_backup.json"

    suspend fun autoBackup(context: Context) {
        withContext(Dispatchers.IO) {
            try {
                val database = AppDatabase.getDatabase(context)
                val tasks = database.taskDao().getAllTasksList()
                val json = Json.encodeToString(tasks)

                val backupFile = File(context.filesDir, AUTO_BACKUP_FILE_NAME)
                backupFile.writeText(json)
                Log.d(TAG, "Auto backup successful: ${backupFile.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "Auto backup failed", e)
            }
        }
    }

    suspend fun restoreFromAutoBackup(context: Context): Boolean? {
        return withContext(Dispatchers.IO) {
            try {
                val backupFile = File(context.filesDir, AUTO_BACKUP_FILE_NAME)
                if (!backupFile.exists()) return@withContext null

                val json = backupFile.readText()
                val tasks = Json.decodeFromString<List<TaskEntity>>(json)
                val hasAlarmTask = tasks.any { it.actionAlarm }

                val database = AppDatabase.getDatabase(context)
                // Clear ID to insert as new tasks if needed, or just insert
                val newTasks = tasks.map { it.copy(id = 0) }
                database.taskDao().insertAll(newTasks)
                hasAlarmTask
            } catch (e: Exception) {
                Log.e(TAG, "Restore from auto backup failed", e)
                null
            }
        }
    }

    suspend fun exportTasks(context: Context, uri: Uri): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val database = AppDatabase.getDatabase(context)
                val tasks = database.taskDao().getAllTasksList()
                val json = Json.encodeToString(tasks)
                context.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
                    stream.write(json.toByteArray())
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Export tasks failed", e)
                Result.failure(e)
            }
        }
    }

    suspend fun importTasks(context: Context, uri: Uri): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val content = context.contentResolver.openInputStream(uri)?.use { stream ->
                    BufferedReader(InputStreamReader(stream)).readText()
                }
                val tasks = Json.decodeFromString<List<TaskEntity>>(content ?: "")
                val hasAlarmTask = tasks.any { it.actionAlarm }
                // 清除 ID 以便重新插入
                val newTasks = tasks.map { taskEntity ->
                    taskEntity.copy(id = 0)
                }
                val database = AppDatabase.getDatabase(context)
                database.taskDao().insertAll(newTasks)
                Result.success(hasAlarmTask)
            } catch (e: Exception) {
                Log.e(TAG, "Import tasks failed", e)
                Result.failure(e)
            }
        }
    }
}
