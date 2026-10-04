package com.apppulse.app.data.collector

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import com.apppulse.app.data.local.entities.AppSnapshotEntity
import com.apppulse.app.data.local.entities.UsageStatEntity
import java.util.concurrent.TimeUnit

class UsageCollector(private val context: Context) {

    data class UsageCollectResult(
        val stats: List<UsageStatEntity>,
        val isAvailable: Boolean
    )

    fun hasUsageAccess(): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
            val mode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            false
        }
    }

    fun collectUsageStats(
        scanId: Long,
        snapshots: List<AppSnapshotEntity>
    ): UsageCollectResult {
        val hasAccess = hasUsageAccess()
        val now = System.currentTimeMillis()
        val thirtyDaysAgo = now - TimeUnit.DAYS.toMillis(30)
        val sevenDaysAgo = now - TimeUnit.DAYS.toMillis(7)

        if (!hasAccess) {
            // Reduced fallback mode: use install/update dates as reference
            val fallbackStats = snapshots.map { app ->
                val refTime = if (app.updateTime > 0) app.updateTime else app.installTime
                val daysSinceRef = TimeUnit.MILLISECONDS.toDays(now - refTime).coerceAtLeast(0)
                UsageStatEntity(
                    packageName = app.packageName,
                    scanId = scanId,
                    lastUsed = refTime,
                    foreground7dMs = 0L,
                    foreground30dMs = 0L,
                    sessionsCount = 0,
                    historyLimitDays = 0
                )
            }
            return UsageCollectResult(stats = fallbackStats, isAvailable = false)
        }

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
        if (usageStatsManager == null) {
            return UsageCollectResult(stats = emptyList(), isAvailable = false)
        }

        return try {
            // Query 30-day stats
            val stats30d = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_BEST,
                thirtyDaysAgo,
                now
            ).associateBy { it.packageName }

            // Query 7-day stats
            val stats7d = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_BEST,
                sevenDaysAgo,
                now
            ).associateBy { it.packageName }

            // Count sessions from events in last 7 days
            val sessionsMap = countSessions(usageStatsManager, sevenDaysAgo, now)

            val results = snapshots.map { app ->
                val stat30 = stats30d[app.packageName]
                val stat7 = stats7d[app.packageName]
                val sessions = sessionsMap[app.packageName] ?: 0

                val lastUsedRaw = stat30?.lastTimeUsed ?: 0L
                val lastUsed = if (lastUsedRaw > 0L) {
                    lastUsedRaw
                } else {
                    // Per FR-07: Apps never opened use install/update date as the reference
                    if (app.updateTime > 0L) app.updateTime else app.installTime
                }

                UsageStatEntity(
                    packageName = app.packageName,
                    scanId = scanId,
                    lastUsed = lastUsed,
                    foreground7dMs = stat7?.totalTimeInForeground ?: 0L,
                    foreground30dMs = stat30?.totalTimeInForeground ?: 0L,
                    sessionsCount = sessions,
                    historyLimitDays = 30
                )
            }

            UsageCollectResult(stats = results, isAvailable = true)
        } catch (e: Exception) {
            UsageCollectResult(stats = emptyList(), isAvailable = false)
        }
    }

    private fun countSessions(
        usageStatsManager: UsageStatsManager,
        startTime: Long,
        endTime: Long
    ): Map<String, Int> {
        val map = mutableMapOf<String, Int>()
        try {
            val events = usageStatsManager.queryEvents(startTime, endTime)
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                    val pkg = event.packageName
                    map[pkg] = (map[pkg] ?: 0) + 1
                }
            }
        } catch (e: Exception) {
            // Ignore event query exceptions
        }
        return map
    }

    fun getUsageForPackage(pkg: String, updateTime: Long = 0L, installTime: Long = 0L): UsageStatEntity {
        val now = System.currentTimeMillis()
        val thirtyDaysAgo = now - TimeUnit.DAYS.toMillis(30)
        val sevenDaysAgo = now - TimeUnit.DAYS.toMillis(7)

        if (hasUsageAccess()) {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            if (usageStatsManager != null) {
                try {
                    val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, thirtyDaysAgo, now)
                    val appStats = stats?.filter { it.packageName == pkg } ?: emptyList()
                    val totalForeground = appStats.sumOf { it.totalTimeInForeground }
                    val lastUsed = appStats.maxOfOrNull { it.lastTimeUsed } ?: 0L

                    val daysSinceLastUse = if (lastUsed > 0L) {
                        TimeUnit.MILLISECONDS.toDays(now - lastUsed).coerceAtLeast(0)
                    } else {
                        val ref = if (updateTime > 0) updateTime else installTime
                        if (ref > 0) TimeUnit.MILLISECONDS.toDays(now - ref).coerceAtLeast(0) else 999L
                    }

                    return UsageStatEntity(
                        packageName = pkg,
                        scanId = 0L,
                        lastUsed = lastUsed,
                        foreground7dMs = totalForeground / 4,
                        foreground30dMs = totalForeground,
                        sessionsCount = 0,
                        historyLimitDays = 30
                    )
                } catch (e: Exception) {
                    // Fall back
                }
            }
        }

        val ref = if (updateTime > 0) updateTime else installTime
        return UsageStatEntity(
            packageName = pkg,
            scanId = 0L,
            lastUsed = ref,
            foreground7dMs = 0L,
            foreground30dMs = 0L,
            sessionsCount = 0,
            historyLimitDays = 30
        )
    }
}
