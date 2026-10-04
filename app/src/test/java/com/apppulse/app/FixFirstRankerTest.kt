package com.apppulse.app

import com.apppulse.app.data.local.entities.*
import com.apppulse.app.domain.ranking.ActionType
import com.apppulse.app.domain.ranking.FixFirstRanker
import org.junit.Assert.*
import org.junit.Test

class FixFirstRankerTest {

    @Test
    fun testFixFirstRankerSuppressesIgnoredApps() {
        val app = AppSnapshotEntity(
            packageName = "com.ignored.app",
            label = "Ignored App",
            versionName = "1.0",
            versionCode = 1,
            targetSdk = 35,
            installTime = System.currentTimeMillis() - 10000000,
            updateTime = System.currentTimeMillis() - 10000000,
            category = 0,
            isSystem = false
        )
        val score = ScoreResultEntity(
            packageName = "com.ignored.app",
            scanId = 1,
            healthScore = 30.0,
            healthBand = "Needs attention",
            subScoresJson = "{}",
            impactScore = 80.0,
            impactBand = "High",
            dominantDriver = "Storage",
            topReasonsJson = "[]"
        )
        val decisions = mapOf(
            "com.ignored.app" to UserDecisionEntity("com.ignored.app", "IGNORE", System.currentTimeMillis(), "")
        )

        val ranked = FixFirstRanker.rankAttentionApps(
            apps = listOf(app),
            scores = mapOf(app.packageName to score),
            storages = emptyMap(),
            usages = emptyMap(),
            permissions = emptyMap(),
            indicators = emptyMap(),
            decisions = decisions
        )

        assertTrue("Ignored apps must be suppressed from attention ranking", ranked.isEmpty())
    }

    @Test
    fun testFixFirstRankerPrioritizesDormantLargeApps() {
        val eightyDaysAgo = System.currentTimeMillis() - (80L * 24 * 3600 * 1000)
        val dormantApp = AppSnapshotEntity(
            packageName = "com.dormant.game",
            label = "Dormant Game",
            versionName = "1.0",
            versionCode = 1,
            targetSdk = 35,
            installTime = eightyDaysAgo,
            updateTime = eightyDaysAgo,
            category = 0,
            isSystem = false
        )
        val dormantUsage = UsageStatEntity(
            packageName = "com.dormant.game",
            scanId = 1,
            lastUsed = eightyDaysAgo,
            foreground7dMs = 0L,
            foreground30dMs = 0L,
            sessionsCount = 0
        )
        val dormantScore = ScoreResultEntity(
            packageName = "com.dormant.game",
            scanId = 1,
            healthScore = 40.0,
            healthBand = "Needs review",
            subScoresJson = "{}",
            impactScore = 75.0,
            impactBand = "High",
            dominantDriver = "Dormancy",
            topReasonsJson = "[]"
        )
        val dormantStorage = StorageStatEntity(
            packageName = "com.dormant.game",
            scanId = 1,
            appBytes = 1000L * 1024 * 1024,
            dataBytes = 500L * 1024 * 1024,
            cacheBytes = 100L * 1024 * 1024,
            totalBytes = 1500L * 1024 * 1024 // 1.5 GB
        )

        val ranked = FixFirstRanker.rankAttentionApps(
            apps = listOf(dormantApp),
            scores = mapOf(dormantApp.packageName to dormantScore),
            storages = mapOf(dormantApp.packageName to dormantStorage),
            usages = mapOf(dormantApp.packageName to dormantUsage),
            permissions = emptyMap(),
            indicators = emptyMap(),
            decisions = emptyMap()
        )

        assertEquals(1, ranked.size)
        assertEquals(ActionType.UNINSTALL, ranked[0].actionType)
        assertTrue(ranked[0].primaryReasonTitle.contains("Unused for"))
    }
}
