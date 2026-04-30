package com.conamobile.romchi.platform

import java.util.UUID
import javax.swing.JOptionPane

actual val platform: Platform = Platform.Android

actual fun deviceInfo(): String {
    return "Desktop, ${System.getProperty("os.arch")}, ${System.getProperty("os.version")}"
}

actual fun randomUUID() = UUID.randomUUID().toString()

actual fun toast(message: String?) {
    JOptionPane.showMessageDialog(
        null,
        message ?: "Unknown error", "Information",
        JOptionPane.INFORMATION_MESSAGE
    )
}