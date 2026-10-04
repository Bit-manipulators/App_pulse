package com.apppulse.app.data.repository

import android.content.Context
import android.os.Build
import com.apppulse.app.data.collector.*
import com.apppulse.app.data.local.AppPulseDatabase
import com.apppulse.app.data.local.entities.*
import com.apppulse.scoring.HealthScorer
import com.apppulse.scoring.ImpactScorer
import com.apppulse.scoring.PhoneHealthScorer
import com.apppulse.scoring.model.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

data class PhoneHealthState(
    val score: Double = 100.0,
    val storageHeadroomScore: Double = 100.0,
    val meanAppHealth: Double = 100.0,
    val totalDeviceBytes: Long = 0L,
    val freeDeviceBytes: Long = 0L,
    val usedDeviceBytes: Long = 0L,
    val potentiallyReviewableBytes: Long = 0L,
    val appsNeedingAttentionCount: Int = 0,
    val totalAppsCount: Int = 0,
    val lastScanTimestamp: Long = 0L,
    val isScanning: Boolean = false
)

data class AppCardData(
    val packageName: String,
    val label: String,
    val versionName: String,
    val targetSdk: Int,
    val category: Int,
    val isSystem: Boolean,
    val totalBytes: Long,
    val appBytes: Long,
    val dataBytes: Long,
    val cacheBytes: Long,
    val lastUsed: Long,
    val daysUnused: Int,
    val foreground30dMs: Long,
    val sessionsCount: Int,
    val healthScore: Double,
    val healthBand: String,
    val impactScore: Double,
    val impactBand: String,
    val dominantDriver: String,
    val topReasons: List<ScoreReason>,
    val subScores: SubScores,
    val isIgnored: Boolean,
    val isKept: Boolean,
    val permissions: List<PermissionStatEntity> = emptyList(),
    val indicators: List<ConfigIndicatorEntity> = emptyList(),
    val exitInfo: ExitStatEntity? = null
)

class AppPulseRepository(private val context: Context) {

    private val db = AppPulseDatabase.getDatabase(context)
    private val dao = db.appPulseDao()

    private val packageCollector = PackageCollector(context)
    private val storageCollector = StorageCollector(context)
    private val usageCollector = UsageCollector(context)
    private val permissionCollector = PermissionCollector(context)
    private val exitCollector = ExitCollector(context)
    private val configCollector = ConfigCollector(context)
    private val gson = Gson()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    fun hasUsageAccess(): Boolean = usageCollector.hasUsageAccess()

    val latestScanRunFlow: Flow<ScanRunEntity?> = dao.getLatestScanRun()
    val snapshotsFlow: Flow<List<AppSnapshotEntity>> = dao.getAllSnapshots()
    val scoreResultsFlow: Flow<List<ScoreResultEntity>> = dao.getAllScoreResults()
    val decisionsFlow: Flow<List<UserDecisionEntity>> = dao.getAllUserDecisions()

