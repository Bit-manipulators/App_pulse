package com.apppulse.app.domain.stealth

enum class ThreatLevel {
    CRITICAL,
    HIGH,
    MODERATE,
    SAFE
}

enum class HeuristicTag(val label: String, val iconDescription: String) {
    NO_LAUNCHER_ICON("No Launcher Icon", "Headless background package with no app drawer icon"),
    ACTIVE_ACCESSIBILITY("Active Accessibility", "Holding active system accessibility privileges to scrape screens & keystrokes"),
    ACTIVE_DEVICE_ADMIN("Device Admin Active", "Elevated device administrator rights preventing uninstallation"),
    SYSTEM_CAMOUFLAGE("System Camouflage", "Deceptively named like a critical Android OS service"),
    OVERLAY_PERMISSION("Screen Overlay Privilege", "Permitted to draw on top of all other running applications"),
    NOTIFICATION_LISTENER("Notification Listener", "Active listener intercepting push alerts and messages"),
    SIDELOADED_ORIGIN("Sideloaded APK", "Installed outside Google Play Store or certified vendor store")
}

data class StealthAppThreat(
    val packageName: String,
    val label: String,
    val versionName: String,
    val isSystem: Boolean,
    val threatScore: Int,
    val threatLevel: ThreatLevel,
    val hasNoLauncherIcon: Boolean,
    val isAccessibilityActive: Boolean,
    val isDeviceAdminActive: Boolean,
    val hasSystemCamouflage: Boolean,
    val hasOverlayPermission: Boolean,
    val hasNotificationListener: Boolean,
    val isSideloaded: Boolean,
    val tags: List<HeuristicTag>,
    val heuristicReasons: List<String>,
    val installTime: Long,
    val installerName: String? = null
)
