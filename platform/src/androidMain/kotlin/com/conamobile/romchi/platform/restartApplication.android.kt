package com.conamobile.romchi.platform

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import com.conamobile.romchi.platform.startup.applicationContext
import kotlin.system.exitProcess

@SuppressLint("NewApi")
actual fun restartApplication() {
    val context = applicationContext
    val packageManager = context.packageManager
    val intent = packageManager.getLaunchIntentForPackage(context.packageName) ?: return
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    context.startActivity(intent)
    exitProcess(0)
}
