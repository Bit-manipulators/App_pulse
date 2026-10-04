package com.apppulse.scoring

import com.apppulse.scoring.model.*
import kotlin.math.max
import kotlin.math.min

object HealthScorer {

    fun computeHealthScore(input: AppHealthInput): HealthScoreResult {
        val reasons = mutableListOf<ScoreReason>()

        // 1. Security Dimension (0 - 100)
        var securityScore = 100.0
        var permDeduction = 0.0
        for (p in input.permissions) {
            val penalty = if (p.isGranted) {
                p.permission.basePenalty
            } else {
                p.permission.basePenalty * ScoringConfig.UNGRANTED_PENALTY_FACTOR
            }
            permDeduction += penalty
            val stateText = if (p.isGranted) "granted" else "requested"
            reasons.add(
                ScoreReason(
                    dimension = "Security",
                    description = "${p.permission.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }} permission ($stateText)",
                    penaltyDeduction = penalty,
                    displayValue = "-${penalty.toInt()} pts"
                )
            )
        }
        securityScore -= permDeduction

        // Configuration indicators
        if (input.configIndicators.isDebuggable) {
            securityScore -= ScoringConfig.PENALTY_DEBUGGABLE
            reasons.add(
                ScoreReason(
                    dimension = "Security",
                    description = "Debuggable build flag enabled",
                    penaltyDeduction = ScoringConfig.PENALTY_DEBUGGABLE,
                    displayValue = "-${ScoringConfig.PENALTY_DEBUGGABLE.toInt()} pts"
                )
            )
        }
        if (input.configIndicators.usesCleartextTraffic) {
            securityScore -= ScoringConfig.PENALTY_CLEARTEXT
            reasons.add(
                ScoreReason(
                    dimension = "Security",
                    description = "Cleartext HTTP traffic allowed",
                    penaltyDeduction = ScoringConfig.PENALTY_CLEARTEXT,
                    displayValue = "-${ScoringConfig.PENALTY_CLEARTEXT.toInt()} pts"
                )
            )
        }
        if (input.configIndicators.isBackupAllowed) {
            securityScore -= ScoringConfig.PENALTY_BACKUP
            reasons.add(
                ScoreReason(
                    dimension = "Security",
                    description = "Application backup flag enabled",
                    penaltyDeduction = ScoringConfig.PENALTY_BACKUP,
                    displayValue = "-${ScoringConfig.PENALTY_BACKUP.toInt()} pts"
                )
            )
        }
        if (input.configIndicators.unprotectedExportedComponentsCount > 0) {
            val deduction = min(
                ScoringConfig.MAX_PENALTY_UNPROTECTED_EXPORTED,
                input.configIndicators.unprotectedExportedComponentsCount * ScoringConfig.PENALTY_PER_UNPROTECTED_EXPORTED
            )
            securityScore -= deduction
            reasons.add(
                ScoreReason(
                    dimension = "Security",
                    description = "${input.configIndicators.unprotectedExportedComponentsCount} unprotected exported component(s)",
                    penaltyDeduction = deduction,
                    displayValue = "-${deduction.toInt()} pts"
                )
            )
        }
        securityScore = max(0.0, securityScore)

        // 2. Storage Dimension (0 - 100)
        val mb = input.totalFootprintBytes.toDouble() / (1024.0 * 1024.0)
        val storageScore: Double
        val storageLoss: Double
        val storageDesc: String
        when {
            mb < 100.0 -> {
                storageScore = 100.0
                storageLoss = 0.0
                storageDesc = "< 100 MB"
            }
            mb <= 500.0 -> {
                storageScore = 85.0
                storageLoss = 15.0
                storageDesc = "100–500 MB footprint (${formatMb(mb)})"
            }
            mb <= 1000.0 -> {
                storageScore = 70.0
                storageLoss = 30.0
                storageDesc = "0.5–1 GB footprint (${formatMb(mb)})"
            }
            mb <= 2000.0 -> {
                storageScore = 50.0
                storageLoss = 50.0
                storageDesc = "1–2 GB footprint (${formatMb(mb)})"
            }
            mb <= 5000.0 -> {
                storageScore = 30.0
                storageLoss = 70.0
                storageDesc = "2–5 GB footprint (${formatMb(mb)})"
            }
            else -> {
                storageScore = 10.0
                storageLoss = 90.0
                storageDesc = "> 5 GB footprint (${formatMb(mb)})"
            }
        }
        if (storageLoss > 0.0) {
            reasons.add(
                ScoreReason(
                    dimension = "Storage",
                    description = "Storage: $storageDesc",
                    penaltyDeduction = storageLoss,
                    displayValue = "-${storageLoss.toInt()} pts"
                )
            )
        }

        // 3. Usage Relevance Dimension (0 - 100)
        val days = input.daysSinceLastUse
        val usageScore: Double
        val usageLoss: Double
        when {
            days <= 7 -> {
                usageScore = 100.0
                usageLoss = 0.0
            }
            days <= 30 -> {
                usageScore = 75.0
                usageLoss = 25.0
            }
            days <= 60 -> {
                usageScore = 45.0
                usageLoss = 55.0
            }
            days <= 90 -> {
                usageScore = 25.0
                usageLoss = 75.0
            }
            else -> {
                usageScore = 10.0
                usageLoss = 90.0
            }
        }
        if (usageLoss > 0.0) {
            reasons.add(
                ScoreReason(
                    dimension = "Usage relevance",
                    description = "Inactive for $days days",
                    penaltyDeduction = usageLoss,
                    displayValue = "-${usageLoss.toInt()} pts"
                )
            )
        }

        // 4. Stability Dimension (optional, 0 - 100)
        val stabilityScore: Double?
        if (input.stability.isAvailable) {
            val deductions = (ScoringConfig.STABILITY_CRASH_FACTOR * input.stability.crashes) +
                    (ScoringConfig.STABILITY_ANR_FACTOR * input.stability.anrs) +
                    (ScoringConfig.STABILITY_KILL_FACTOR * input.stability.resourceKills)
            val computed = max(0.0, 100.0 - deductions)
            stabilityScore = computed
            if (deductions > 0.0) {
                val details = mutableListOf<String>()
                if (input.stability.crashes > 0) details.add("${input.stability.crashes} crashes")
                if (input.stability.anrs > 0) details.add("${input.stability.anrs} ANRs")
                if (input.stability.resourceKills > 0) details.add("${input.stability.resourceKills} kills")
                reasons.add(
                    ScoreReason(
                        dimension = "Stability",
                        description = "Abnormal exits in 30d: ${details.joinToString(", ")}",
                        penaltyDeduction = min(100.0, deductions),
                        displayValue = "-${min(100.0, deductions).toInt()} pts"
                    )
                )
            }
        } else {
            stabilityScore = null
        }

        // 5. Maintenance Dimension (0 - 100)
        val lag = max(0, ScoringConfig.CURRENT_PLATFORM_API_LEVEL - input.targetSdk)
        var maintScore = when {
            lag <= 1 -> 100.0
            lag == 2 -> 80.0
            lag == 3 -> 55.0
            else -> 30.0
        }
        val sdkLagLoss = 100.0 - maintScore
        if (sdkLagLoss > 0.0) {
            reasons.add(
                ScoreReason(
                    dimension = "Maintenance",
                    description = "Target SDK lag of $lag levels (target ${input.targetSdk})",
                    penaltyDeduction = sdkLagLoss,
                    displayValue = "-${sdkLagLoss.toInt()} pts"
                )
            )
        }
        if (input.daysSinceLastUpdate > ScoringConfig.OUTDATED_UPDATE_DAYS) {
            maintScore = max(0.0, maintScore - ScoringConfig.OUTDATED_UPDATE_PENALTY)
            reasons.add(
                ScoreReason(
                    dimension = "Maintenance",
                    description = "Not updated in ${input.daysSinceLastUpdate} days (> 1 year)",
                    penaltyDeduction = ScoringConfig.OUTDATED_UPDATE_PENALTY,
                    displayValue = "-${ScoringConfig.OUTDATED_UPDATE_PENALTY.toInt()} pts"
                )
            )
        }

        // Overall Weighted Health Score
        val overallScore: Double
        if (stabilityScore != null) {
            overallScore = (securityScore * ScoringConfig.WEIGHT_SECURITY) +
                    (storageScore * ScoringConfig.WEIGHT_STORAGE) +
                    (usageScore * ScoringConfig.WEIGHT_USAGE) +
                    (stabilityScore * ScoringConfig.WEIGHT_STABILITY) +
                    (maintScore * ScoringConfig.WEIGHT_MAINTENANCE)
        } else {
            // Renormalise weights when stability is unavailable
            val normalSum = ScoringConfig.WEIGHT_SECURITY +
                    ScoringConfig.WEIGHT_STORAGE +
                    ScoringConfig.WEIGHT_USAGE +
                    ScoringConfig.WEIGHT_MAINTENANCE // 0.30 + 0.20 + 0.15 + 0.15 = 0.80
            val raw = (securityScore * ScoringConfig.WEIGHT_SECURITY) +
                    (storageScore * ScoringConfig.WEIGHT_STORAGE) +
                    (usageScore * ScoringConfig.WEIGHT_USAGE) +
                    (maintScore * ScoringConfig.WEIGHT_MAINTENANCE)
            overallScore = raw / normalSum
        }

        val clampedOverall = min(100.0, max(0.0, overallScore))
        val band = when {
            clampedOverall >= 80.0 -> HealthBand.HEALTHY
            clampedOverall >= 60.0 -> HealthBand.FAIR
            clampedOverall >= 40.0 -> HealthBand.NEEDS_REVIEW
            else -> HealthBand.NEEDS_ATTENTION
        }

        // Top 3 reasons that lowered the score
        val topReasons = reasons
            .sortedByDescending { it.penaltyDeduction }
            .take(3)

        return HealthScoreResult(
            score = (Math.round(clampedOverall * 10.0) / 10.0),
            band = band,
            subScores = SubScores(
                security = securityScore,
                storage = storageScore,
                usageRelevance = usageScore,
                stability = stabilityScore,
                maintenance = maintScore
            ),
            topReasons = topReasons
        )
    }

    private fun formatMb(mb: Double): String {
        return if (mb >= 1024.0) {
            String.format(java.util.Locale.US, "%.1f GB", mb / 1024.0)
        } else {
            String.format(java.util.Locale.US, "%.0f MB", mb)
        }
    }
}
