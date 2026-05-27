package com.dansheng.notifyenh.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface NotificationDao {
    @Insert
    suspend fun insert(notification: NotificationEntity)

    @Delete
    suspend fun delete(notification: NotificationEntity)

    @Query("SELECT * FROM notifications ORDER BY postTime DESC")
    fun getAllNotificationsFlow(): kotlinx.coroutines.flow.Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications WHERE title LIKE '%' || :searchQuery || '%' OR content LIKE '%' || :searchQuery || '%' OR packageName LIKE '%' || :searchQuery || '%' ORDER BY postTime DESC")
    fun searchNotifications(searchQuery: String): kotlinx.coroutines.flow.Flow<List<NotificationEntity>>

    @Query("DELETE FROM notifications WHERE postTime < :timestamp")
    suspend fun deleteOldNotifications(timestamp: Long)

    @Query("DELETE FROM notifications")
    suspend fun deleteAll()
}
