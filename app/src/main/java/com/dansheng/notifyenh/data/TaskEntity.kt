package com.dansheng.notifyenh.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "tasks",
    indices = [Index(value = ["packageName"])]
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val packageName: String? = null,
    val titlePattern: String? = null, // 新增：标题匹配模式
    val contentPattern: String? = null, // 新增：内容匹配模式
    val isRegex: Boolean = false,
    val actionCancel: Boolean = false,
    val actionTts: Boolean = false,
    val isEnabled: Boolean = true
)
