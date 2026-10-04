package com.apppulse.app.data.collector

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.apppulse.app.data.local.entities.AppSnapshotEntity
import com.apppulse.app.data.local.entities.PermissionStatEntity
import com.apppulse.app.domain.rules.PermissionPlausibilityRules

class PermissionCollector(private val context: Context) {

    data class PermissionDetail(
        val categoryName: String,
        val isSensitive: Boolean,
        val plainExplanation: String
    )

    private val permissionMap = mapOf(
        "android.permission.SEND_SMS" to PermissionDetail("SMS", true, "Send SMS messages directly"),
        "android.permission.RECEIVE_SMS" to PermissionDetail("SMS", true, "Read incoming text messages"),
        "android.permission.READ_SMS" to PermissionDetail("SMS", true, "Read stored text messages"),
        "android.permission.RECEIVE_MMS" to PermissionDetail("SMS", true, "Monitor incoming MMS messages"),
        "android.permission.READ_CALL_LOG" to PermissionDetail("Call Log", true, "Read sensitive phone call history"),
        "android.permission.WRITE_CALL_LOG" to PermissionDetail("Call Log", true, "Modify phone call history"),
        "android.permission.PROCESS_OUTGOING_CALLS" to PermissionDetail("Call Log", true, "Intercept and redirect outgoing phone calls"),
        "android.permission.READ_CONTACTS" to PermissionDetail("Contacts", true, "Access personal address book and contacts"),
        "android.permission.WRITE_CONTACTS" to PermissionDetail("Contacts", true, "Modify stored address book records"),
        "android.permission.GET_ACCOUNTS" to PermissionDetail("Contacts", true, "Access device account credentials"),
        "android.permission.RECORD_AUDIO" to PermissionDetail("Microphone", true, "Record audio via device microphone"),
        "android.permission.ACCESS_FINE_LOCATION" to PermissionDetail("Location", true, "Access precise GPS physical coordinates"),
        "android.permission.ACCESS_COARSE_LOCATION" to PermissionDetail("Location", true, "Access approximate network-derived location"),
        "android.permission.ACCESS_BACKGROUND_LOCATION" to PermissionDetail("Location", true, "Track physical location in the background"),
        "android.permission.BODY_SENSORS" to PermissionDetail("Body Sensors", true, "Read vital biological sensor metrics (heart rate)"),
        "android.permission.BODY_SENSORS_BACKGROUND" to PermissionDetail("Body Sensors", true, "Monitor bodily health sensors continuously"),
        "android.permission.CAMERA" to PermissionDetail("Camera", true, "Capture photos and video recordings"),
        "android.permission.READ_CALENDAR" to PermissionDetail("Calendar", true, "Read personal calendar entries and events"),
        "android.permission.WRITE_CALENDAR" to PermissionDetail("Calendar", true, "Modify personal calendar entries and meetings"),
        "android.permission.READ_PHONE_STATE" to PermissionDetail("Phone State", true, "Access cellular network state and identifiers"),
        "android.permission.CALL_PHONE" to PermissionDetail("Phone State", true, "Initiate phone calls without confirmation"),
        "android.permission.READ_PHONE_NUMBERS" to PermissionDetail("Phone State", true, "Read device cellular phone numbers")
    )

    fun collectPermissionsForApps(
        scanId: Long,
        snapshots: List<AppSnapshotEntity>
    ): List<PermissionStatEntity> {
        val pm = context.packageManager
        val results = mutableListOf<PermissionStatEntity>()

        for (snapshot in snapshots) {
            try {
                val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(
                        snapshot.packageName,
                        PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())
                    )
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(snapshot.packageName, PackageManager.GET_PERMISSIONS)
                }

                val requestedPermissions = pkgInfo.requestedPermissions ?: emptyArray()
                val requestedFlags = pkgInfo.requestedPermissionsFlags ?: IntArray(0)

                for (i in requestedPermissions.indices) {
                    val permName = requestedPermissions[i]
                    val isGranted = if (i < requestedFlags.size) {
                        (requestedFlags[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
                    } else {
                        false
                    }

                    val detail = permissionMap[permName]
                    if (detail != null) {
                        val plausibility = PermissionPlausibilityRules.checkPlausibility(
                            snapshot.category,
                            detail.categoryName
                        )

                        results.add(
                            PermissionStatEntity(
                                packageName = snapshot.packageName,
                                scanId = scanId,
                                permission = permName,
                                requested = true,
                                granted = isGranted,
                                isSensitive = detail.isSensitive,
                                categoryName = detail.categoryName,
                                plainExplanation = detail.plainExplanation,
                                isUnusual = plausibility.isUnusual
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                // Graceful fallback for single package error
            }
        }
        return results
    }
}
