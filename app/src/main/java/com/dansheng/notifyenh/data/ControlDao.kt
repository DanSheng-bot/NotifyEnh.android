package com.dansheng.notifyenh.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ControlDao {
    @Query("SELECT * FROM controls ORDER BY sortOrder ASC")
    fun getAllControls(): Flow<List<ControlEntity>>

    @Query("SELECT * FROM controls ORDER BY sortOrder ASC")
    suspend fun getAllControlsList(): List<ControlEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(control: ControlEntity): Long

    @Update
    suspend fun update(control: ControlEntity)

    @Update
    suspend fun updateAll(controls: List<ControlEntity>)

    @Delete
    suspend fun delete(control: ControlEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCrossRef(crossRef: TaskControlCrossRef)

    @Delete
    suspend fun deleteCrossRef(crossRef: TaskControlCrossRef)

    @Query("DELETE FROM task_control_cross_ref WHERE taskId = :taskId")
    suspend fun deleteByTaskId(taskId: Long)

    @Query("SELECT * FROM controls INNER JOIN task_control_cross_ref ON controls.id = task_control_cross_ref.controlId WHERE task_control_cross_ref.taskId = :taskId")
    fun getControlsForTask(taskId: Long): Flow<List<ControlEntity>>

    @Query("SELECT * FROM controls INNER JOIN task_control_cross_ref ON controls.id = task_control_cross_ref.controlId WHERE task_control_cross_ref.taskId = :taskId")
    suspend fun getControlsForTaskList(taskId: Long): List<ControlEntity>
}
