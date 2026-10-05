package com.apppulse.app.domain.stealth

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.app.NotificationManagerCompat
import java.util.Locale
import kotlin.math.min

class StealthHunterEngine(private val context: Context) {

    companion object {
        // Strict whitelist of reputable mainstream apps that should NEVER be flagged
        private val TRUSTED_PACKAGES = setOf(
            "com.whatsapp",
            "com.whatsapp.w4b",
            "org.telegram.messenger",
            "com.facebook.orca",
            "com.facebook.katana",
            "com.instagram.android",
            "com.spotify.music",
            "com.netflix.mediaclient",
            "com.truecaller",
            "us.zoom.videomeetings",
            "com.ubercab",
            "com.snapchat.android",
            "com.discord",
            "com.reddit.frontpage",
            "com.twitter.android",
            "com.google.android.youtube",
            "com.google.android.gm",
            "com.google.android.apps.maps",
            "com.google.android.apps.photos",
            "com.google.android.apps.messaging",
            "com.google.android.dialer",
            "com.google.android.contacts",
            "com.apppulse.app"
        )

        // Trusted OEM & platform namespace prefixes
        private val TRUSTED_NAMESPACE_PREFIXES = listOf(
            "com.google.android.",
            "com.android.",
            "com.samsung.",
            "com.oppo.",
            "com.coloros.",
            "com.heytap.",
            "com.oneplus.",
            "com.realme.",
            "com.xiaomi.",
            "com.miui.",
            "com.motorola.",
            "com.vivo.",
            "com.bbk.",
            "com.asus.",
            "com.sony."
        )

        // Deceptive spoofing names commonly utilized by mobile stalkerware / spyware
        private val CAMOUFLAGE_NAMES = listOf(
            "system update",
            "device health",
            "google play",
            "google play services",
            "android system",
            "system service",
            "sync service",
            "security service",
            "wifi service",
            "battery health",
            "device support",
            "device service",
            "system framework",
            "settings helper",
            "device monitor",
            "backup service"
        )

        private val OFFICIAL_STORE_INSTALLERS = setOf(
            "com.android.vending",
            "com.google.android.feedback",
            "com.sec.android.app.samsungapps",
            "com.amazon.venezia",
            "com.oppo.market",
            "com.heytap.market",
            "com.xiaomi.mipicks",
            "com.vivo.appstore"
        )
    }

