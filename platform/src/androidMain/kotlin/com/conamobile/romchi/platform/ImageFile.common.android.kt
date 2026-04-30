package com.conamobile.romchi.platform

actual suspend fun readBytesFromUri(filePath: String): ByteArray? =
    java.io.File(filePath).readBytes()
