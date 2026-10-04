package com.apppulse.scoring

import com.apppulse.scoring.model.*
import org.junit.Assert.*
import org.junit.Test

class ScoringEngineTest {

    @Test
    fun testStorageBoundaries() {
        val testCases = listOf(
            50L * 1024 * 1024 to 100.0,    // < 100 MB -> 100
            200L * 1024 * 1024 to 85.0,   // 100–500 MB -> 85
            700L * 1024 * 1024 to 70.0,   // 0.5–1 GB -> 70
            1500L * 1024 * 1024 to 50.0,  // 1–2 GB -> 50
            3000L * 1024 * 1024 to 30.0,  // 2–5 GB -> 30
            6000L * 1024 * 1024 to 10.0   // > 5 GB -> 10
        )

        for ((bytes, expectedStorageScore) in testCases) {
            val input = AppHealthInput(
                packageName = "com.test.storage",
                totalFootprintBytes = bytes,
                daysSinceLastUse = 2,
                targetSdk = 35
            )
            val result = HealthScorer.computeHealthScore(input)
            assertEquals("Failed for bytes $bytes", expectedStorageScore, result.subScores.storage, 0.01)
        }
    }

    @Test
    fun testUsageRelevanceBoundaries() {
        val testCases = listOf(
            3 to 100.0,   // <= 7 -> 100
            7 to 100.0,
            15 to 75.0,   // 8–30 -> 75
            30 to 75.0,
            45 to 45.0,   // 31–60 -> 45
            60 to 45.0,
            75 to 25.0,   // 61–90 -> 25
            90 to 25.0,
            120 to 10.0   // > 90 -> 10
        )

        for ((days, expectedUsageScore) in testCases) {
            val input = AppHealthInput(
                packageName = "com.test.usage",
                totalFootprintBytes = 50L * 1024 * 1024,
                daysSinceLastUse = days,
                targetSdk = 35
            )
            val result = HealthScorer.computeHealthScore(input)
            assertEquals("Failed for days $days", expectedUsageScore, result.subScores.usageRelevance, 0.01)
        }
    }

    @Test
    fun testSecurityPermissionsAndUngrantedDiscount() {
        // SMS granted: 25 pts, Contacts granted: 15 pts -> 100 - 40 = 60
        val grantedInput = AppHealthInput(
            packageName = "com.test.perm",
            permissions = listOf(
                PermissionInput(SensitivePermission.SMS_OR_CALL_LOG, isGranted = true),
                PermissionInput(SensitivePermission.CONTACTS, isGranted = true)
            )
        )
        val grantedResult = HealthScorer.computeHealthScore(grantedInput)
        assertEquals(60.0, grantedResult.subScores.security, 0.01)

        // SMS requested but NOT granted: 25 * 0.25 = 6.25 pts deduction -> 100 - 6.25 = 93.75
        val ungrantedInput = AppHealthInput(
            packageName = "com.test.perm.ungranted",
            permissions = listOf(
                PermissionInput(SensitivePermission.SMS_OR_CALL_LOG, isGranted = false)
            )
        )
        val ungrantedResult = HealthScorer.computeHealthScore(ungrantedInput)
        assertEquals(93.75, ungrantedResult.subScores.security, 0.01)
    }

    @Test
    fun testConfigurationIndicatorsSecurityPenalties() {
        // debuggable -25, cleartext -10, backup -5, 2 unprotected exported -10
        // Total deduction = 50 -> 100 - 50 = 50
        val input = AppHealthInput(
            packageName = "com.test.config",
            configIndicators = ConfigIndicatorsInput(
                isDebuggable = true,
                usesCleartextTraffic = true,
                isBackupAllowed = true,
                unprotectedExportedComponentsCount = 2
            )
        )
        val result = HealthScorer.computeHealthScore(input)
        assertEquals(50.0, result.subScores.security, 0.01)
    }

