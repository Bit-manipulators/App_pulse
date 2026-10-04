package com.apppulse.scoring

import com.apppulse.scoring.model.PhoneHealthResult
import com.apppulse.scoring.model.StorageHeadroomResult
import kotlin.math.max
import kotlin.math.min

object PhoneHealthScorer {

    fun computeStorageHeadroom(freeBytes: Long, totalBytes: Long): StorageHeadroomResult {
        if (totalBytes <= 0L) {
            return StorageHeadroomResult(freePercent = 0.0, score = 10.0)
        }
        val freePercent = (freeBytes.toDouble() / totalBytes.toDouble()) * 100.0
        val score = when {
            freePercent >= 40.0 -> 100.0
            freePercent >= 25.0 -> 80.0
            freePercent >= 15.0 -> 55.0
            freePercent >= 8.0 -> 30.0
            else -> 10.0
        }
        return StorageHeadroomResult(
            freePercent = Math.round(freePercent * 10.0) / 10.0,
            score = score
        )
    }

    fun computePhoneHealth(
        freeBytes: Long,
        totalBytes: Long,
        appHealthScores: List<Double>,
        highImpactAppCount: Int
    ): PhoneHealthResult {
        val headroom = computeStorageHeadroom(freeBytes, totalBytes)

        val meanAppHealth = if (appHealthScores.isNotEmpty()) {
            appHealthScores.average()
        } else {
            100.0
        }

        val totalApps = appHealthScores.size
        val highImpactPercentage = if (totalApps > 0) {
            (highImpactAppCount.toDouble() / totalApps.toDouble()) * 100.0
        } else {
            0.0
        }

        val highImpactPenalty = min(100.0, 3.0 * highImpactPercentage)
        val highImpactFactor = 100.0 - highImpactPenalty

        val phoneHealth = (0.4 * headroom.score) +
                (0.4 * meanAppHealth) +
                (0.2 * highImpactFactor)

        val clamped = min(100.0, max(0.0, phoneHealth))

        return PhoneHealthResult(
            overallScore = Math.round(clamped * 10.0) / 10.0,
            storageHeadroomScore = headroom.score,
            meanAppHealth = Math.round(meanAppHealth * 10.0) / 10.0,
            highImpactAppPercentage = Math.round(highImpactPercentage * 10.0) / 10.0,
            highImpactPenalty = Math.round(highImpactPenalty * 10.0) / 10.0
        )
    }
}
