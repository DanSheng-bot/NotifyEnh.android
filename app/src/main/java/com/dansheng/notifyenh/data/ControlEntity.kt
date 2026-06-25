package com.dansheng.notifyenh.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
enum class ControlType {
    MANUAL, // 手动开关
    DND,    // 勿扰模式
    TIME    // 时间段
}

@Serializable
@Entity(tableName = "controls")
data class ControlEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val isEnabled: Boolean = true,
    val controlType: ControlType = ControlType.MANUAL,

    // DND 相关
    val dndBehavior: Int = 0, // 0: Silence if DND, 1: Execute if DND

    // Time 相关
    val startTime: String = "00:00",
    val endTime: String = "23:59",

    val sortOrder: Int = 0
)

@Entity(
    tableName = "task_control_cross_ref",
    primaryKeys = ["taskId", "controlId"],
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ControlEntity::class,
            parentColumns = ["id"],
            childColumns = ["controlId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class TaskControlCrossRef(
    val taskId: Long,
    val controlId: Long
)
