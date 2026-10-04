package com.apppulse.app.domain.ranking

import com.apppulse.app.data.local.entities.*
import com.apppulse.scoring.model.DominantDriver
import com.apppulse.scoring.model.ImpactBand

data class RankedIssue(
    val app: AppSnapshotEntity,
    val score: ScoreResultEntity,
    val storage: StorageStatEntity?,
    val usage: UsageStatEntity?,
    val priorityRank: Int,
    val primaryReasonTitle: String,
    val recommendedAction: String,
    val actionType: ActionType
)

enum class ActionType {
    OPEN_STORAGE_SETTINGS,
    OPEN_APP_SETTINGS,
    UNINSTALL,
    REVIEW_PERMISSIONS
}

object FixFirstRanker {

    fun rankAttentionApps(
        apps: List<AppSnapshotEntity>,
        scores: Map<String, ScoreResultEntity>,
        storages: Map<String, StorageStatEntity>,
        usages: Map<String, UsageStatEntity>,
        permissions: Map<String, List<PermissionStatEntity>>,
        indicators: Map<String, List<ConfigIndicatorEntity>>,
        decisions: Map<String, UserDecisionEntity>
    ): List<RankedIssue> {
        val candidates = mutableListOf<RankedIssue>()

        for (app in apps) {
            val decision = decisions[app.packageName]
            // Ignored apps are suppressed from attention counts and review ranking per PRD FR-19
            if (decision?.decision == "IGNORE") continue

            val score = scores[app.packageName] ?: continue
            val storage = storages[app.packageName]
            val usage = usages[app.packageName]
            val perms = permissions[app.packageName] ?: emptyList()
            val appIndicators = indicators[app.packageName] ?: emptyList()

            val mb = (storage?.totalBytes ?: 0L).toDouble() / (1024.0 * 1024.0)
            val now = System.currentTimeMillis()
            val lastUsed = usage?.lastUsed ?: app.installTime
            val daysUnused = ((now - lastUsed) / (1000L * 60 * 60 * 24)).coerceAtLeast(0)

            // High impact dormant large apps
            if (score.impactBand == ImpactBand.HIGH.label && daysUnused >= 30 && mb >= 500.0) {
                candidates.add(
                    RankedIssue(
                        app = app,
                        score = score,
                        storage = storage,
                        usage = usage,
                        priorityRank = 1,
                        primaryReasonTitle = "Unused for $daysUnused days taking ${formatMb(mb)}",
                        recommendedAction = "Consider uninstalling to reclaim significant storage.",
                        actionType = ActionType.UNINSTALL
                    )
                )
                continue
            }

            // Unusual permissions detected
            val unusualPerms = perms.filter { it.isUnusual && it.granted }
            if (unusualPerms.isNotEmpty()) {
                val names = unusualPerms.joinToString(", ") { it.categoryName }
                candidates.add(
                    RankedIssue(
                        app = app,
                        score = score,
                        storage = storage,
                        usage = usage,
                        priorityRank = 2,
                        primaryReasonTitle = "Unusual sensitive permissions granted: $names",
                        recommendedAction = "Review and revoke unnecessary permissions in system settings.",
                        actionType = ActionType.REVIEW_PERMISSIONS
                    )
                )
                continue
            }

            // High impact security / config issues (Debuggable build)
            if (appIndicators.any { it.ruleId == "CONFIG_DEBUGGABLE" }) {
                candidates.add(
                    RankedIssue(
                        app = app,
                        score = score,
                        storage = storage,
                        usage = usage,
                        priorityRank = 3,
                        primaryReasonTitle = "Debuggable build flag enabled in application",
                        recommendedAction = "Review application source or replace with official release build.",
                        actionType = ActionType.OPEN_APP_SETTINGS
                    )
                )
                continue
            }

            // Large storage hogs (> 1 GB)
            if (mb >= 1024.0) {
                val cacheMb = (storage?.cacheBytes ?: 0L).toDouble() / (1024.0 * 1024.0)
                candidates.add(
                    RankedIssue(
                        app = app,
                        score = score,
                        storage = storage,
                        usage = usage,
                        priorityRank = 4,
                        primaryReasonTitle = "Large storage footprint of ${formatMb(mb)} (${formatMb(cacheMb)} cache)",
                        recommendedAction = "Inspect storage breakdown and clear temporary app cache in settings.",
                        actionType = ActionType.OPEN_STORAGE_SETTINGS
                    )
                )
                continue
            }

            // Other High impact apps
            if (score.impactBand == ImpactBand.HIGH.label) {
                candidates.add(
                    RankedIssue(
                        app = app,
                        score = score,
                        storage = storage,
                        usage = usage,
                        priorityRank = 5,
                        primaryReasonTitle = "Elevated impact driver: ${score.dominantDriver}",
                        recommendedAction = "Review app health breakdown and adjust permissions or background usage.",
                        actionType = ActionType.OPEN_APP_SETTINGS
                    )
                )
                continue
            }

            // Needs attention or Needs review health score (< 60)
            if (score.healthScore < 60.0) {
                candidates.add(
                    RankedIssue(
                        app = app,
                        score = score,
                        storage = storage,
                        usage = usage,
                        priorityRank = 6,
                        primaryReasonTitle = "Health score is ${score.healthScore} (${score.healthBand})",
                        recommendedAction = "Inspect top contributing factors in app detail.",
                        actionType = ActionType.OPEN_APP_SETTINGS
                    )
                )
            }
        }

        // Deterministic sorting: priority rank first, then descending impact score, then largest storage
        return candidates.sortedWith(
            compareBy<RankedIssue> { it.priorityRank }
                .thenByDescending { it.score.impactScore }
                .thenByDescending { it.storage?.totalBytes ?: 0L }
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
