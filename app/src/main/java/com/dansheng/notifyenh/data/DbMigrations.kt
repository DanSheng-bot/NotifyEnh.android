package com.dansheng.notifyenh.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DbMigrations {

    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE tasks ADD COLUMN actionAlarm INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE tasks ADD COLUMN alarmRingtone TEXT")
        }
    }

    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `app_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `time` INTEGER NOT NULL, `message` TEXT NOT NULL, `stackTrace` TEXT)")
        }
    }

    val MIGRATIONS = arrayOf(
        MIGRATION_4_5,
        MIGRATION_5_6,
        MIGRATION_6_7
    )

}