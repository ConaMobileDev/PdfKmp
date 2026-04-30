package com.conamobile.romchi.platform.map

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSURL
import platform.UIKit.UIAlertAction
import platform.UIKit.UIAlertActionStyleCancel
import platform.UIKit.UIAlertActionStyleDefault
import platform.UIKit.UIAlertController
import platform.UIKit.UIAlertControllerStyleActionSheet
import platform.UIKit.UIApplication
import platform.UIKit.popoverPresentationController

@OptIn(ExperimentalForeignApi::class)
actual fun openInMaps(latitude: Double, longitude: Double, label: String) {
    val rootVC = UIApplication.sharedApplication
        .keyWindow
        ?.rootViewController
        ?: return

    val alert = UIAlertController.alertControllerWithTitle(
        title = "Choose navigation",
        message = null,
        preferredStyle = UIAlertControllerStyleActionSheet
    )

    // Apple Maps (always)
    alert.addAction(
        UIAlertAction.actionWithTitle("Apple Maps", UIAlertActionStyleDefault) { _ ->
            UIApplication.sharedApplication.openURL(
                NSURL.URLWithString("http://maps.apple.com/?daddr=$latitude,$longitude")!!,
                emptyMap<Any?, Any?>(),
                null
            )
        }
    )

    // Google Maps
    NSURL.URLWithString("comgooglemaps://")?.takeIf {
        UIApplication.sharedApplication.canOpenURL(it)
    }?.let {
        alert.addAction(
            UIAlertAction.actionWithTitle("Google Maps", UIAlertActionStyleDefault) { _ ->
                UIApplication.sharedApplication.openURL(
                    NSURL.URLWithString("comgooglemaps://?daddr=$latitude,$longitude&directionsmode=driving")!!,
                    emptyMap<Any?, Any?>(),
                    null
                )
            }
        )
    }

    // Yandex.Navigator
    NSURL.URLWithString("yandexnavi://")?.takeIf {
        UIApplication.sharedApplication.canOpenURL(it)
    }?.let {
        alert.addAction(
            UIAlertAction.actionWithTitle("Yandex.Navigator", UIAlertActionStyleDefault) { _ ->
                UIApplication.sharedApplication.openURL(
                    NSURL.URLWithString(
                        "yandexnavi://build_route_on_map?lat_to=$latitude&lon_to=$longitude&name=${
                            label.replace(" ", "+")
                        }"
                    )!!,
                    emptyMap<Any?, Any?>(),
                    null
                )
            }
        )
    }

    // Cancel
    alert.addAction(
        UIAlertAction.actionWithTitle("Cancel", UIAlertActionStyleCancel) { _ -> }
    )

    alert.popoverPresentationController?.sourceView = rootVC.view
    alert.popoverPresentationController?.sourceRect = rootVC.view.bounds

    rootVC.presentViewController(alert, true, null)
}