    suspend fun performScan(): Long = withContext(Dispatchers.IO) {
        _isScanning.value = true
        val startTime = System.currentTimeMillis()

        try {
            // 1. App Inventory
            val pkgResult = packageCollector.collectLaunchableApps()
            val snapshots = pkgResult.snapshots
            val pkgNames = snapshots.map { it.packageName }

            // 2. Storage Stats & Device Info
            val devInfo = storageCollector.getDeviceStorageInfo()
            val scanRunEntity = ScanRunEntity(
                timestamp = startTime,
                durationMs = 0L,
                apiLevel = Build.VERSION.SDK_INT,
                usageAvailable = usageCollector.hasUsageAccess(),
                storageAvailable = false,
                exitInfoAvailable = false,
                totalDeviceBytes = devInfo.totalBytes,
                freeDeviceBytes = devInfo.freeBytes
            )
            val scanId = dao.insertScanRun(scanRunEntity)
            dao.insertAppSnapshots(snapshots)

            // Collect measurements
            val storageResult = storageCollector.collectStorageStats(scanId, pkgNames)
            val usageResult = usageCollector.collectUsageStats(scanId, snapshots)
            val permissionStats = permissionCollector.collectPermissionsForApps(scanId, snapshots)
            val exitResult = exitCollector.collectExitStats(scanId, pkgNames)
            val configIndicators = configCollector.collectIndicatorsForApps(scanId, snapshots)

            dao.insertStorageStats(storageResult.stats)
            dao.insertUsageStats(usageResult.stats)
            dao.insertPermissionStats(permissionStats)
            dao.insertExitStats(exitResult.stats)
            dao.insertConfigIndicators(configIndicators)

            // Index stats by package
            val storageMap = storageResult.stats.associateBy { it.packageName }
            val usageMap = usageResult.stats.associateBy { it.packageName }
            val permMap = permissionStats.groupBy { it.packageName }
            val exitMap = exitResult.stats.associateBy { it.packageName }
            val configMap = configIndicators.groupBy { it.packageName }

            val scoreEntities = mutableListOf<ScoreResultEntity>()

            // 3. Compute Deterministic Health & Impact Scores
            for (app in snapshots) {
                val st = storageMap[app.packageName]
                val us = usageMap[app.packageName]
                val perms = permMap[app.packageName] ?: emptyList()
                val ex = exitMap[app.packageName]
                val cfg = configMap[app.packageName] ?: emptyList()

                val now = System.currentTimeMillis()
                val lastUsedTime = us?.lastUsed ?: app.installTime
                val daysUnused = TimeUnit.MILLISECONDS.toDays(now - lastUsedTime).toInt().coerceAtLeast(0)
                val daysSinceUpdate = TimeUnit.MILLISECONDS.toDays(now - app.updateTime).toInt().coerceAtLeast(0)

                // Map sensitive permissions to scoring input
                val permInputs = perms.mapNotNull { p ->
                    val enumPerm = when (p.categoryName) {
                        "SMS" -> SensitivePermission.SMS_OR_CALL_LOG
                        "Call Log" -> SensitivePermission.SMS_OR_CALL_LOG
                        "Contacts" -> SensitivePermission.CONTACTS
                        "Microphone" -> SensitivePermission.MICROPHONE
                        "Location" -> if (p.permission.contains("BACKGROUND")) SensitivePermission.BACKGROUND_LOCATION else SensitivePermission.LOCATION
                        "Body Sensors" -> SensitivePermission.BODY_SENSORS
                        "Camera" -> SensitivePermission.CAMERA
                        "Calendar" -> SensitivePermission.CALENDAR
                        "Phone State" -> SensitivePermission.PHONE_STATE
                        else -> null
                    }
                    enumPerm?.let { PermissionInput(it, p.granted) }
                }

                val configInput = ConfigIndicatorsInput(
                    isDebuggable = cfg.any { it.ruleId == "CONFIG_DEBUGGABLE" },
                    usesCleartextTraffic = cfg.any { it.ruleId == "CONFIG_CLEARTEXT" },
                    isBackupAllowed = cfg.any { it.ruleId == "CONFIG_BACKUP" },
                    unprotectedExportedComponentsCount = if (cfg.any { it.ruleId == "CONFIG_EXPORTED_COMPONENTS" }) 1 else 0
                )

                val stabilityInput = StabilityInput(
                    isAvailable = ex?.available ?: false,
                    crashes = if (ex?.available == true) (ex.count30d / 2) else 0,
                    anrs = 0,
                    resourceKills = 0
                )

                val healthInput = AppHealthInput(
                    packageName = app.packageName,
                    permissions = permInputs,
                    configIndicators = configInput,
                    totalFootprintBytes = st?.totalBytes ?: 0L,
                    daysSinceLastUse = daysUnused,
                    stability = stabilityInput,
                    targetSdk = app.targetSdk,
                    daysSinceLastUpdate = daysSinceUpdate
                )

                val healthResult = HealthScorer.computeHealthScore(healthInput)
                val impactResult = ImpactScorer.computeImpactScore(healthInput, healthResult)

                scoreEntities.add(
                    ScoreResultEntity(
                        packageName = app.packageName,
                        scanId = scanId,
                        healthScore = healthResult.score,
                        healthBand = healthResult.band.label,
                        subScoresJson = gson.toJson(healthResult.subScores),
                        impactScore = impactResult.score,
                        impactBand = impactResult.band.label,
                        dominantDriver = impactResult.dominantDriver.label,
                        topReasonsJson = gson.toJson(healthResult.topReasons)
                    )
                )
            }

            dao.insertScoreResults(scoreEntities)

            // Update ScanRun duration
            val duration = System.currentTimeMillis() - startTime
            val updatedScan = scanRunEntity.copy(
                id = scanId,
                durationMs = duration,
                storageAvailable = storageResult.isAvailable,
                exitInfoAvailable = exitResult.isAvailable
            )
            dao.insertScanRun(updatedScan)

            scanId
        } finally {
            _isScanning.value = false
        }
    }

