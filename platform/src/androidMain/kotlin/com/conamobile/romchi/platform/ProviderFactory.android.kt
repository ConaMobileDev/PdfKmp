package com.conamobile.romchi.platform

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.location.LocationManagerCompat
import com.conamobile.romchi.data.model.type.ConnectionQualityType
import com.conamobile.romchi.platform.startup.applicationContext

actual class ProviderFactory {

    private val connectivityManager: ConnectivityManager by lazy {
        applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }
    private val locationManager: LocationManager by lazy {
        applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }
    private val batteryManager: BatteryManager by lazy {
        applicationContext.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    }
    private val bluetoothManager: BluetoothManager by lazy {
        applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }
    private val powerManager: PowerManager by lazy {
        applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
    }

    @SuppressLint("BatteryLife")
    actual fun turnOffDozeMode(callback: () -> Unit) {
        try {
            if (!dozeModeTurnedOff()) {
                val intent = Intent().apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                    data = Uri.parse("package:${applicationContext.packageName}")
                }
                applicationContext.startActivity(intent)
                callback()
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    actual fun connectionQuality(): ConnectionQualityType {
        return try {
            val networkCapabilities = connectivityManager.getNetworkCapabilities(
                connectivityManager.activeNetwork
            )

            val speed = networkCapabilities?.linkDownstreamBandwidthKbps
            if (speed != null) {
                val qualityType = when {
                    speed in 0..500 -> ConnectionQualityType.Bad
                    speed in 500..2000 -> ConnectionQualityType.Good
                    speed >= 2000 -> ConnectionQualityType.Excellent
                    else -> ConnectionQualityType.Unknown
                }
                qualityType
            } else {
                ConnectionQualityType.Unknown
            }
        } catch (t: Throwable) {
            ConnectionQualityType.Unknown
        }
    }

    actual fun batteryLevel(): Int {
        return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    actual fun gpsEnabled(): Boolean {
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }


    actual fun dozeModeTurnedOff(): Boolean {
        return try {
            powerManager.isIgnoringBatteryOptimizations(applicationContext.packageName)
        } catch (t: Throwable) {
            false
        }
    }
}
