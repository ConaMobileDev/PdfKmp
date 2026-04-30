package com.conamobile.romchi.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import org.jetbrains.skia.Image

actual suspend fun ImageBitmap.toByteArray(): ByteArray? {
    return Image.makeFromBitmap(asSkiaBitmap()).encodeToData()?.bytes
}