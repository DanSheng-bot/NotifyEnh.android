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

    private val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE tasks ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE notifications ADD COLUMN notificationKey TEXT")
        }
    }

    private val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `controls` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `isEnabled` INTEGER NOT NULL, `checkDnd` INTEGER NOT NULL, `dndBehavior` INTEGER NOT NULL, `checkTime` INTEGER NOT NULL, `startTime` TEXT, `endTime` TEXT, `sortOrder` INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `task_control_cross_ref` (`taskId` INTEGER NOT NULL, `controlId` INTEGER NOT NULL, PRIMARY KEY(`taskId`, `controlId`))")
        }
    }

    private val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 1. 创建新表
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `controls_new` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`isEnabled` INTEGER NOT NULL, " +
                        "`controlType` TEXT NOT NULL, " +
                        "`dndBehavior` INTEGER NOT NULL, " +
                        "`startTime` TEXT NOT NULL, " +
                        "`endTime` TEXT NOT NULL, " +
                        "`sortOrder` INTEGER NOT NULL)"
            )

            // 2. 迁移数据
            // 逻辑：如果 checkDnd 为 1，则设为 DND；否则如果 checkTime 为 1，则设为 TIME；否则设为 MANUAL
            db.execSQL(
                "INSERT INTO controls_new (id, name, isEnabled, controlType, dndBehavior, startTime, endTime, sortOrder) " +
                        "SELECT id, name, isEnabled, " +
                        "CASE WHEN checkDnd = 1 THEN 'DND' WHEN checkTime = 1 THEN 'TIME' ELSE 'MANUAL' END, " +
                        "dndBehavior, " +
                        "IFNULL(startTime, '00:00'), " +
                        "IFNULL(endTime, '23:59'), " +
                        "sortOrder FROM controls"
            )

            // 3. 删除旧表并重命名新表
            db.execSQL("DROP TABLE controls")
            db.execSQL("ALTER TABLE controls_new RENAME TO controls")
        }
    }

    val MIGRATIONS = arrayOf(
        MIGRATION_4_5,
        MIGRATION_5_6,
        MIGRATION_6_7,
        MIGRATION_7_8,
        MIGRATION_8_9,
        MIGRATION_9_10,
        MIGRATION_10_11
    )

}