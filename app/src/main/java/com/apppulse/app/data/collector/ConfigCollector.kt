package com.apppulse.app.data.collector

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.apppulse.app.data.local.entities.AppSnapshotEntity
import com.apppulse.app.data.local.entities.ConfigIndicatorEntity

class ConfigCollector(private val context: Context) {

    fun collectIndicatorsForApps(
        scanId: Long,
        snapshots: List<AppSnapshotEntity>
    ): List<ConfigIndicatorEntity> {
        val pm = context.packageManager
        val indicators = mutableListOf<ConfigIndicatorEntity>()

        for (snapshot in snapshots) {
            try {
                val flags = PackageManager.GET_SERVICES or
                        PackageManager.GET_RECEIVERS or
                        PackageManager.GET_PROVIDERS

                val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(snapshot.packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(snapshot.packageName, flags)
                }

                val appInfo = pkgInfo.applicationInfo ?: continue

                // 1. Debuggable build
                if ((appInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
                    indicators.add(
                        ConfigIndicatorEntity(
                            packageName = snapshot.packageName,
                            scanId = scanId,
                            ruleId = "CONFIG_DEBUGGABLE",
                            title = "Debuggable application build",
                            severity = "CRITICAL",
                            description = "Application has debug flags active, which may expose runtime memory and inspection hooks.",
                            remediationHint = "Review if this is a development build or replace with release package."
                        )
                    )
                }

                // 2. Cleartext traffic
                val usesCleartext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    (appInfo.flags and ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC) != 0
                } else {
                    true
                }
                if (usesCleartext) {
                    indicators.add(
                        ConfigIndicatorEntity(
                            packageName = snapshot.packageName,
                            scanId = scanId,
                            ruleId = "CONFIG_CLEARTEXT",
                            title = "Cleartext HTTP traffic allowed",
                            severity = "WARN",
                            description = "Application configuration permits unencrypted HTTP network communication.",
                            remediationHint = "Ensure network traffic uses HTTPS and TLS 1.3 transport security."
                        )
                    )
                }

                // 3. Backup allowed
                if ((appInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP) != 0) {
                    indicators.add(
                        ConfigIndicatorEntity(
                            packageName = snapshot.packageName,
                            scanId = scanId,
                            ruleId = "CONFIG_BACKUP",
                            title = "Application backup enabled",
                            severity = "INFO",
                            description = "Application data may be included in system backups or adb backup archives.",
                            remediationHint = "Verify whether sensitive application credentials are encrypted on-device."
                        )
                    )
                }

                // 4. Exported components without permission protection
                var unprotectedCount = 0
                pkgInfo.services?.forEach { service ->
                    if (service.exported && (service.permission == null || service.permission.isEmpty())) {
                        unprotectedCount++
                    }
                }
                pkgInfo.receivers?.forEach { receiver ->
                    if (receiver.exported && (receiver.permission == null || receiver.permission.isEmpty())) {
                        unprotectedCount++
                    }
                }
                pkgInfo.providers?.forEach { provider ->
                    if (provider.exported && (provider.readPermission == null && provider.writePermission == null)) {
                        unprotectedCount++
                    }
                }

                if (unprotectedCount > 0) {
                    indicators.add(
                        ConfigIndicatorEntity(
                            packageName = snapshot.packageName,
                            scanId = scanId,
                            ruleId = "CONFIG_EXPORTED_COMPONENTS",
                            title = "Unprotected exported components",
                            severity = "WARN",
                            description = "$unprotectedCount background component(s) are exported without explicit permission requirements.",
                            remediationHint = "Enforce component permission protection or restrict export attribute."
                        )
                    )
                }

                // 5. Target SDK lag
                if (snapshot.targetSdk < 34) {
                    val lag = 35 - snapshot.targetSdk
                    indicators.add(
                        ConfigIndicatorEntity(
                            packageName = snapshot.packageName,
                            scanId = scanId,
                            ruleId = "CONFIG_TARGET_SDK_LAG",
                            title = "Outdated Target SDK ($lag levels lag)",
                            severity = "WARN",
                            description = "App targets API ${snapshot.targetSdk}, missing recent Android runtime permission guards.",
                            remediationHint = "Check Google Play Store for updated package releases from developer."
                        )
                    )
                }

            } catch (e: Exception) {
                // Ignore package errors gracefully
            }
        }

        return indicators
    }
}
