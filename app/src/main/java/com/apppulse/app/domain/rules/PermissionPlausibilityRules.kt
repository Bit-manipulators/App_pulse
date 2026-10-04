package com.apppulse.app.domain.rules

import android.content.pm.ApplicationInfo

object PermissionPlausibilityRules {

    data class PlausibilityResult(
        val isUnusual: Boolean,
        val reason: String? = null
    )

    fun checkPlausibility(category: Int, categoryName: String): PlausibilityResult {
        return when (category) {
            ApplicationInfo.CATEGORY_GAME -> {
                when (categoryName) {
                    "SMS", "Contacts", "Call Log", "Phone State" ->
                        PlausibilityResult(true, "Games rarely require access to communications or personal contacts.")
                    "Location" ->
                        PlausibilityResult(true, "Games typically do not require background or fine location data.")
                    else -> PlausibilityResult(false)
                }
            }
            ApplicationInfo.CATEGORY_AUDIO -> {
                when (categoryName) {
                    "SMS", "Contacts", "Call Log", "Camera" ->
                        PlausibilityResult(true, "Audio and music playback apps rarely need contacts, SMS, or camera access.")
                    else -> PlausibilityResult(false)
                }
            }
            ApplicationInfo.CATEGORY_IMAGE -> {
                when (categoryName) {
                    "SMS", "Call Log", "Contacts" ->
                        PlausibilityResult(true, "Photo viewing apps do not typically require communication or contact privileges.")
                    else -> PlausibilityResult(false)
                }
            }
            ApplicationInfo.CATEGORY_NEWS -> {
                when (categoryName) {
                    "SMS", "Call Log", "Contacts", "Camera" ->
                        PlausibilityResult(true, "News reading applications rarely necessitate contact, SMS, or camera permissions.")
                    else -> PlausibilityResult(false)
                }
            }
            ApplicationInfo.CATEGORY_VIDEO -> {
                when (categoryName) {
                    "SMS", "Call Log", "Contacts" ->
                        PlausibilityResult(true, "Video playback applications do not typically require communication permissions.")
                    else -> PlausibilityResult(false)
                }
            }
            else -> {
                // When category is undefined or ambiguous, stay neutral as required by PRD FR-09
                PlausibilityResult(false)
            }
        }
    }
}
