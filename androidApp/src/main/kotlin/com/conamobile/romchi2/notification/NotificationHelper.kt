package com.conamobile.romchi2.notification

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.conamobile.romchi2.R
import com.mmk.kmpnotifier.notification.NotifierManager
import com.mmk.kmpnotifier.notification.configuration.NotificationPlatformConfiguration

object NotificationHelper {
    fun createNotificationChannel(context: Context) {
        NotifierManager.initialize(
            configuration = NotificationPlatformConfiguration.Android(
                notificationIconResId = R.drawable.rom_chi_logo,
                showPushNotification = true,
                notificationChannelData = NotificationPlatformConfiguration.Android.NotificationChannelData(),
            )
        )

        val fcmChannel = NotificationChannel(
            FCM_CHANNEL_ID,
            FCM_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            setShowBadge(true)
            enableVibration(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        val manager = getNotificationManager(context)
        manager.createNotificationChannel(fcmChannel)
    }

    private const val FCM_CHANNEL_ID: String = "romchi_channel"
    private const val FCM_CHANNEL_NAME: String = "Romchi Channel"


    private fun getNotificationManager(context: Context): NotificationManager {
        return context.getSystemService(Application.NOTIFICATION_SERVICE) as NotificationManager
    }
}