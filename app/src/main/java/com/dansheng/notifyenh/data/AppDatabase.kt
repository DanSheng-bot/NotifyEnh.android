package com.dansheng.notifyenh.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [NotificationEntity::class, TaskEntity::class, LogEntity::class, ControlEntity::class, TaskControlCrossRef::class],
    version = 10
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun notificationDao(): NotificationDao
    abstract fun taskDao(): TaskDao
    abstract fun logDao(): LogDao
    abstract fun controlDao(): ControlDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null


        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "notify_enh_database"
                ).addMigrations(*DbMigrations.MIGRATIONS)
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
