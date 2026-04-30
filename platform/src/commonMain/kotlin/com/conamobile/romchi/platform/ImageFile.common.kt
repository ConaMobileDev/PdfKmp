package com.conamobile.romchi.platform

import androidx.compose.ui.graphics.ImageBitmap

expect suspend fun ImageBitmap.toByteArray(): ByteArray?
expect suspend fun readBytesFromUri(filePath: String): ByteArray?