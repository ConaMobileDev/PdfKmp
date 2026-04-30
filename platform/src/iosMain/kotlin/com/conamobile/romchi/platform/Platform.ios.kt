package com.conamobile.romchi.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreFoundation.CFUUIDCreate
import platform.CoreFoundation.CFUUIDCreateString
import platform.Foundation.CFBridgingRelease
import platform.UIKit.UIAlertAction
import platform.UIKit.UIAlertActionStyleDefault
import platform.UIKit.UIAlertController
import platform.UIKit.UIAlertControllerStyleAlert
import platform.UIKit.UIApplication
import platform.UIKit.UIDevice
import platform.UIKit.UIWindow

actual val platform = Platform.IOS

actual fun deviceInfo(): String {
    return "${UIDevice.currentDevice.systemName}, ${UIDevice.currentDevice.model}, ${UIDevice.currentDevice.systemVersion}"
}

@OptIn(ExperimentalForeignApi::class)
actual fun randomUUID(): String =
    CFBridgingRelease(CFUUIDCreateString(null, CFUUIDCreate(null))) as String

actual fun toast(message: String?) {
    val window = UIApplication.sharedApplication.windows.last() as? UIWindow
    val currentViewController = window?.rootViewController
    val alert = UIAlertController.alertControllerWithTitle(
        title = null,
        message = message,
        preferredStyle = UIAlertControllerStyleAlert
    )
    alert.addAction(
        UIAlertAction.actionWithTitle(
            title = "OK",
            style = UIAlertActionStyleDefault,
            handler = null
        )
    )
    currentViewController?.presentViewController(
        viewControllerToPresent = alert,
        animated = true,
        completion = null,
    )
}