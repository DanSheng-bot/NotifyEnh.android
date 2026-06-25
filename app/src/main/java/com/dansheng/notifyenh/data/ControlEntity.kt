package com.dansheng.notifyenh.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "controls")
data class ControlEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val isEnabled: Boolean = true,
    val checkDnd: Boolean = false,
    val dndBehavior: Int = 0, // 0: Silence if DND, 1: Execute if DND
    val checkTime: Boolean = false,
    val startTime: String? = "00:00",
    val endTime: String? = "23:59",
    val sortOrder: Int = 0
)

@Entity(
    tableName = "task_control_cross_ref",
    primaryKeys = ["taskId", "controlId"]
)
data class TaskControlCrossRef(
    val taskId: Long,
    val controlId: Long
)
