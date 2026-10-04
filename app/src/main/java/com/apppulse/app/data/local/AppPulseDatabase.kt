package com.apppulse.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.apppulse.app.data.local.dao.AppPulseDao
import com.apppulse.app.data.local.entities.*

@Database(
    entities = [
        ScanRunEntity::class,
        AppSnapshotEntity::class,
        StorageStatEntity::class,
        UsageStatEntity::class,
        PermissionStatEntity::class,
        ExitStatEntity::class,
        ConfigIndicatorEntity::class,
        ScoreResultEntity::class,
        UserDecisionEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppPulseDatabase : RoomDatabase() {
    abstract fun appPulseDao(): AppPulseDao

    companion object {
        @Volatile
        private var INSTANCE: AppPulseDatabase? = null

        fun getDatabase(context: Context): AppPulseDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppPulseDatabase::class.java,
                    "apppulse_database"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
