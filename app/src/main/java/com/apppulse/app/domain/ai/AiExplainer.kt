package com.apppulse.app.domain.ai

import java.util.Locale
import java.util.regex.Pattern

enum class AiMode {
    OFF,
    ON_DEVICE_TEMPLATE,
    CLOUD_PROXY
}

data class ExplanationInput(
    val app: String,
    val footprintMB: Int,
    val cacheMB: Int = 0,
    val daysSinceUse: Int,
    val permissions: List<String> = emptyList(),
    val impactBand: String,
    val driver: String,
    val crashes: Int = 0,
    val anrs: Int = 0,
    val isDebuggable: Boolean = false,
    val usesCleartext: Boolean = false,
    val unprotectedComponents: Int = 0,
    val targetSdk: Int = 35,
    val hasUnusualPermissions: Boolean = false
)

object OutputValidator {

    private val BANNED_ALARMIST_WORDS = listOf(
        "malicious", "hacked", "hacker", "spyware", "virus", "trojan",
        "danger", "spying", "infected", "stolen", "breached"
    )

    /**
     * Verifies that all numbers present in the AI response were provided in the input,
     * and that no alarmist words violate the Claims Policy.
     */
    fun isValid(output: String, input: ExplanationInput): Boolean {
        val lower = output.lowercase(Locale.ROOT)
        for (word in BANNED_ALARMIST_WORDS) {
            if (lower.contains(word)) return false
        }

        // Extract all numbers from the generated response
        val numberPattern = Pattern.compile("\\b\\d+(?:\\.\\d+)?\\b")
        val matcher = numberPattern.matcher(output)

        val allowedNumbers = mutableSetOf(
            input.footprintMB.toString(),
            input.cacheMB.toString(),
            input.daysSinceUse.toString(),
            input.crashes.toString(),
            input.anrs.toString(),
            input.unprotectedComponents.toString(),
            input.targetSdk.toString(),
            // Common conversions (e.g. GB rounded)
            String.format(Locale.US, "%.1f", input.footprintMB / 1024.0),
            String.format(Locale.US, "%.0f", input.footprintMB / 1024.0),
            String.format(Locale.US, "%.1f", input.cacheMB / 1024.0),
            "1", "2", "3", "30", "60", "90"
        )

        while (matcher.find()) {
            val numStr = matcher.group()
            if (!allowedNumbers.contains(numStr)) {
                // Number not traceable to collected input fields
                return false
            }
        }

        return true
    }
}

object TemplateExplainer {

    fun generateExplanation(input: ExplanationInput): String {
        val sizeStr = formatFootprint(input.footprintMB)
        val cacheStr = formatFootprint(input.cacheMB)

        // 1. Dormant large app
        if (input.driver.equals("dormancy", ignoreCase = true) && input.daysSinceUse >= 30 && input.footprintMB >= 500) {
            return "You have not opened ${input.app} for ${input.daysSinceUse} days, yet it takes about $sizeStr. Consider removing it if you no longer need it."
        }

        // 2. Dormant small app
        if (input.daysSinceUse >= 60 && input.footprintMB < 100) {
            return "You have not used ${input.app} for ${input.daysSinceUse} days. While its storage impact is low ($sizeStr), you can review its permissions or remove it."
        }

        // 3. Security: Debuggable build
        if (input.isDebuggable) {
            return "${input.app} has a debuggable build flag enabled, which indicates an unoptimized development package with potential inspection risks."
        }

        // 4. Security: Unusual sensitive permissions
        if (input.hasUnusualPermissions && input.permissions.isNotEmpty()) {
            val permList = input.permissions.joinToString(", ")
            return "${input.app} holds sensitive permissions ($permList) that appear unusual for its app category. You may wish to review these in system settings."
        }

        // 5. Security: Cleartext HTTP traffic
        if (input.usesCleartext) {
            return "${input.app} allows cleartext HTTP communication, which does not enforce encrypted TLS network transport."
        }

        // 6. Security: Unprotected exported components
        if (input.unprotectedComponents > 0) {
            return "${input.app} exposes ${input.unprotectedComponents} background component(s) without permission requirements, which may allow interactions from other apps."
        }

        // 7. Stability: Recent crashes/ANRs
        if (input.crashes > 0 || input.anrs > 0) {
            val details = mutableListOf<String>()
            if (input.crashes > 0) details.add("${input.crashes} crash(es)")
            if (input.anrs > 0) details.add("${input.anrs} ANR(s)")
            return "${input.app} experienced abnormal exits (${details.joinToString(", ")}) over the last 30 days, indicating possible stability issues."
        }

        // 8. Storage hog with large cache
        if (input.driver.equals("storage", ignoreCase = true) && input.cacheMB >= 200) {
            return "${input.app} occupies about $sizeStr on your device, including $cacheStr in temporary cache that you can clear in storage settings."
        }

        // 9. Storage hog general
        if (input.driver.equals("storage", ignoreCase = true)) {
            return "${input.app} is one of your largest applications at $sizeStr. Check storage settings to review its stored data."
        }

        // 10. Outdated Target SDK
        if (input.targetSdk < 33) {
            return "${input.app} targets Android API ${input.targetSdk}, which lags behind current platform standards. Consider checking for an update."
        }

        // 11. Sensitive permissions general
        if (input.permissions.isNotEmpty()) {
            val permList = input.permissions.take(3).joinToString(", ")
            return "${input.app} holds access to sensitive permissions ($permList). Review permissions in settings if they are not actively required."
        }

        // 12. Default healthy / low impact
        return "${input.app} shows a healthy profile with $sizeStr of storage, recent usage, and no elevated risk indicators detected."
    }

    private fun formatFootprint(mb: Int): String {
        return if (mb >= 1024) {
            String.format(Locale.US, "%.1f GB", mb / 1024.0)
        } else {
            "$mb MB"
        }
    }
}

class AiExplainerService {

    fun explain(input: ExplanationInput, mode: AiMode): String {
        if (mode == AiMode.OFF) {
            return TemplateExplainer.generateExplanation(input)
        }

        if (mode == AiMode.ON_DEVICE_TEMPLATE) {
            return TemplateExplainer.generateExplanation(input)
        }

        // Cloud Proxy Mode: Simulated fallback or proxy client
        val candidate = TemplateExplainer.generateExplanation(input)
        return if (OutputValidator.isValid(candidate, input)) {
            candidate
        } else {
            TemplateExplainer.generateExplanation(input)
        }
    }
}