    @Test
    fun testStabilityDegradationAndWeightRenormalization() {
        // When stability is unavailable:
        val inputNoStability = AppHealthInput(
            packageName = "com.test.nostability",
            totalFootprintBytes = 50L * 1024 * 1024, // 100
            daysSinceLastUse = 2, // 100
            targetSdk = 35, // 100
            stability = StabilityInput(isAvailable = false)
        )
        val resultNoStability = HealthScorer.computeHealthScore(inputNoStability)
        assertNull(resultNoStability.subScores.stability)
        assertEquals(100.0, resultNoStability.score, 0.01)
        assertEquals(HealthBand.HEALTHY, resultNoStability.band)

        // When stability is available with 2 crashes and 1 ANR:
        // Stability score = 100 - (10*2 + 15*1) = 65
        val inputWithStability = AppHealthInput(
            packageName = "com.test.stability",
            totalFootprintBytes = 50L * 1024 * 1024,
            daysSinceLastUse = 2,
            targetSdk = 35,
            stability = StabilityInput(isAvailable = true, crashes = 2, anrs = 1)
        )
        val resultWithStability = HealthScorer.computeHealthScore(inputWithStability)
        assertNotNull(resultWithStability.subScores.stability)
        assertEquals(65.0, resultWithStability.subScores.stability!!, 0.01)
        // Overall: 0.30*100 + 0.20*100 + 0.15*100 + 0.20*65 + 0.15*100 = 30 + 20 + 15 + 13 + 15 = 93.0
        assertEquals(93.0, resultWithStability.score, 0.01)
    }

    @Test
    fun testMaintenanceLagAndAgePenalty() {
        // Lag 0-1: 100; Lag 2: 80; Lag 3: 55; Lag >= 4: 30
        val inputLag3 = AppHealthInput(
            packageName = "com.test.maint",
            targetSdk = 32 // 35 - 32 = 3
        )
        val resultLag3 = HealthScorer.computeHealthScore(inputLag3)
        assertEquals(55.0, resultLag3.subScores.maintenance, 0.01)

        // Lag 3 plus not updated for 400 days (-10 penalty): 55 - 10 = 45
        val inputOld = AppHealthInput(
            packageName = "com.test.old",
            targetSdk = 32,
            daysSinceLastUpdate = 400
        )
        val resultOld = HealthScorer.computeHealthScore(inputOld)
        assertEquals(45.0, resultOld.subScores.maintenance, 0.01)
    }

    @Test
    fun testImpactScoreSevereDimensionNeverLow() {
        // App has great storage, usage, maintenance, but CRITICAL security issue:
        // security score = 0 -> security impact = 100.
        // Even if all other dimensions are healthy (0 impact),
        // raw impact = 0.6 * (100 * 30 / 80) + 0.4 * 100 = 0.6 * 37.5 + 40 = 22.5 + 40 = 62.5 (Medium!)
        // It must NEVER be banded Low (<35).
        val input = AppHealthInput(
            packageName = "com.test.severe",
            permissions = listOf(
                PermissionInput(SensitivePermission.SMS_OR_CALL_LOG, isGranted = true),
                PermissionInput(SensitivePermission.CONTACTS, isGranted = true),
                PermissionInput(SensitivePermission.LOCATION, isGranted = true),
                PermissionInput(SensitivePermission.MICROPHONE, isGranted = true),
                PermissionInput(SensitivePermission.CAMERA, isGranted = true),
                PermissionInput(SensitivePermission.BODY_SENSORS, isGranted = true),
                PermissionInput(SensitivePermission.CALENDAR, isGranted = true)
            ),
            configIndicators = ConfigIndicatorsInput(isDebuggable = true),
            totalFootprintBytes = 10L * 1024 * 1024, // < 100 MB
            daysSinceLastUse = 1,
            targetSdk = 35
        )
        val health = HealthScorer.computeHealthScore(input)
        assertEquals(0.0, health.subScores.security, 0.01)

        val impact = ImpactScorer.computeImpactScore(input, health)
        assertNotEquals("Severe security app must not be banded Low", ImpactBand.LOW, impact.band)
        assertEquals(DominantDriver.SECURITY, impact.dominantDriver)
        assertTrue(impact.score >= 35.0)
    }

