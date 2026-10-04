package com.apppulse.app.data.collector

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.apppulse.app.data.local.entities.AppSnapshotEntity

class PackageCollector(private val context: Context) {

    data class PackageCollectResult(
        val snapshots: List<AppSnapshotEntity>,
        val isAvailable: Boolean = true
    )

    fun collectLaunchableApps(): PackageCollectResult {
        val pm = context.packageManager
        val launchIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        return try {
            val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(
                    launchIntent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(launchIntent, PackageManager.MATCH_ALL)
            }

            val seenPackages = mutableSetOf<String>()
            val snapshots = mutableListOf<AppSnapshotEntity>()

            for (resolveInfo in resolveInfos) {
                val pkgName = resolveInfo.activityInfo.packageName
                if (seenPackages.contains(pkgName)) continue
                seenPackages.add(pkgName)

                try {
                    val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        pm.getPackageInfo(
                            pkgName,
                            PackageManager.PackageInfoFlags.of(0)
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        pm.getPackageInfo(pkgName, 0)
                    }

                    val appInfo = pkgInfo.applicationInfo
                    val label = resolveInfo.loadLabel(pm)?.toString()
                        ?: appInfo?.loadLabel(pm)?.toString()
                        ?: pkgName

                    val isSystem = appInfo?.let {
                        (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                                (it.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                    } ?: false

                    val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        pkgInfo.longVersionCode
                    } else {
                        @Suppress("DEPRECATION")
                        pkgInfo.versionCode.toLong()
                    }

                    val category = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        appInfo?.category ?: ApplicationInfo.CATEGORY_UNDEFINED
                    } else {
                        ApplicationInfo.CATEGORY_UNDEFINED
                    }

                    snapshots.add(
                        AppSnapshotEntity(
                            packageName = pkgName,
                            label = label,
                            versionName = pkgInfo.versionName ?: "1.0",
                            versionCode = versionCode,
                            targetSdk = appInfo?.targetSdkVersion ?: 35,
                            installTime = pkgInfo.firstInstallTime,
                            updateTime = pkgInfo.lastUpdateTime,
                            category = category,
                            isSystem = isSystem
                        )
                    )
                } catch (e: Exception) {
                    // Gracefully skip unresolvable packages
                }
            }

            PackageCollectResult(snapshots = snapshots, isAvailable = true)
        } catch (e: Exception) {
            PackageCollectResult(snapshots = emptyList(), isAvailable = false)
        }
    }
}
