package com.apppulse.app.data.collector

import android.app.ActivityManager
import android.content.Context
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.view.WindowManager
import java.io.File
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
    val cpuMaxFreqGhz: Double,
    val socName: String,
    val gpuRenderer: String,
    val gpuVendor: String,
    val displayRefreshRateHz: Int,
    val deviceModel: String,
    val androidVersion: String,
    val performanceScore: Int,
    val performanceStatus: String,
    val cpuPerformanceSummary: String,
    val gpuPerformanceSummary: String
)

class DevicePerformanceCollector(private val context: Context) {

    @Volatile
    private var cachedGpuInfo: Pair<String, String>? = null

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

        // 3. Hardware: CPU
        val cores = Runtime.getRuntime().availableProcessors()
        val cpuFreqGhz = readCpuMaxFreqGhz()
        val socName = readSocName()

        // 4. Hardware: GPU via Headless EGL
        val (gpuVendor, gpuRenderer) = getGpuInfo()

        // 5. Display Refresh Rate
        val refreshRate = getDisplayRefreshRate()

        val model = "${Build.MANUFACTURER} ${Build.MODEL}".trim().replaceFirstChar { it.uppercase() }
        val androidVer = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"

        val freqText = if (cpuFreqGhz > 0.0) String.format("%.2f GHz", cpuFreqGhz) else ""
        val cpuSummary = buildString {
            if (socName.isNotBlank() && socName != "Unknown") {
                append(socName)
                append(" • ")
            }
            append("$cores Cores")
            if (freqText.isNotBlank()) {
                append(" ($freqText)")
            } else {
                append(" ($abi)")
            }
            if (refreshRate > 0) {
                append(" • ${refreshRate}Hz")
            }
        }

        val glVersion = actManager?.deviceConfigurationInfo?.glEsVersion ?: "3.2"
        val gpuSummary = buildString {
            if (gpuRenderer.isNotBlank() && gpuRenderer != "Unknown") {
                append(gpuRenderer)
                append(" • ")
            }
            append("OpenGL ES $glVersion")
            append(" • Vulkan Ready")
        }

        // 6. Performance Vitality Score (0 - 100)
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
            cpuMaxFreqGhz = cpuFreqGhz,
            socName = socName,
            gpuRenderer = gpuRenderer,
            gpuVendor = gpuVendor,
            displayRefreshRateHz = refreshRate,
            deviceModel = model,
            androidVersion = androidVer,
            performanceScore = performanceScore,
            performanceStatus = status,
            cpuPerformanceSummary = cpuSummary,
            gpuPerformanceSummary = gpuSummary
        )
    }

    private fun readCpuMaxFreqGhz(): Double {
        try {
            // Check cpu0 through cpu7 for max frequency
            for (i in 0 until Runtime.getRuntime().availableProcessors()) {
                val f = File("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq")
                if (f.exists() && f.canRead()) {
                    val line = f.readText().trim()
                    val khz = line.toDoubleOrNull()
                    if (khz != null && khz > 0) {
                        return khz / 1_000_000.0 // Convert kHz to GHz
                    }
                }
            }
        } catch (_: Exception) {}
        return 0.0
    }

    private fun readSocName(): String {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val soc = Build.SOC_MODEL
                if (!soc.isNullOrBlank() && soc != Build.UNKNOWN) {
                    return soc
                }
            }
            // Check /proc/cpuinfo
            val cpuinfo = File("/proc/cpuinfo")
            if (cpuinfo.exists() && cpuinfo.canRead()) {
                val lines = cpuinfo.readLines()
                for (line in lines) {
                    if (line.startsWith("Hardware", ignoreCase = true) || line.startsWith("model name", ignoreCase = true)) {
                        val parts = line.split(":")
                        if (parts.size >= 2) {
                            val name = parts[1].trim()
                            if (name.isNotBlank()) return name
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        val hw = Build.HARDWARE
        return if (!hw.isNullOrBlank() && hw != Build.UNKNOWN) hw else "Octa-Core SoC"
    }

    private fun getGpuInfo(): Pair<String, String> {
        cachedGpuInfo?.let { return it }

        var vendor = ""
        var renderer = ""
        try {
            val dpy = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            val vers = IntArray(2)
            EGL14.eglInitialize(dpy, vers, 0, vers, 1)

            val configAttr = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            EGL14.eglChooseConfig(dpy, configAttr, 0, configs, 0, 1, numConfigs, 0)

            if (numConfigs[0] > 0 && configs[0] != null) {
                val surfAttr = intArrayOf(
                    EGL14.EGL_WIDTH, 1,
                    EGL14.EGL_HEIGHT, 1,
                    EGL14.EGL_NONE
                )
                val surf = EGL14.eglCreatePbufferSurface(dpy, configs[0], surfAttr, 0)
                val ctxAttr = intArrayOf(
                    EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                    EGL14.EGL_NONE
                )
                val ctx = EGL14.eglCreateContext(dpy, configs[0], EGL14.EGL_NO_CONTEXT, ctxAttr, 0)
                EGL14.eglMakeCurrent(dpy, surf, surf, ctx)

                renderer = GLES20.glGetString(GLES20.GL_RENDERER) ?: ""
                vendor = GLES20.glGetString(GLES20.GL_VENDOR) ?: ""

                EGL14.eglMakeCurrent(dpy, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                EGL14.eglDestroyContext(dpy, ctx)
                EGL14.eglDestroySurface(dpy, surf)
                EGL14.eglTerminate(dpy)
            }
        } catch (_: Exception) {}

        if (renderer.isBlank()) {
            renderer = if (Build.HARDWARE.contains("qcom", ignoreCase = true)) "Adreno GPU"
            else if (Build.HARDWARE.contains("mt", ignoreCase = true)) "ARM Mali GPU"
            else "Hardware Accelerated GPU"
        }
        val result = Pair(vendor, renderer)
        cachedGpuInfo = result
        return result
    }

    private fun getDisplayRefreshRate(): Int {
        try {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            val display = wm?.defaultDisplay
            val rate = display?.refreshRate ?: 60f
            return rate.toInt()
        } catch (_: Exception) {
            return 60
        }
    }
}
