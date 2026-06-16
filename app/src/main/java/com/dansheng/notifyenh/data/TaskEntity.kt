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
    val titlePattern: String? = null,
    val contentPattern: String? = null,
    val isRegex: Boolean = false,
    val actionCancel: Boolean = false,
    val actionTts: Boolean = false,
    val actionAlarm: Boolean = false,
    val alarmRingtone: String? = null,
    val isEnabled: Boolean = true,
    val sortOrder: Int = 0
)
