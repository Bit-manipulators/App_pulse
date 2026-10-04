package com.apppulse.scoring.model

/**
 * Sensitive permissions evaluated for security scoring.
 */
enum class SensitivePermission(val basePenalty: Double) {
    SMS_OR_CALL_LOG(25.0),
    CONTACTS(15.0),
    MICROPHONE(15.0),
    LOCATION(15.0),
    BACKGROUND_LOCATION(25.0), // 15 location + 10 background
    BODY_SENSORS(10.0),
    CAMERA(10.0),
    CALENDAR(8.0),
    PHONE_STATE(8.0)
}

data class PermissionInput(
    val permission: SensitivePermission,
    val isGranted: Boolean
)

data class ConfigIndicatorsInput(
    val isDebuggable: Boolean = false,
    val usesCleartextTraffic: Boolean = false,
    val isBackupAllowed: Boolean = false,
    val unprotectedExportedComponentsCount: Int = 0
)

data class StabilityInput(
    val isAvailable: Boolean = false,
    val crashes: Int = 0,
    val anrs: Int = 0,
    val resourceKills: Int = 0
)

data class AppHealthInput(
    val packageName: String,
    val permissions: List<PermissionInput> = emptyList(),
    val configIndicators: ConfigIndicatorsInput = ConfigIndicatorsInput(),
    val totalFootprintBytes: Long = 0L,
    val daysSinceLastUse: Int = 0,
    val stability: StabilityInput = StabilityInput(),
    val targetSdk: Int = 35,
    val daysSinceLastUpdate: Int = 0
)

enum class HealthBand(val label: String) {
    HEALTHY("Healthy"),
    FAIR("Fair"),
    NEEDS_REVIEW("Needs review"),
    NEEDS_ATTENTION("Needs attention")
}

enum class ImpactBand(val label: String) {
    LOW("Low"),
    MEDIUM("Medium"),
    HIGH("High")
}

enum class DominantDriver(val label: String) {
    STORAGE("Storage"),
    SECURITY("Security"),
    STABILITY("Stability"),
    DORMANCY("Dormancy")
}

data class ScoreReason(
    val dimension: String,
    val description: String,
    val penaltyDeduction: Double,
    val displayValue: String
)

data class SubScores(
    val security: Double,
    val storage: Double,
    val usageRelevance: Double,
    val stability: Double?,
    val maintenance: Double
)

data class HealthScoreResult(
    val score: Double,
    val band: HealthBand,
    val subScores: SubScores,
    val topReasons: List<ScoreReason>
)

data class ImpactDimensions(
    val storageImpact: Double,
    val securityImpact: Double,
    val stabilityImpact: Double?,
    val dormancyImpact: Double
)

data class ImpactScoreResult(
    val score: Double,
    val band: ImpactBand,
    val dominantDriver: DominantDriver,
    val dimensions: ImpactDimensions
)

data class StorageHeadroomResult(
    val freePercent: Double,
    val score: Double
)

data class PhoneHealthResult(
    val overallScore: Double,
    val storageHeadroomScore: Double,
    val meanAppHealth: Double,
    val highImpactAppPercentage: Double,
    val highImpactPenalty: Double
)
