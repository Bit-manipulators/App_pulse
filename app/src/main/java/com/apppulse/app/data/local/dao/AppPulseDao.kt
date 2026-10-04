package com.apppulse.app.data.local.dao

import androidx.room.*
import com.apppulse.app.data.local.entities.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AppPulseDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScanRun(scanRun: ScanRunEntity): Long

    @Query("SELECT * FROM scan_runs ORDER BY id DESC LIMIT 1")
    fun getLatestScanRun(): Flow<ScanRunEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAppSnapshots(snapshots: List<AppSnapshotEntity>)

    @Query("SELECT * FROM app_snapshots ORDER BY label ASC")
    fun getAllSnapshots(): Flow<List<AppSnapshotEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStorageStats(stats: List<StorageStatEntity>)

    @Query("SELECT * FROM storage_stats WHERE scanId = :scanId")
    suspend fun getStorageStatsForScan(scanId: Long): List<StorageStatEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsageStats(stats: List<UsageStatEntity>)

    @Query("SELECT * FROM usage_stats WHERE scanId = :scanId")
    suspend fun getUsageStatsForScan(scanId: Long): List<UsageStatEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPermissionStats(stats: List<PermissionStatEntity>)

    @Query("SELECT * FROM permission_stats WHERE packageName = :packageName ORDER BY isSensitive DESC, categoryName ASC")
    fun getPermissionsForApp(packageName: String): Flow<List<PermissionStatEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExitStats(stats: List<ExitStatEntity>)

    @Query("SELECT * FROM exit_stats WHERE packageName = :packageName LIMIT 1")
    suspend fun getExitStatForApp(packageName: String): ExitStatEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfigIndicators(indicators: List<ConfigIndicatorEntity>)

    @Query("SELECT * FROM config_indicators WHERE packageName = :packageName")
    fun getConfigIndicatorsForApp(packageName: String): Flow<List<ConfigIndicatorEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScoreResults(scores: List<ScoreResultEntity>)

    @Query("SELECT * FROM score_results WHERE packageName = :packageName")
    fun getScoreResultForApp(packageName: String): Flow<ScoreResultEntity?>

    @Query("SELECT * FROM score_results")
    fun getAllScoreResults(): Flow<List<ScoreResultEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserDecision(decision: UserDecisionEntity)

    @Query("SELECT * FROM user_decisions")
    fun getAllUserDecisions(): Flow<List<UserDecisionEntity>>

    @Query("SELECT * FROM user_decisions WHERE packageName = :packageName")
    suspend fun getUserDecision(packageName: String): UserDecisionEntity?

    @Query("DELETE FROM user_decisions WHERE packageName = :packageName")
    suspend fun removeUserDecision(packageName: String)

    @Query("DELETE FROM scan_runs")
    suspend fun clearScanRuns()

    @Query("DELETE FROM app_snapshots")
    suspend fun clearSnapshots()

    @Query("DELETE FROM storage_stats")
    suspend fun clearStorageStats()

    @Query("DELETE FROM usage_stats")
    suspend fun clearUsageStats()

    @Query("DELETE FROM permission_stats")
    suspend fun clearPermissionStats()

    @Query("DELETE FROM exit_stats")
    suspend fun clearExitStats()

    @Query("DELETE FROM config_indicators")
    suspend fun clearConfigIndicators()

    @Query("DELETE FROM score_results")
    suspend fun clearScoreResults()

    @Query("DELETE FROM user_decisions")
    suspend fun clearUserDecisions()

    @Transaction
    suspend fun clearAllLocalData() {
        clearScanRuns()
        clearSnapshots()
        clearStorageStats()
        clearUsageStats()
        clearPermissionStats()
        clearExitStats()
        clearConfigIndicators()
        clearScoreResults()
        clearUserDecisions()
    }
}
