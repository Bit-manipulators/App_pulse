package com.apppulse.app.domain.nlq

import com.apppulse.app.data.local.entities.*
import com.apppulse.app.domain.ranking.FixFirstRanker
import com.apppulse.app.domain.ranking.RankedIssue
import java.util.Locale
import java.util.regex.Pattern

data class ValidatedFilter(
    val minSizeMB: Double? = null,
    val minDaysUnused: Int? = null,
    val permissionCategory: String? = null,
    val hasUnusualPermissions: Boolean = false,
    val impactBand: String? = null,
    val maxHealthScore: Double? = null,
    val hasRecentProblems: Boolean = false,
    val sortBy: String? = null, // "storage", "impact", "health", "usage"
    val isFixFirst: Boolean = false
)

data class NlqQueryResult(
    val explanation: String,
    val matchedApps: List<AppSnapshotEntity>,
    val rankedIssues: List<RankedIssue> = emptyList(),
    val isError: Boolean = false
)

object NaturalLanguageQueryEngine {

    val PRESET_QUERIES = listOf(
        "What should I fix first?",
        "Which apps over 500 MB haven't I used in 60 days?",
        "Show apps with location permission",
        "Which apps have unusual permissions?",
        "Show largest storage consumers",
        "Which apps have recent crashes or problems?"
    )

    fun parseQuery(query: String): ValidatedFilter {
        val q = query.lowercase(Locale.ROOT).trim()

        if (q.contains("fix first") || q.contains("what should i fix") || q.contains("recommendation")) {
            return ValidatedFilter(isFixFirst = true)
        }

        var minSizeMB: Double? = null
        var minDaysUnused: Int? = null

        // Detect size e.g. "500 mb", "1 gb"
        val sizePattern = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(mb|gb)")
        val sizeMatcher = sizePattern.matcher(q)
        if (sizeMatcher.find()) {
            val amount = sizeMatcher.group(1)?.toDoubleOrNull() ?: 0.0
            val unit = sizeMatcher.group(2)
            minSizeMB = if (unit == "gb") amount * 1024.0 else amount
        }

        // Detect days unused e.g. "30 days", "60 days", "90 days"
        val daysPattern = Pattern.compile("(\\d+)\\s*days?")
        val daysMatcher = daysPattern.matcher(q)
        if (daysMatcher.find()) {
            minDaysUnused = daysMatcher.group(1)?.toIntOrNull()
        }

        if (q.contains("unused") || q.contains("haven't used") || q.contains("not used")) {
            if (minDaysUnused == null) minDaysUnused = 30
        }

        val permission = when {
            q.contains("location") -> "Location"
            q.contains("camera") -> "Camera"
            q.contains("microphone") || q.contains("mic") -> "Microphone"
            q.contains("sms") || q.contains("text") -> "SMS"
            q.contains("contact") -> "Contacts"
            q.contains("call") -> "Call Log"
            q.contains("sensor") -> "Body Sensors"
            else -> null
        }

        val hasUnusual = q.contains("unusual") || q.contains("suspicious")
        val hasRecentProblems = q.contains("crash") || q.contains("problem") || q.contains("anr") || q.contains("stability")
        val isHighImpact = q.contains("high impact") || q.contains("heavy")
        val isLowHealth = q.contains("low health") || q.contains("poor health") || q.contains("needs attention")

        val sortBy = when {
            q.contains("largest") || q.contains("biggest") || q.contains("storage") || q.contains("size") -> "storage"
            q.contains("impact") -> "impact"
            q.contains("health") -> "health"
            else -> null
        }

        return ValidatedFilter(
            minSizeMB = minSizeMB,
            minDaysUnused = minDaysUnused,
            permissionCategory = permission,
            hasUnusualPermissions = hasUnusual,
            impactBand = if (isHighImpact) "High" else null,
            maxHealthScore = if (isLowHealth) 60.0 else null,
            hasRecentProblems = hasRecentProblems,
            sortBy = sortBy,
            isFixFirst = false
        )
    }

