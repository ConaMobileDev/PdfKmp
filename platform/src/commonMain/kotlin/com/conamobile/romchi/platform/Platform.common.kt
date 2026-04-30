package com.conamobile.romchi.platform

enum class Platform(
    val version: Int,
    val url: String,
) {
    Android(
        version = AppConstants.APP_VERSION,
        url = AppConstants.ANDROID_APP_URL,
    ),
    IOS(
        version = AppConstants.APP_VERSION,
        url = AppConstants.IOS_APP_URL,
    ),
    Desktop(
        version = AppConstants.APP_VERSION,
        url = AppConstants.DESKTOP_APP_URL,
    )
}

expect val platform: Platform

expect fun deviceInfo(): String

expect fun randomUUID(): String

expect fun toast(message: String?)