    @Test
    fun testDormantLargeAppImpact() {
        // 1.5 GB app, unused for 95 days
        // Usage relevance = 10 -> 100 - 10 = 90
        // Footprint factor = min(1, 1500 / 500) = 1.0
        // Dormancy impact = 90 * 1.0 = 90
        val input = AppHealthInput(
            packageName = "com.test.dormant",
            totalFootprintBytes = 1500L * 1024 * 1024,
            daysSinceLastUse = 95,
            targetSdk = 35
        )
        val health = HealthScorer.computeHealthScore(input)
        val impact = ImpactScorer.computeImpactScore(input, health)

        assertEquals(90.0, impact.dimensions.dormancyImpact, 0.01)
        assertEquals(DominantDriver.DORMANCY, impact.dominantDriver)
        assertEquals(ImpactBand.MEDIUM, impact.band)
        assertEquals(60.8, impact.score, 0.1)

        // Case that reaches High (>= 65): > 5 GB dormant app with storage impact 90
        val hugeInput = AppHealthInput(
            packageName = "com.test.huge.dormant",
            totalFootprintBytes = 6000L * 1024 * 1024,
            daysSinceLastUse = 95,
            targetSdk = 35
        )
        val hugeHealth = HealthScorer.computeHealthScore(hugeInput)
        val hugeImpact = ImpactScorer.computeImpactScore(hugeInput, hugeHealth)
        assertEquals(ImpactBand.HIGH, hugeImpact.band)
        assertTrue(hugeImpact.score >= 65.0)
    }

    @Test
    fun testTopThreeReasonsExplainabilityRule() {
        val input = AppHealthInput(
            packageName = "com.test.explain",
            permissions = listOf(
                PermissionInput(SensitivePermission.SMS_OR_CALL_LOG, isGranted = true) // -25
            ),
            configIndicators = ConfigIndicatorsInput(
                isDebuggable = true // -25
            ),
            totalFootprintBytes = 3000L * 1024 * 1024, // 2-5 GB -> lost 70
            daysSinceLastUse = 80 // 61-90 -> lost 75
        )
        val health = HealthScorer.computeHealthScore(input)
        assertEquals(3, health.topReasons.size)

        // Reasons should be sorted descending by penalty deduction
        assertTrue(health.topReasons[0].penaltyDeduction >= health.topReasons[1].penaltyDeduction)
        assertTrue(health.topReasons[1].penaltyDeduction >= health.topReasons[2].penaltyDeduction)
        for (reason in health.topReasons) {
            assertTrue(reason.displayValue.isNotEmpty())
            assertTrue(reason.description.isNotEmpty())
        }
    }

    @Test
    fun testPhoneHealthScoreAndHeadroom() {
        // 50% free space -> headroom score = 100
        val headroom = PhoneHealthScorer.computeStorageHeadroom(50L * 1024, 100L * 1024)
        assertEquals(100.0, headroom.score, 0.01)

        // 20% free space -> headroom score = 55
        val headroom2 = PhoneHealthScorer.computeStorageHeadroom(20L * 1024, 100L * 1024)
        assertEquals(55.0, headroom2.score, 0.01)

        // 10 apps with mean health 80.0, 1 high impact app (10% high impact -> penalty min(100, 3*10) = 30)
        // High impact factor = 100 - 30 = 70
        // Phone health = 0.4 * 100 + 0.4 * 80 + 0.2 * 70 = 40 + 32 + 14 = 86.0
        val phoneHealth = PhoneHealthScorer.computePhoneHealth(
            freeBytes = 50L * 1024 * 1024,
            totalBytes = 100L * 1024 * 1024,
            appHealthScores = List(10) { 80.0 },
            highImpactAppCount = 1
        )
        assertEquals(86.0, phoneHealth.overallScore, 0.01)
    }
}
