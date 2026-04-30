package com.conamobile.romchi.platform

import com.conamobile.romchi.data.model.type.ConnectionQualityType

actual class ProviderFactory actual constructor() {
    actual fun turnOffDozeMode(callback: () -> Unit) {
    }

    actual fun connectionQuality(): ConnectionQualityType {
        return ConnectionQualityType.Good
    }

    actual fun batteryLevel(): Int {
        return 100
    }

    actual fun gpsEnabled(): Boolean {
        return false
    }

    actual fun dozeModeTurnedOff(): Boolean {
        return true
    }

}