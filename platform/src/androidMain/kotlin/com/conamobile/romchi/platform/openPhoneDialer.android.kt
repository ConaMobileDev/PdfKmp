package com.conamobile.romchi.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler

@Composable
actual fun OpenPhoneDialer(number: String) {
    val urlController = LocalUriHandler.current
    urlController.openUri(number)
}