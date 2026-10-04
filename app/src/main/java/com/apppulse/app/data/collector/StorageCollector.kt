package com.apppulse.app.data.collector

import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.os.storage.StorageManager
import com.apppulse.app.data.local.entities.StorageStatEntity
import java.io.File

class StorageCollector(private val context: Context) {

    data class DeviceStorageInfo(
        val totalBytes: Long,
        val freeBytes: Long,
        val usedBytes: Long,
        val isAvailable: Boolean
    )

    data class StorageCollectResult(
        val stats: List<StorageStatEntity>,
        val deviceInfo: DeviceStorageInfo,
        val isAvailable: Boolean
    )

    fun getDeviceStorageInfo(): DeviceStorageInfo {
        return try {
            val statFs = StatFs(Environment.getDataDirectory().path)
            val blockSize = statFs.blockSizeLong
            val totalBlocks = statFs.blockCountLong
            val availableBlocks = statFs.availableBlocksLong

            val totalBytes = totalBlocks * blockSize
            val freeBytes = availableBlocks * blockSize
            val usedBytes = totalBytes - freeBytes

            DeviceStorageInfo(
                totalBytes = totalBytes,
                freeBytes = freeBytes,
                usedBytes = usedBytes,
                isAvailable = true
            )
        } catch (e: Exception) {
            DeviceStorageInfo(
                totalBytes = 64L * 1024 * 1024 * 1024,
                freeBytes = 20L * 1024 * 1024 * 1024,
                usedBytes = 44L * 1024 * 1024 * 1024,
                isAvailable = false
            )
        }
    }

    fun collectStorageStats(
        scanId: Long,
        packageNames: List<String>
    ): StorageCollectResult {
        val deviceInfo = getDeviceStorageInfo()
        val stats = mutableListOf<StorageStatEntity>()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return StorageCollectResult(
                stats = emptyList(),
                deviceInfo = deviceInfo,
                isAvailable = false
            )
        }

        val storageStatsManager = context.getSystemService(Context.STORAGE_STATS_SERVICE) as? StorageStatsManager
        val userHandle = Process.myUserHandle()

        if (storageStatsManager == null) {
            return StorageCollectResult(
                stats = emptyList(),
                deviceInfo = deviceInfo,
                isAvailable = false
            )
        }

        var successCount = 0
        for (pkg in packageNames) {
            try {
                val appStats = storageStatsManager.queryStatsForPackage(
                    StorageManager.UUID_DEFAULT,
                    pkg,
                    userHandle
                )
                val appBytes = appStats.appBytes
                val dataBytes = appStats.dataBytes
                val cacheBytes = appStats.cacheBytes
                val totalBytes = appBytes + dataBytes

                stats.add(
                    StorageStatEntity(
                        packageName = pkg,
                        scanId = scanId,
                        appBytes = appBytes,
                        dataBytes = dataBytes,
                        cacheBytes = cacheBytes,
                        totalBytes = totalBytes
                    )
                )
                successCount++
            } catch (e: SecurityException) {
                // Usage access not granted for storage stats
                // Fallback gracefully without crash
                stats.add(createFallbackStat(pkg, scanId))
            } catch (e: Exception) {
                // NameNotFoundException or other platform errors
                stats.add(createFallbackStat(pkg, scanId))
            }
        }

        return StorageCollectResult(
            stats = stats,
            deviceInfo = deviceInfo,
            isAvailable = successCount > 0
        )
    }

    private fun createFallbackStat(pkg: String, scanId: Long): StorageStatEntity {
        // Fallback estimate based on apk file size if accessible
        var apkSize = 50L * 1024 * 1024 // Default 50MB estimate
        try {
            val appInfo = context.packageManager.getApplicationInfo(pkg, 0)
            val sourceDir = appInfo.sourceDir
            if (sourceDir != null) {
                val file = File(sourceDir)
                if (file.exists()) {
                    apkSize = file.length()
                }
            }
        } catch (e: Exception) {
            // Ignore
        }

        return StorageStatEntity(
            packageName = pkg,
            scanId = scanId,
            appBytes = apkSize,
            dataBytes = apkSize / 2,
            cacheBytes = 10L * 1024 * 1024,
            totalBytes = apkSize + (apkSize / 2)
        )
    }
}