    suspend fun setUserDecision(packageName: String, decision: String, scoreHash: String = "") {
        withContext(Dispatchers.IO) {
            dao.insertUserDecision(
                UserDecisionEntity(
                    packageName = packageName,
                    decision = decision,
                    timestamp = System.currentTimeMillis(),
                    scoreHash = scoreHash
                )
            )
        }
    }

    suspend fun removeUserDecision(packageName: String) {
        withContext(Dispatchers.IO) {
            dao.removeUserDecision(packageName)
        }
    }

    suspend fun clearAllLocalData() {
        withContext(Dispatchers.IO) {
            dao.clearAllLocalData()
        }
    }

    fun getAppDetailFlow(packageName: String): Flow<AppCardData?> {
        return combine(
            dao.getAllSnapshots(),
            dao.getScoreResultForApp(packageName),
            dao.getPermissionsForApp(packageName),
            dao.getConfigIndicatorsForApp(packageName),
            dao.getAllUserDecisions()
        ) { snapshots, score, perms, indicators, decisions ->
            val app = snapshots.firstOrNull { it.packageName == packageName } ?: return@combine null
            val decision = decisions.firstOrNull { it.packageName == packageName }
            val topReasonsType = object : TypeToken<List<ScoreReason>>() {}.type
            val topReasons: List<ScoreReason> = score?.topReasonsJson?.let {
                try { gson.fromJson(it, topReasonsType) } catch (e: Exception) { emptyList() }
            } ?: emptyList()

            val subScores: SubScores = score?.subScoresJson?.let {
                try { gson.fromJson(it, SubScores::class.java) } catch (e: Exception) {
                    SubScores(100.0, 100.0, 100.0, null, 100.0)
                }
            } ?: SubScores(100.0, 100.0, 100.0, null, 100.0)

            val now = System.currentTimeMillis()
            val days = TimeUnit.MILLISECONDS.toDays(now - app.updateTime).toInt().coerceAtLeast(0)

            AppCardData(
                packageName = app.packageName,
                label = app.label,
                versionName = app.versionName,
                targetSdk = app.targetSdk,
                category = app.category,
                isSystem = app.isSystem,
                totalBytes = 150L * 1024 * 1024,
                appBytes = 100L * 1024 * 1024,
                dataBytes = 40L * 1024 * 1024,
                cacheBytes = 10L * 1024 * 1024,
                lastUsed = app.updateTime,
                daysUnused = days,
                foreground30dMs = 0L,
                sessionsCount = 0,
                healthScore = score?.healthScore ?: 100.0,
                healthBand = score?.healthBand ?: "Healthy",
                impactScore = score?.impactScore ?: 0.0,
                impactBand = score?.impactBand ?: "Low",
                dominantDriver = score?.dominantDriver ?: "Storage",
                topReasons = topReasons,
                subScores = subScores,
                isIgnored = decision?.decision == "IGNORE",
                isKept = decision?.decision == "KEEP",
                permissions = perms,
                indicators = indicators
            )
        }
    }
}
