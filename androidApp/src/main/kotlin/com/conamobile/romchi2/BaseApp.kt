package com.conamobile.romchi2

import android.app.Application
import com.conamobile.romchi.di.initKoin
import com.conamobile.romchi2.notification.NotificationHelper
import com.famas.kmp_device_info.DeviceInfo
import com.onesignal.OneSignal
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext

class BaseApp : Application() {
    companion object {
        lateinit var instance: BaseApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        Napier.base(DebugAntilog())

        initKoin {
            androidContext(this@BaseApp)
        }
        installOneSignal()
        NotificationHelper.createNotificationChannel(this)
        DeviceInfo.initialize(this)
    }

    private fun installOneSignal() {
        OneSignal.initWithContext(this, "1aa5b45f-515f-470b-a2dc-b1efefe97045")
        CoroutineScope(Dispatchers.IO).launch {
            OneSignal.Notifications.requestPermission(true)
        }
    }
}