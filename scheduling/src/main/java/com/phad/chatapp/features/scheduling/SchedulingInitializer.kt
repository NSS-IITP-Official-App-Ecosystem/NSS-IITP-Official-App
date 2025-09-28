package com.phad.chatapp.features.scheduling

/**
 * Initializer for the scheduling module.
 * Unlike the original TTWApplication, this doesn't initialize Firebase
 * since that's already handled by the main application.
 */
object SchedulingInitializer {
    /**
     * Initialize the scheduling module.
     * This should be called from the main application during startup.
     */
    fun initialize() {
        // Any module-specific initialization can go here
        // No need to initialize Firebase as it's already done in the main app
    }
} 