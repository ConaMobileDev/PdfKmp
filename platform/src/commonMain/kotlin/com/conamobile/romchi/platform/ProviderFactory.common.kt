package com.conamobile.romchi.platform

import com.conamobile.romchi.data.model.type.ConnectionQualityType

expect class ProviderFactory() {

    fun turnOffDozeMode(callback: () -> Unit)

    fun connectionQuality(): ConnectionQualityType

    fun batteryLevel(): Int

    fun gpsEnabled(): Boolean

    fun dozeModeTurnedOff(): Boolean
}