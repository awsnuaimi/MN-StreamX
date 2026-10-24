package com.yourapp.iptv.domain.caption

import javax.inject.Inject
import javax.inject.Singleton

/** A process-local fence: never delete models while caption inference or downloads own them. */
@Singleton
class CaptionStorageCoordinator @Inject constructor() {
    private var sessions = 0
    private var maintenance = false

    @Synchronized fun beginSession(): Boolean {
        if (maintenance) return false
        sessions++
        return true
    }

    @Synchronized fun endSession() {
        check(sessions > 0) { "Unbalanced caption session" }
        sessions--
    }

    @Synchronized fun beginMaintenance(): Boolean {
        if (maintenance || sessions != 0) return false
        maintenance = true
        return true
    }

    @Synchronized fun endMaintenance() {
        check(maintenance) { "Maintenance is not active" }
        maintenance = false
    }
}