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
    @Query("SELECT * FROM tasks ORDER BY sortOrder ASC")
    fun getAllTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE isEnabled = 1 ORDER BY sortOrder ASC")
    suspend fun getEnabledTasks(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE isEnabled = 1 AND (packageName = :pkgName OR packageName IS NULL OR packageName = '') ORDER BY sortOrder ASC")
    suspend fun getEnabledTasksForPackage(pkgName: String): List<TaskEntity>

    @Query("SELECT * FROM tasks ORDER BY sortOrder ASC")
    suspend fun getAllTasksList(): List<TaskEntity>

    @Query("SELECT MAX(sortOrder) FROM tasks WHERE (packageName = :pkgName OR (packageName IS NULL AND (:pkgName IS NULL OR :pkgName = '')))")
    suspend fun getMaxSortOrder(pkgName: String?): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tasks: List<TaskEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskEntity): Long

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTaskById(id: Long): TaskEntity?

    @Update
    suspend fun update(task: TaskEntity)

    @Delete
    suspend fun delete(task: TaskEntity)
}
