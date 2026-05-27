package com.dansheng.notifyenh.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks")
    fun getAllTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE isEnabled = 1")
    suspend fun getEnabledTasks(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE isEnabled = 1 AND (packageName = :pkgName OR packageName IS NULL OR packageName = '')")
    suspend fun getEnabledTasksForPackage(pkgName: String): List<TaskEntity>

    @Query("SELECT * FROM tasks")
    suspend fun getAllTasksList(): List<TaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tasks: List<TaskEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskEntity)

    @Update
    suspend fun update(task: TaskEntity)

    @Delete
    suspend fun delete(task: TaskEntity)
}
