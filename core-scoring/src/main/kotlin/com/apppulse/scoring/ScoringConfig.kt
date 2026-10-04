package com.apppulse.scoring

/**
 * Tunable thresholds and weights configuration for AppPulse scoring engine.
 * As per PRD Section 8: Keep them in one configuration object so they can be tuned
 * without touching logic.
 */
object ScoringConfig {
    // Current Android platform API level for maintenance SDK lag calculation
    var CURRENT_PLATFORM_API_LEVEL: Int = 35

    // Health Score dimension weights (normal)
    var WEIGHT_SECURITY: Double = 0.30
    var WEIGHT_STORAGE: Double = 0.20
    var WEIGHT_USAGE: Double = 0.15
    var WEIGHT_STABILITY: Double = 0.20
    var WEIGHT_MAINTENANCE: Double = 0.15

    // Impact Score component weights
    var IMPACT_WEIGHT_STORAGE: Double = 30.0
    var IMPACT_WEIGHT_SECURITY: Double = 30.0
    var IMPACT_WEIGHT_STABILITY: Double = 20.0
    var IMPACT_WEIGHT_DORMANCY: Double = 20.0

    // Requested-but-not-granted penalty multiplier
    var UNGRANTED_PENALTY_FACTOR: Double = 0.25

    // Configuration indicator deductions
    var PENALTY_DEBUGGABLE: Double = 25.0
    var PENALTY_CLEARTEXT: Double = 10.0
    var PENALTY_BACKUP: Double = 5.0
    var PENALTY_PER_UNPROTECTED_EXPORTED: Double = 5.0
    var MAX_PENALTY_UNPROTECTED_EXPORTED: Double = 20.0

    // Stability deduction constants
    var STABILITY_CRASH_FACTOR: Double = 10.0
    var STABILITY_ANR_FACTOR: Double = 15.0
    var STABILITY_KILL_FACTOR: Double = 5.0

    // Maintenance update age penalty
    var OUTDATED_UPDATE_DAYS: Int = 365
    var OUTDATED_UPDATE_PENALTY: Double = 10.0

    // MB constants
    val ONE_MB_BYTES: Long = 1024L * 1024L
    val ONE_GB_BYTES: Long = 1024L * 1024L * 1024L
}
