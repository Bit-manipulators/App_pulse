package com.apppulse.app.domain.apk

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.apppulse.app.data.local.entities.ConfigIndicatorEntity
import java.io.File
import java.io.FileOutputStream

data class ApkScanReport(
    val fileName: String,
    val packageName: String,
    val appName: String,
    val versionName: String,
    val targetSdk: Int,
    val fileSizeBytes: Long,
    val indicators: List<ConfigIndicatorEntity>,
    val sensitivePermissions: List<String>,
    val isSuccess: Boolean,
    val errorMessage: String? = null
)

object ApkScanner {

    fun scanApk(context: Context, uri: Uri, fileName: String): ApkScanReport {
        var tempFile: File? = null
        return try {
            val cacheDir = context.cacheDir
            tempFile = File.createTempFile("scan_", ".apk", cacheDir)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return ApkScanReport(
                fileName = fileName,
                packageName = "",
                appName = "",
                versionName = "",
                targetSdk = 0,
                fileSizeBytes = 0,
                indicators = emptyList(),
                sensitivePermissions = emptyList(),
                isSuccess = false,
                errorMessage = "Unable to read APK file stream."
            )

            val archivePath = tempFile.absolutePath
            val pm = context.packageManager

            val flags = PackageManager.GET_PERMISSIONS or
                    PackageManager.GET_SERVICES or
                    PackageManager.GET_RECEIVERS or
                    PackageManager.GET_PROVIDERS or
                    PackageManager.GET_ACTIVITIES

            val pkgInfo = pm.getPackageArchiveInfo(archivePath, flags)
                ?: return ApkScanReport(
                    fileName = fileName,
                    packageName = "",
                    appName = "",
                    versionName = "",
                    targetSdk = 0,
                    fileSizeBytes = tempFile.length(),
                    indicators = emptyList(),
                    sensitivePermissions = emptyList(),
                    isSuccess = false,
                    errorMessage = "Failed to parse APK manifest. Package may be corrupt or invalid."
                )

            val appInfo = pkgInfo.applicationInfo
            appInfo?.sourceDir = archivePath
            appInfo?.publicSourceDir = archivePath

            val packageName = pkgInfo.packageName ?: "unknown"
            val appLabel = appInfo?.loadLabel(pm)?.toString() ?: packageName
            val versionName = pkgInfo.versionName ?: "1.0"
            val targetSdk = appInfo?.targetSdkVersion ?: 35
            val fileSizeBytes = tempFile.length()

            val indicators = mutableListOf<ConfigIndicatorEntity>()

            if (appInfo != null) {
                // 1. Debuggable build
                if ((appInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
                    indicators.add(
                        ConfigIndicatorEntity(
                            packageName = packageName,
                            scanId = 0,
                            ruleId = "APK_DEBUGGABLE",
                            title = "Debuggable build enabled",
                            severity = "CRITICAL",
                            description = "APK was built with debug mode active, exposing memory inspection and adb debug hooks.",
                            remediationHint = "Compile package with release signing configuration and android:debuggable='false'."
                        )
                    )
                }

                // 2. Cleartext traffic
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    if ((appInfo.flags and ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC) != 0) {
                        indicators.add(
                            ConfigIndicatorEntity(
                                packageName = packageName,
                                scanId = 0,
                                ruleId = "APK_CLEARTEXT",
                                title = "Cleartext HTTP traffic allowed",
                                severity = "WARN",
                                description = "APK allows unencrypted network communication without mandatory HTTPS enforcement.",
                                remediationHint = "Set cleartextTrafficPermitted='false' in Network Security Config."
                            )
                        )
                    }
                }

                // 3. Backup allowed
                if ((appInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP) != 0) {
                    indicators.add(
                        ConfigIndicatorEntity(
                            packageName = packageName,
                            scanId = 0,
                            ruleId = "APK_BACKUP",
                            title = "Application backup allowed",
                            severity = "INFO",
                            description = "Application state may be extracted via system adb backups.",
                            remediationHint = "Consider disabling backup for sensitive authentication stores."
                        )
                    )
                }
            }

            // 4. Unprotected exported components
            var unprotectedExported = 0
            pkgInfo.services?.forEach { s ->
                if (s.exported && (s.permission == null || s.permission.isEmpty())) unprotectedExported++
            }
            pkgInfo.receivers?.forEach { r ->
                if (r.exported && (r.permission == null || r.permission.isEmpty())) unprotectedExported++
            }
            pkgInfo.providers?.forEach { p ->
                if (p.exported && (p.readPermission == null && p.writePermission == null)) unprotectedExported++
            }

            if (unprotectedExported > 0) {
                indicators.add(
                    ConfigIndicatorEntity(
                        packageName = packageName,
                        scanId = 0,
                        ruleId = "APK_EXPORTED_COMPONENTS",
                        title = "Unprotected exported components",
                        severity = "WARN",
                        description = "$unprotectedExported exported background component(s) have no permission checks declared.",
                        remediationHint = "Enforce permissions on exported components or set exported='false'."
                    )
                )
            }

            // 5. Target SDK lag
            if (targetSdk < 34) {
                indicators.add(
                    ConfigIndicatorEntity(
                        packageName = packageName,
                        scanId = 0,
                        ruleId = "APK_OLD_SDK",
                        title = "Outdated Target SDK ($targetSdk)",
                        severity = "WARN",
                        description = "APK targets an older Android platform level, bypassing modern security enforcements.",
                        remediationHint = "Upgrade targetSdkVersion to 34 or 35 in build configuration."
                    )
                )
            }

            // Sensitive permissions
            val sensitiveList = mutableListOf<String>()
            val reqPerms = pkgInfo.requestedPermissions ?: emptyArray()
            for (p in reqPerms) {
                when {
                    p.contains("SMS") -> sensitiveList.add("SMS ($p)")
                    p.contains("LOCATION") -> sensitiveList.add("Location ($p)")
                    p.contains("CAMERA") -> sensitiveList.add("Camera")
                    p.contains("RECORD_AUDIO") -> sensitiveList.add("Microphone")
                    p.contains("CONTACTS") -> sensitiveList.add("Contacts")
                    p.contains("CALL_LOG") -> sensitiveList.add("Call Log")
                }
            }

            // Sort indicators by severity: CRITICAL > WARN > INFO
            val severityOrder = mapOf("CRITICAL" to 1, "WARN" to 2, "INFO" to 3)
            val sortedIndicators = indicators.sortedBy { severityOrder[it.severity] ?: 4 }

            ApkScanReport(
                fileName = fileName,
                packageName = packageName,
                appName = appLabel,
                versionName = versionName,
                targetSdk = targetSdk,
                fileSizeBytes = fileSizeBytes,
                indicators = sortedIndicators,
                sensitivePermissions = sensitiveList.distinct(),
                isSuccess = true
            )
        } catch (e: Exception) {
            ApkScanReport(
                fileName = fileName,
                packageName = "",
                appName = "",
                versionName = "",
                targetSdk = 0,
                fileSizeBytes = 0,
                indicators = emptyList(),
                sensitivePermissions = emptyList(),
                isSuccess = false,
                errorMessage = e.localizedMessage ?: "Unknown parsing error."
            )
        } finally {
            try {
                tempFile?.delete()
            } catch (e: Exception) {
                // Ignore cleanup error
            }
        }
    }
}
