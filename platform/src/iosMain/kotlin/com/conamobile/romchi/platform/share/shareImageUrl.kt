package com.conamobile.romchi.platform.share

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.popoverPresentationController

@OptIn(ExperimentalForeignApi::class)
actual fun shareImageUrl(url: String) {
    presentActivityController(url)
}

@OptIn(ExperimentalForeignApi::class)
actual fun downloadImageUrl(url: String, fileName: String) {
    presentActivityController(url)
}

@OptIn(ExperimentalForeignApi::class)
private fun presentActivityController(url: String) {
    val nsUrl = NSURL.URLWithString(url) ?: return
    val rootVC = UIApplication.sharedApplication
        .keyWindow
        ?.rootViewController
        ?: return

    val controller = UIActivityViewController(
        activityItems = listOf(nsUrl),
        applicationActivities = null,
    )

    controller.popoverPresentationController?.sourceView = rootVC.view
    controller.popoverPresentationController?.sourceRect = rootVC.view.bounds

    rootVC.presentViewController(controller, true, null)
}