    /**
     * Inspects all installed packages on the device and returns list of suspicious or stealth threats.
     */
    fun scanForThreats(whitelistedPackages: Set<String> = emptySet()): List<StealthAppThreat> {
        val pm = context.packageManager
        val threats = mutableListOf<StealthAppThreat>()

        val flags = PackageManager.GET_PERMISSIONS or
                PackageManager.GET_SERVICES or
                PackageManager.GET_RECEIVERS

        val packages: List<PackageInfo> = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledPackages(flags)
            }
        } catch (e: Exception) {
            emptyList()
        }

        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        val enabledAccessibilities = getEnabledAccessibilityServices()
        val enabledNotificationListeners = NotificationManagerCompat.getEnabledListenerPackages(context)

        for (pkg in packages) {
            val pkgName = pkg.packageName
            val appInfo = pkg.applicationInfo ?: continue

            // 1. Skip AppPulse itself
            if (pkgName == context.packageName) continue

            // 2. Skip user-whitelisted / trusted packages
            if (whitelistedPackages.contains(pkgName)) continue

            // 3. Skip explicitly trusted packages (WhatsApp, Telegram, etc.)
            if (TRUSTED_PACKAGES.contains(pkgName)) continue

            // 4. Skip pre-installed system applications unless updated with a deceptive package
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            if (isSystem) continue

            // Skip trusted OEM vendor packages
            if (TRUSTED_NAMESPACE_PREFIXES.any { pkgName.startsWith(it) }) continue

            val label = appInfo.loadLabel(pm)?.toString() ?: pkgName
            val lowerLabel = label.lowercase(Locale.ROOT).trim()

            // Check if app has a visible launch intent in the app drawer
            val hasLauncher = pm.getLaunchIntentForPackage(pkgName) != null

            // Check for System Camouflage
            val hasSystemCamouflage = CAMOUFLAGE_NAMES.any { lowerLabel == it || lowerLabel.contains(it) }

            // ==========================================
            // ANTI-FALSE-POSITIVE GATE (Tier 1)
            // If the app has a normal visible launcher icon AND does not impersonate
            // system core components, it is fundamentally SAFE and bypassed immediately!
            // ==========================================
            if (hasLauncher && !hasSystemCamouflage) {
                continue
            }

            // Headless absence check
            val hasNoLauncherIcon = !hasLauncher

            // Active Accessibility Service inspection
            val isAccessibilityActive = isAccessibilityHijacking(pkg, enabledAccessibilities)

            // Active Device Administrator inspection
            val isDeviceAdminActive = isDeviceAdminHijacking(pkg, dpm)

            // Screen Overlay (SYSTEM_ALERT_WINDOW)
            val hasOverlayPermission = hasOverlayPrivilege(pkg)

            // Notification Listener
            val hasNotificationListener = enabledNotificationListeners.contains(pkgName)

            // Sideloaded outside official stores
            val installer = getInstaller(pkgName)
            val isSideloaded = installer == null || !OFFICIAL_STORE_INSTALLERS.contains(installer)

            // ==========================================
            // ANTI-FALSE-POSITIVE CONJUNCTION RULE (Tier 3)
            // A non-system app must possess real covert indicators to be flagged.
            // ==========================================
            val tags = mutableListOf<HeuristicTag>()
            val reasons = mutableListOf<String>()
            var score = 0

            if (hasNoLauncherIcon) {
                score += 35
                tags.add(HeuristicTag.NO_LAUNCHER_ICON)
                reasons.add("App has no launcher icon in the app drawer and remains hidden from the home screen.")
            }

            if (hasSystemCamouflage) {
                score += 25
                tags.add(HeuristicTag.SYSTEM_CAMOUFLAGE)
                reasons.add("Deceptively titled \"$label\" to disguise as an official Android system utility.")
            }

            if (isAccessibilityActive) {
                score += 35
                tags.add(HeuristicTag.ACTIVE_ACCESSIBILITY)
                reasons.add("Active Accessibility Service granted, allowing screen scraping, keystroke logging, and tap interception.")
            }

            if (isDeviceAdminActive) {
                score += 30
                tags.add(HeuristicTag.ACTIVE_DEVICE_ADMIN)
                reasons.add("Active Device Administrator privilege held, which can prevent standard uninstallation.")
            }

            if (hasOverlayPermission && (hasNoLauncherIcon || isAccessibilityActive || hasSystemCamouflage)) {
                score += 15
                tags.add(HeuristicTag.OVERLAY_PERMISSION)
                reasons.add("Permitted to draw persistent floating overlays on top of all other running applications.")
            }

            if (hasNotificationListener && (hasNoLauncherIcon || hasSystemCamouflage)) {
                score += 20
                tags.add(HeuristicTag.NOTIFICATION_LISTENER)
                reasons.add("Active notification listener capable of intercepting two-factor codes and incoming chat alerts.")
            }

            if (isSideloaded && (hasNoLauncherIcon || isAccessibilityActive || isDeviceAdminActive)) {
                score += 10
                tags.add(HeuristicTag.SIDELOADED_ORIGIN)
                reasons.add("Sideloaded outside the Google Play Store (installer: ${installer ?: "Manual APK/Unknown"}).")
            }

            val finalScore = min(100, score)

            // Only flag if threat score reaches at least MODERATE threshold (>= 30)
            if (finalScore >= 30) {
                val level = when {
                    finalScore >= 75 -> ThreatLevel.CRITICAL
                    finalScore >= 50 -> ThreatLevel.HIGH
                    else -> ThreatLevel.MODERATE
                }

                threats.add(
                    StealthAppThreat(
                        packageName = pkgName,
                        label = label,
                        versionName = pkg.versionName ?: "1.0",
                        isSystem = false,
                        threatScore = finalScore,
                        threatLevel = level,
                        hasNoLauncherIcon = hasNoLauncherIcon,
                        isAccessibilityActive = isAccessibilityActive,
                        isDeviceAdminActive = isDeviceAdminActive,
                        hasSystemCamouflage = hasSystemCamouflage,
                        hasOverlayPermission = hasOverlayPermission,
                        hasNotificationListener = hasNotificationListener,
                        isSideloaded = isSideloaded,
                        tags = tags,
                        heuristicReasons = reasons,
                        installTime = pkg.firstInstallTime,
                        installerName = installer
                    )
                )
            }
        }

        return threats.sortedByDescending { it.threatScore }
    }

    private fun getEnabledAccessibilityServices(): Set<String> {
        val result = mutableSetOf<String>()
        try {
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            if (!enabled.isNullOrBlank()) {
                enabled.split(":").forEach {
                    val comp = ComponentName.unflattenFromString(it)
                    if (comp != null) {
                        result.add(comp.packageName)
                    }
                }
            }
        } catch (_: Exception) {}
        return result
    }

    private fun isAccessibilityHijacking(pkg: PackageInfo, enabledPackages: Set<String>): Boolean {
        if (!enabledPackages.contains(pkg.packageName)) return false
        val services = pkg.services ?: return false
        for (service in services) {
            if (service.permission == "android.permission.BIND_ACCESSIBILITY_SERVICE") {
                return true
            }
        }
        return false
    }

    private fun isDeviceAdminHijacking(pkg: PackageInfo, dpm: DevicePolicyManager?): Boolean {
        if (dpm == null) return false
        val receivers = pkg.receivers ?: return false
        for (receiver in receivers) {
            if (receiver.permission == "android.permission.BIND_DEVICE_ADMIN") {
                val comp = ComponentName(pkg.packageName, receiver.name)
                if (dpm.isAdminActive(comp)) {
                    return true
                }
            }
        }
        return false
    }

    private fun hasOverlayPrivilege(pkg: PackageInfo): Boolean {
        val requested = pkg.requestedPermissions ?: return false
        return requested.contains("android.permission.SYSTEM_ALERT_WINDOW")
    }

    private fun getInstaller(packageName: String): String? {
        val pm = context.packageManager
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val info = pm.getInstallSourceInfo(packageName)
                info.installingPackageName ?: info.initiatingPackageName
            } else {
                @Suppress("DEPRECATION")
                pm.getInstallerPackageName(packageName)
            }
        } catch (_: Exception) {
            null
        }
    }
}
