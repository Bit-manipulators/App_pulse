package com.apppulse.scoring

import com.apppulse.scoring.model.*
import kotlin.math.max
import kotlin.math.min

object ImpactScorer {

    fun computeImpactScore(
        input: AppHealthInput,
        healthResult: HealthScoreResult
    ): ImpactScoreResult {
        val storageImpact = 100.0 - healthResult.subScores.storage
        val securityImpact = 100.0 - healthResult.subScores.security

        val mb = input.totalFootprintBytes.toDouble() / (1024.0 * 1024.0)
        val footprintFactor = min(1.0, mb / 500.0)
        val dormancyImpact = (100.0 - healthResult.subScores.usageRelevance) * footprintFactor

        val stabilityImpact = healthResult.subScores.stability?.let { 100.0 - it }

        // Weighted mean calculation
        val weightedMean: Double
        val maxDimension: Double
        val dominantDriver: DominantDriver

        if (stabilityImpact != null) {
            val totalWeight = ScoringConfig.IMPACT_WEIGHT_STORAGE +
                    ScoringConfig.IMPACT_WEIGHT_SECURITY +
                    ScoringConfig.IMPACT_WEIGHT_STABILITY +
                    ScoringConfig.IMPACT_WEIGHT_DORMANCY // 30 + 30 + 20 + 20 = 100
            val sum = (storageImpact * ScoringConfig.IMPACT_WEIGHT_STORAGE) +
                    (securityImpact * ScoringConfig.IMPACT_WEIGHT_SECURITY) +
                    (stabilityImpact * ScoringConfig.IMPACT_WEIGHT_STABILITY) +
                    (dormancyImpact * ScoringConfig.IMPACT_WEIGHT_DORMANCY)
            weightedMean = sum / totalWeight

            val dimensionMap = mapOf(
                DominantDriver.STORAGE to storageImpact,
                DominantDriver.SECURITY to securityImpact,
                DominantDriver.STABILITY to stabilityImpact,
                DominantDriver.DORMANCY to dormancyImpact
            )
            val maxEntry = dimensionMap.maxByOrNull { it.value }!!
            maxDimension = maxEntry.value
            dominantDriver = maxEntry.key
        } else {
            // Renormalise without stability
            val totalWeight = ScoringConfig.IMPACT_WEIGHT_STORAGE +
                    ScoringConfig.IMPACT_WEIGHT_SECURITY +
                    ScoringConfig.IMPACT_WEIGHT_DORMANCY // 30 + 30 + 20 = 80
            val sum = (storageImpact * ScoringConfig.IMPACT_WEIGHT_STORAGE) +
                    (securityImpact * ScoringConfig.IMPACT_WEIGHT_SECURITY) +
                    (dormancyImpact * ScoringConfig.IMPACT_WEIGHT_DORMANCY)
            weightedMean = sum / totalWeight

            val dimensionMap = mapOf(
                DominantDriver.STORAGE to storageImpact,
                DominantDriver.SECURITY to securityImpact,
                DominantDriver.DORMANCY to dormancyImpact
            )
            val maxEntry = dimensionMap.maxByOrNull { it.value }!!
            maxDimension = maxEntry.value
            dominantDriver = maxEntry.key
        }

        val rawImpact = (0.6 * weightedMean) + (0.4 * maxDimension)
        val clampedImpact = min(100.0, max(0.0, rawImpact))

        val band = when {
            clampedImpact < 35.0 -> ImpactBand.LOW
            clampedImpact < 65.0 -> ImpactBand.MEDIUM
            else -> ImpactBand.HIGH
        }

        return ImpactScoreResult(
            score = (Math.round(clampedImpact * 10.0) / 10.0),
            band = band,
            dominantDriver = dominantDriver,
            dimensions = ImpactDimensions(
                storageImpact = storageImpact,
                securityImpact = securityImpact,
                stabilityImpact = stabilityImpact,
                dormancyImpact = dormancyImpact
            )
        )
    }
}
