package com.conamobile.romchi.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import platform.Foundation.NSURL
import platform.UIKit.UIApplication

@Composable
actual fun OpenPhoneDialer(number: String) {
    LaunchedEffect(number) {
        val url = NSURL.URLWithString(number) ?: return@LaunchedEffect
        UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any>()) { _ -> }
    }
}
