package com.conamobile.romchi.platform

import com.conamobile.romchi.data.model.type.ConnectionQualityType
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreTelephony.CTRadioAccessTechnologyCDMA1x
import platform.CoreTelephony.CTRadioAccessTechnologyCDMAEVDORev0
import platform.CoreTelephony.CTRadioAccessTechnologyCDMAEVDORevA
import platform.CoreTelephony.CTRadioAccessTechnologyCDMAEVDORevB
import platform.CoreTelephony.CTRadioAccessTechnologyEdge
import platform.CoreTelephony.CTRadioAccessTechnologyGPRS
import platform.CoreTelephony.CTRadioAccessTechnologyHSDPA
import platform.CoreTelephony.CTRadioAccessTechnologyHSUPA
import platform.CoreTelephony.CTRadioAccessTechnologyLTE
import platform.CoreTelephony.CTRadioAccessTechnologyNR
import platform.CoreTelephony.CTRadioAccessTechnologyNRNSA
import platform.CoreTelephony.CTRadioAccessTechnologyWCDMA
import platform.CoreTelephony.CTRadioAccessTechnologyeHRPD
import platform.CoreTelephony.CTTelephonyNetworkInfo
import platform.UIKit.UIDevice
import platform.Foundation.NSURL
import platform.UIKit.UIApplication

actual class ProviderFactory {

    actual fun turnOffDozeMode(callback: () -> Unit) {
        callback()
    }

    actual fun connectionQuality(): ConnectionQualityType {
        val telephonyInfo = CTTelephonyNetworkInfo()

        return when (telephonyInfo.currentRadioAccessTechnology()) {
            CTRadioAccessTechnologyGPRS,
            CTRadioAccessTechnologyEdge,
            CTRadioAccessTechnologyCDMA1x -> ConnectionQualityType.Bad

            CTRadioAccessTechnologyWCDMA,
            CTRadioAccessTechnologyHSDPA,
            CTRadioAccessTechnologyHSUPA,
            CTRadioAccessTechnologyCDMAEVDORev0,
            CTRadioAccessTechnologyCDMAEVDORevA,
            CTRadioAccessTechnologyCDMAEVDORevB,
            CTRadioAccessTechnologyeHRPD -> ConnectionQualityType.Good

            CTRadioAccessTechnologyLTE,
            CTRadioAccessTechnologyNRNSA,
            CTRadioAccessTechnologyNR -> ConnectionQualityType.Excellent

            else -> ConnectionQualityType.Unknown
        }
    }

    actual fun batteryLevel(): Int {
        UIDevice.currentDevice().setBatteryMonitoringEnabled(true)
        val level = UIDevice.currentDevice().batteryLevel()
        return level.toInt()
    }

    actual fun gpsEnabled(): Boolean {
        return locationEnabled()
    }

    private fun locationEnabled(): Boolean {
        return when (CLLocationManager.authorizationStatus()) {
            kCLAuthorizationStatusAuthorizedAlways,
            kCLAuthorizationStatusAuthorizedWhenInUse -> true

            else -> false
        }
    }


    actual fun dozeModeTurnedOff(): Boolean {
        return true
    }
}