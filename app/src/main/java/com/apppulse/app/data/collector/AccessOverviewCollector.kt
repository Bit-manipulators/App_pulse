package com.apppulse.app.data.collector

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build

data class PermissionCategorySummary(
    val name: String,
    val iconName: String,
    val count: Int,
    val highRisk: Boolean = false
)

data class AccessOverviewMetrics(
    val totalAppsAudited: Int,
    val locationAppsCount: Int,
    val cameraAppsCount: Int,
    val micAppsCount: Int,
    val smsAppsCount: Int,
    val contactsAppsCount: Int,
    val categories: List<PermissionCategorySummary>
)

class AccessOverviewCollector(private val context: Context) {

    fun collectAccessSummary(): AccessOverviewMetrics {
        val pm = context.packageManager
        val launchIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfos = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(
                    launchIntent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(launchIntent, PackageManager.MATCH_ALL)
            }
        } catch (e: Exception) {
            emptyList()
        }

        var locCount = 0
        var camCount = 0
        var micCount = 0
        var smsCount = 0
        var contactCount = 0
        val seen = mutableSetOf<String>()

        for (resolveInfo in resolveInfos) {
            val pkg = resolveInfo.activityInfo.packageName
            if (seen.contains(pkg)) continue
            seen.add(pkg)

            try {
                val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS)
                }

                val req = pkgInfo.requestedPermissions ?: emptyArray()
                val flags = pkgInfo.requestedPermissionsFlags ?: IntArray(0)

                var hasLoc = false
                var hasCam = false
                var hasMic = false
                var hasSms = false
                var hasContact = false

                for (i in req.indices) {
                    val isGranted = if (i < flags.size) {
                        (flags[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
                    } else false

                    if (!isGranted) continue

                    when (req[i]) {
                        "android.permission.ACCESS_FINE_LOCATION",
                        "android.permission.ACCESS_COARSE_LOCATION",
                        "android.permission.ACCESS_BACKGROUND_LOCATION" -> hasLoc = true

                        "android.permission.CAMERA" -> hasCam = true

                        "android.permission.RECORD_AUDIO" -> hasMic = true

                        "android.permission.SEND_SMS",
                        "android.permission.RECEIVE_SMS",
                        "android.permission.READ_SMS",
                        "android.permission.READ_CALL_LOG" -> hasSms = true

                        "android.permission.READ_CONTACTS",
                        "android.permission.WRITE_CONTACTS" -> hasContact = true
                    }
                }

                if (hasLoc) locCount++
                if (hasCam) camCount++
                if (hasMic) micCount++
                if (hasSms) smsCount++
                if (hasContact) contactCount++
            } catch (e: Exception) {
                // Ignore package lookup errors
            }
        }

        val categories = listOf(
            PermissionCategorySummary("Location", "LocationOn", locCount, highRisk = true),
            PermissionCategorySummary("Camera", "PhotoCamera", camCount),
            PermissionCategorySummary("Microphone", "Mic", micCount, highRisk = true),
            PermissionCategorySummary("SMS & Calls", "Chat", smsCount, highRisk = true),
            PermissionCategorySummary("Contacts", "Contacts", contactCount)
        )

        return AccessOverviewMetrics(
            totalAppsAudited = seen.size,
            locationAppsCount = locCount,
            cameraAppsCount = camCount,
            micAppsCount = micCount,
            smsAppsCount = smsCount,
            contactsAppsCount = contactCount,
            categories = categories
        )
    }
}
