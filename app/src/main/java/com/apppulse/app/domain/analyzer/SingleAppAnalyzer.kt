package com.apppulse.app.domain.analyzer

import android.content.Context
import com.apppulse.app.data.collector.*
import com.apppulse.app.data.local.entities.AppSnapshotEntity
import com.apppulse.app.data.local.entities.PermissionStatEntity
import com.apppulse.app.data.local.entities.StorageStatEntity
import com.apppulse.app.data.local.entities.UsageStatEntity
import com.apppulse.app.data.remote.OllamaClient
import com.apppulse.scoring.HealthScorer
import com.apppulse.scoring.ImpactScorer
import com.apppulse.scoring.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class AnalysisParameter(val label: String, val description: String) {
    SECURITY_RISK("Security & Permissions", "Evaluate sensitive permissions, background access & risk blast radius"),
    STORAGE_FOOTPRINT("Storage Footprint", "Measure code size, user data & temporary cache bloat"),
    USAGE_DORMANCY("Usage & Inactivity", "Audit screen time, launch frequency & days dormant"),
    STABILITY_BATTERY("Stability & Background Impact", "Check crashes, ANR exits & abnormal memory pressure")
}

data class SingleAppAnalysisReport(
    val app: AppSnapshotEntity,
    val selectedParameters: Set<AnalysisParameter>,
    val healthScore: Double,
    val impactScore: Double,
    val impactBand: String,
    val dominantDriver: String,
    val storageStats: StorageStatEntity?,
    val usageStats: UsageStatEntity?,
    val sensitivePermissions: List<PermissionStatEntity>,
    val aiGeneratedAnalysis: String,
    val isAiFromOllama: Boolean
)

class SingleAppAnalyzer(private val context: Context) {

    private val storageCollector = StorageCollector(context)
    private val usageCollector = UsageCollector(context)
    private val permissionCollector = PermissionCollector(context)
    private val exitCollector = ExitCollector(context)
    private val configCollector = ConfigCollector(context)

