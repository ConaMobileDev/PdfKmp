package com.conamobile.romchi.platform

import android.os.Build
import android.widget.Toast
import com.conamobile.romchi.platform.startup.applicationContext
import java.util.UUID

actual val platform: Platform = Platform.Android

actual fun deviceInfo(): String {
    return "Android, ${Build.MANUFACTURER} ${Build.MODEL}, ${Build.VERSION.SDK_INT}"
}

actual fun randomUUID() = UUID.randomUUID().toString()

actual fun toast(message: String?) {
    Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
}