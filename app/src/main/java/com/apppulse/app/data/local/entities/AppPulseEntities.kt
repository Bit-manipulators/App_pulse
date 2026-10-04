package com.apppulse.app.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scan_runs")
data class ScanRunEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val durationMs: Long,
    val apiLevel: Int,
    val usageAvailable: Boolean,
    val storageAvailable: Boolean,
    val exitInfoAvailable: Boolean,
    val totalDeviceBytes: Long = 0,
    val freeDeviceBytes: Long = 0
)

@Entity(tableName = "app_snapshots")
data class AppSnapshotEntity(
    @PrimaryKey val packageName: String,
    val label: String,
    val versionName: String,
    val versionCode: Long,
    val targetSdk: Int,
    val installTime: Long,
    val updateTime: Long,
    val category: Int,
    val isSystem: Boolean
)

@Entity(tableName = "storage_stats")
data class StorageStatEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val scanId: Long,
    val appBytes: Long,
    val dataBytes: Long,
    val cacheBytes: Long,
    val totalBytes: Long
)

@Entity(tableName = "usage_stats")
data class UsageStatEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val scanId: Long,
    val lastUsed: Long,
    val foreground7dMs: Long,
    val foreground30dMs: Long,
    val sessionsCount: Int,
    val historyLimitDays: Int = 30
)

@Entity(tableName = "permission_stats")
data class PermissionStatEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val scanId: Long,
    val permission: String,
    val requested: Boolean,
    val granted: Boolean,
    val isSensitive: Boolean,
    val categoryName: String,
    val plainExplanation: String = "",
    val isUnusual: Boolean = false
)

@Entity(tableName = "exit_stats")
data class ExitStatEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val scanId: Long,
    val reason: String,
    val count30d: Int,
    val available: Boolean
)

@Entity(tableName = "config_indicators")
data class ConfigIndicatorEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val scanId: Long,
    val ruleId: String,
    val title: String,
    val severity: String, // "INFO", "WARN", "CRITICAL"
    val description: String,
    val remediationHint: String
)

@Entity(tableName = "score_results")
data class ScoreResultEntity(
    @PrimaryKey val packageName: String,
    val scanId: Long,
    val healthScore: Double,
    val healthBand: String,
    val subScoresJson: String,
    val impactScore: Double,
    val impactBand: String,
    val dominantDriver: String,
    val topReasonsJson: String
)

@Entity(tableName = "user_decisions")
data class UserDecisionEntity(
    @PrimaryKey val packageName: String,
    val decision: String, // "KEEP", "IGNORE"
    val timestamp: Long,
    val scoreHash: String
)