    suspend fun analyzeApp(
        app: AppSnapshotEntity,
        parameters: Set<AnalysisParameter>
    ): SingleAppAnalysisReport = withContext(Dispatchers.IO) {

        // 1. Gather on-demand metrics based on chosen parameters
        val storage = if (parameters.contains(AnalysisParameter.STORAGE_FOOTPRINT)) {
            storageCollector.getStorageForPackage(app.packageName)
        } else null

        val usage = if (parameters.contains(AnalysisParameter.USAGE_DORMANCY)) {
            usageCollector.getUsageForPackage(app.packageName, app.updateTime, app.installTime)
        } else null

        val permissions = if (parameters.contains(AnalysisParameter.SECURITY_RISK)) {
            permissionCollector.collectPermissionsForPackage(app.packageName, app.category)
        } else emptyList()

        val stability = if (parameters.contains(AnalysisParameter.STABILITY_BATTERY)) {
            exitCollector.collectExitStats(0L, listOf(app.packageName)).stats.firstOrNull()
        } else null

        val configs = configCollector.collectIndicatorsForApps(0L, listOf(app))

        // 2. Compute deterministic scores
        val permInputs = permissions.mapNotNull { p ->
            val sensitivePerm = when (p.categoryName) {
                "SMS", "Call Log" -> SensitivePermission.SMS_OR_CALL_LOG
                "Contacts" -> SensitivePermission.CONTACTS
                "Microphone" -> SensitivePermission.MICROPHONE
                "Location" -> if (p.permission.contains("BACKGROUND")) SensitivePermission.BACKGROUND_LOCATION else SensitivePermission.LOCATION
                "Camera" -> SensitivePermission.CAMERA
                "Phone State" -> SensitivePermission.PHONE_STATE
                "Calendar" -> SensitivePermission.CALENDAR
                "Body Sensors" -> SensitivePermission.BODY_SENSORS
                else -> null
            }
            sensitivePerm?.let { PermissionInput(it, p.granted) }
        }

        val healthInput = AppHealthInput(
            packageName = app.packageName,
            permissions = permInputs,
            configIndicators = ConfigIndicatorsInput(
                isDebuggable = configs.any { it.ruleId == "CONFIG_DEBUGGABLE" },
                usesCleartextTraffic = configs.any { it.ruleId == "CONFIG_CLEARTEXT" },
                isBackupAllowed = configs.any { it.ruleId == "CONFIG_BACKUP" },
                unprotectedExportedComponentsCount = if (configs.any { it.ruleId == "CONFIG_EXPORTED_COMPONENTS" }) 1 else 0
            ),
            totalFootprintBytes = storage?.totalBytes ?: 0L,
            daysSinceLastUse = usage?.daysSinceLastUse ?: 0,
            stability = StabilityInput(
                isAvailable = stability?.available ?: false,
                crashes = if (stability?.available == true) (stability.count30d / 2) else 0,
                anrs = 0,
                resourceKills = 0
            ),
            targetSdk = app.targetSdk
        )

        val healthResult = HealthScorer.computeHealthScore(healthInput)
        val impactResult = ImpactScorer.computeImpactScore(healthInput, healthResult)

        // 3. Construct rich factual prompt for Qwen model in Ollama
        val storageMb = (storage?.totalBytes ?: 0L) / (1024 * 1024)
        val cacheMb = (storage?.cacheBytes ?: 0L) / (1024 * 1024)
        val dataMb = (storage?.dataBytes ?: 0L) / (1024 * 1024)
        val grantedPerms = permissions.filter { it.granted }.map { it.categoryName }.distinct()
        val daysDormant = usage?.daysSinceLastUse ?: 0

        val promptBuilder = StringBuilder()
        promptBuilder.append("Analyze the following Android application based on real device metrics:\n\n")
        promptBuilder.append("Application: ${app.label} (${app.packageName})\n")
        promptBuilder.append("Target SDK: ${app.targetSdk} | Version: ${app.versionName}\n")
        promptBuilder.append("Evaluated Parameters:\n")

        if (parameters.contains(AnalysisParameter.SECURITY_RISK)) {
            promptBuilder.append("- Security & Permissions: Granted access to [${grantedPerms.joinToString(", ")}]. Total sensitive permissions: ${grantedPerms.size}.\n")
        }
        if (parameters.contains(AnalysisParameter.STORAGE_FOOTPRINT)) {
            promptBuilder.append("- Storage Footprint: Total: ${storageMb} MB (User Data: ${dataMb} MB, Cache: ${cacheMb} MB).\n")
        }
        if (parameters.contains(AnalysisParameter.USAGE_DORMANCY)) {
            promptBuilder.append("- Usage Activity: Last used ${daysDormant} days ago.\n")
        }
        if (parameters.contains(AnalysisParameter.STABILITY_BATTERY)) {
            promptBuilder.append("- Stability: Process exits in 30d: ${stability?.count30d ?: 0} (Exit telemetry available: ${stability?.available ?: false}).\n")
        }

        promptBuilder.append("- Computed Health Score: ${healthResult.score.toInt()}/100 (${healthResult.band.label})\n")
        promptBuilder.append("- Computed Resource Impact: ${impactResult.score.toInt()}/100 (${impactResult.band.label} driven by ${impactResult.dominantDriver})\n\n")
        promptBuilder.append("Please provide a concise, factual, and actionable 3-part assessment:\n")
        promptBuilder.append("1. Real-World Risk & System Impact (factual summary without panic or hype)\n")
        promptBuilder.append("2. Resource & Storage Footprint Assessment\n")
        promptBuilder.append("3. Recommended User Actions (specific to whether they should clear cache, adjust permissions, keep, or uninstall)\n")

        // 4. Query Ollama Qwen model
        val ollamaResult = OllamaClient.generateAnalysis(
            prompt = promptBuilder.toString(),
            systemPrompt = "You are an expert mobile systems engineer. Provide a factual, non-alarmist, and actionable performance and impact assessment based strictly on the provided real device metrics."
        )

        val (aiText, isOllama) = if (ollamaResult.isSuccess) {
            ollamaResult.getOrThrow() to true
        } else {
            // High-fidelity deterministic fallback
            val fallback = buildString {
                append("• Real-World Impact: ${app.label} has an estimated impact score of ${impactResult.score.toInt()}/100 driven by ${impactResult.dominantDriver.name.lowercase()}.\n\n")
                if (grantedPerms.isNotEmpty()) {
                    append("• Permissions Audit: Holds access to ${grantedPerms.joinToString(", ")}. Review permissions if any are not actively required.\n\n")
                }
                if (storageMb > 200) {
                    append("• Storage Review: Consuming ${storageMb} MB of disk space with ${cacheMb} MB reviewable temporary cache.\n\n")
                }
                if (daysDormant > 30) {
                    append("• Dormancy Notice: Not used for ${daysDormant} days. Consider uninstalling if no longer needed.\n\n")
                }
                append("• Action: Retain if actively used; manage permissions in Settings if background access is unnecessary.")
            }
            fallback to false
        }

        SingleAppAnalysisReport(
            app = app,
            selectedParameters = parameters,
            healthScore = healthResult.score,
            impactScore = impactResult.score,
            impactBand = impactResult.band.label,
            dominantDriver = impactResult.dominantDriver.name.lowercase().replaceFirstChar { it.uppercase() },
            storageStats = storage,
            usageStats = usage,
            sensitivePermissions = permissions,
            aiGeneratedAnalysis = aiText,
            isAiFromOllama = isOllama
        )
    }
}
