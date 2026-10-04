package com.apppulse.app.data.collector

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import com.apppulse.app.data.local.entities.ExitStatEntity

class ExitCollector(private val context: Context) {

    data class ExitCollectResult(
        val stats: List<ExitStatEntity>,
        val isAvailable: Boolean
    )

    fun collectExitStats(scanId: Long, packageNames: List<String>): ExitCollectResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            val unavailable = packageNames.map { pkg ->
                ExitStatEntity(
                    packageName = pkg,
                    scanId = scanId,
                    reason = "API Level < 30",
                    count30d = 0,
                    available = false
                )
            }
            return ExitCollectResult(unavailable, false)
        }

        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        if (am == null) {
            val unavailable = packageNames.map { pkg ->
                ExitStatEntity(
                    packageName = pkg,
                    scanId = scanId,
                    reason = "ActivityManager unavailable",
                    count30d = 0,
                    available = false
                )
            }
            return ExitCollectResult(unavailable, false)
        }

        var anyAvailable = false
        val results = mutableListOf<ExitStatEntity>()

        for (pkg in packageNames) {
            try {
                val exitInfos = am.getHistoricalProcessExitReasons(pkg, 0, 50)
                var crashes = 0
                var anrs = 0
                var kills = 0

                for (info in exitInfos) {
                    when (info.reason) {
                        ApplicationExitInfo.REASON_CRASH,
                        ApplicationExitInfo.REASON_CRASH_NATIVE -> crashes++
                        ApplicationExitInfo.REASON_ANR -> anrs++
                        ApplicationExitInfo.REASON_LOW_MEMORY,
                        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> kills++
                    }
                }

                anyAvailable = true
                results.add(
                    ExitStatEntity(
                        packageName = pkg,
                        scanId = scanId,
                        reason = "Crashes: $crashes, ANRs: $anrs, Resource Kills: $kills",
                        count30d = crashes + anrs + kills,
                        available = true
                    )
                )
            } catch (e: SecurityException) {
                // Ordinary apps without privileged ADB access cannot read other packages' exit info
                results.add(
                    ExitStatEntity(
                        packageName = pkg,
                        scanId = scanId,
                        reason = "Not available on this device",
                        count30d = 0,
                        available = false
                    )
                )
            } catch (e: Exception) {
                results.add(
                    ExitStatEntity(
                        packageName = pkg,
                        scanId = scanId,
                        reason = "Not available on this device",
                        count30d = 0,
                        available = false
                    )
                )
            }
        }

        return ExitCollectResult(results, anyAvailable)
    }
}