    fun execute(
        query: String,
        apps: List<AppSnapshotEntity>,
        scores: Map<String, ScoreResultEntity>,
        storages: Map<String, StorageStatEntity>,
        usages: Map<String, UsageStatEntity>,
        permissions: Map<String, List<PermissionStatEntity>>,
        indicators: Map<String, List<ConfigIndicatorEntity>>,
        decisions: Map<String, UserDecisionEntity>
    ): NlqQueryResult {
        val filter = parseQuery(query)

        if (filter.isFixFirst) {
            val ranked = FixFirstRanker.rankAttentionApps(
                apps, scores, storages, usages, permissions, indicators, decisions
            )
            return NlqQueryResult(
                explanation = if (ranked.isEmpty()) {
                    "No immediate problems detected! All apps appear healthy and within acceptable impact boundaries."
                } else {
                    "Identified ${ranked.size} app(s) that would benefit from your review, ordered deterministically by impact priority:"
                },
                matchedApps = ranked.map { it.app },
                rankedIssues = ranked
            )
        }

        val now = System.currentTimeMillis()
        var filtered = apps.filter { app ->
            val decision = decisions[app.packageName]
            if (decision?.decision == "IGNORE") return@filter false

            val storage = storages[app.packageName]
            val usage = usages[app.packageName]
            val score = scores[app.packageName]
            val appPerms = permissions[app.packageName] ?: emptyList()

            // Size filter
            if (filter.minSizeMB != null) {
                val mb = (storage?.totalBytes ?: 0L).toDouble() / (1024.0 * 1024.0)
                if (mb < filter.minSizeMB) return@filter false
            }

            // Days unused filter
            if (filter.minDaysUnused != null) {
                val lastUsed = usage?.lastUsed ?: app.installTime
                val days = ((now - lastUsed) / (1000L * 60 * 60 * 24)).coerceAtLeast(0)
                if (days < filter.minDaysUnused) return@filter false
            }

            // Permission filter
            if (filter.permissionCategory != null) {
                val hasPerm = appPerms.any { it.categoryName.equals(filter.permissionCategory, ignoreCase = true) && it.granted }
                if (!hasPerm) return@filter false
            }

            // Unusual permissions
            if (filter.hasUnusualPermissions) {
                val hasUnusual = appPerms.any { it.isUnusual && it.granted }
                if (!hasUnusual) return@filter false
            }

            // Impact band
            if (filter.impactBand != null) {
                if (score?.impactBand != filter.impactBand) return@filter false
            }

            // Health threshold
            if (filter.maxHealthScore != null) {
                if ((score?.healthScore ?: 100.0) >= filter.maxHealthScore) return@filter false
            }

            // Recent problems
            if (filter.hasRecentProblems) {
                val hasIssues = (score?.subScoresJson?.contains("crashes") == true) ||
                        (score?.dominantDriver == "Stability")
                if (!hasIssues) return@filter false
            }

            true
        }

        // Apply sorting
        filtered = when (filter.sortBy) {
            "storage" -> filtered.sortedByDescending { storages[it.packageName]?.totalBytes ?: 0L }
            "impact" -> filtered.sortedByDescending { scores[it.packageName]?.impactScore ?: 0.0 }
            "health" -> filtered.sortedBy { scores[it.packageName]?.healthScore ?: 100.0 }
            else -> filtered.sortedByDescending { storages[it.packageName]?.totalBytes ?: 0L }
        }

        val explanation = when {
            filtered.isEmpty() -> "No installed applications matched your query criteria."
            filter.minDaysUnused != null && filter.minSizeMB != null ->
                "Found ${filtered.size} app(s) over ${filter.minSizeMB.toInt()} MB unused for at least ${filter.minDaysUnused} days:"
            filter.permissionCategory != null ->
                "Found ${filtered.size} app(s) with granted ${filter.permissionCategory} permission:"
            filter.hasUnusualPermissions ->
                "Found ${filtered.size} app(s) with unusual sensitive permissions for their category:"
            else ->
                "Found ${filtered.size} app(s) matching your criteria:"
        }

        return NlqQueryResult(
            explanation = explanation,
            matchedApps = filtered
        )
    }
}
