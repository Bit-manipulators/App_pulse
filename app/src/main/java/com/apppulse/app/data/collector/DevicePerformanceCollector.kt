package com.apppulse.app.data.collector

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import kotlin.math.max
import kotlin.math.min

data class DevicePerformanceMetrics(
    val totalRamBytes: Long,
    val availableRamBytes: Long,
    val usedRamBytes: Long,
    val ramUsagePercent: Int,
    val totalStorageBytes: Long,
    val freeStorageBytes: Long,
    val usedStorageBytes: Long,
    val storageUsagePercent: Int,
    val cpuCores: Int,
    val deviceModel: String,
    val androidVersion: String,
    val performanceScore: Int,
    val performanceStatus: String
)

class DevicePerformanceCollector(private val context: Context) {

    fun collectMetrics(): DevicePerformanceMetrics {
        // 1. RAM / Memory
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager?.getMemoryInfo(memInfo)

        val totalRam = memInfo.totalMem
        val availRam = memInfo.availMem
        val usedRam = max(0L, totalRam - availRam)
        val ramPercent = if (totalRam > 0) ((usedRam.toDouble() / totalRam.toDouble()) * 100).toInt() else 0

        // 2. Storage
        val dataDir = Environment.getDataDirectory()
        val stat = StatFs(dataDir.path)
        val blockSize = stat.blockSizeLong
        val totalBlocks = stat.blockCountLong
        val availBlocks = stat.availableBlocksLong

        val totalStorage = totalBlocks * blockSize
        val freeStorage = availBlocks * blockSize
        val usedStorage = max(0L, totalStorage - freeStorage)
        val storagePercent = if (totalStorage > 0) ((usedStorage.toDouble() / totalStorage.toDouble()) * 100).toInt() else 0

        // 3. Hardware & OS
        val cores = Runtime.getRuntime().availableProcessors()
        val model = "${Build.MANUFACTURER} ${Build.MODEL}".trim().replaceFirstChar { it.uppercase() }
        val androidVer = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

        // 4. Performance Vitality Score (0 - 100)
        // High free RAM and high free storage = higher score
        val ramHealth = 100 - ramPercent
        val storageHealth = 100 - storagePercent
        val rawScore = (ramHealth * 0.5) + (storageHealth * 0.5)
        val performanceScore = min(100, max(10, rawScore.toInt()))

        val status = when {
            performanceScore >= 75 -> "Optimal"
            performanceScore >= 55 -> "Good"
            performanceScore >= 35 -> "Moderate"
            else -> "Heavy Load"
        }

        return DevicePerformanceMetrics(
            totalRamBytes = totalRam,
            availableRamBytes = availRam,
            usedRamBytes = usedRam,
            ramUsagePercent = ramPercent,
            totalStorageBytes = totalStorage,
            freeStorageBytes = freeStorage,
            usedStorageBytes = usedStorage,
            storageUsagePercent = storagePercent,
            cpuCores = cores,
            deviceModel = model,
            androidVersion = androidVer,
            performanceScore = performanceScore,
            performanceStatus = status
        )
    }
}
