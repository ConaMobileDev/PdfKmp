@file:OptIn(ExperimentalForeignApi::class)

package com.conamobile.pdfkmp.viewer

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.posix.memcpy

/**
 * Resolves common iOS URI shapes into a `ByteArray`:
 *
 * - `file://...`     → `NSData(contentsOfURL:)`
 * - `http(s)://...`  → blocking `NSData(contentsOfURL:)` over the network
 * - `bundle:///path` → `NSBundle.mainBundle.URLForResource:withExtension:`
 * - `/path/...`      → treated as an absolute filesystem path
 */
internal actual suspend fun loadPdfBytesFromUri(uri: String): ByteArray =
    withContext(Dispatchers.Default) {
        val resolvedUrl = resolve(uri)
            ?: error("Unable to resolve URI: $uri")
        val data: NSData = NSData.dataWithContentsOfURL(resolvedUrl)
            ?: error("NSData.dataWithContentsOfURL returned null for $uri")
        nsDataToByteArray(data)
    }

private fun resolve(uri: String): NSURL? = when {
    uri.startsWith("bundle:///") -> {
        val resourcePath = uri.removePrefix("bundle:///")
        val (name, ext) = resourcePath.split('.', limit = 2)
            .let { it.first() to it.getOrNull(1) }
        NSBundle.mainBundle.URLForResource(name, withExtension = ext)
    }
    uri.startsWith("file://") || uri.startsWith("http://") || uri.startsWith("https://") ->
        NSURL.URLWithString(uri)
    uri.startsWith("/") ->
        NSURL.fileURLWithPath(uri)
    else -> NSURL.URLWithString(uri)
}

private fun nsDataToByteArray(data: NSData): ByteArray {
    val length = data.length.toInt()
    if (length == 0) return ByteArray(0)
    val bytes = ByteArray(length)
    bytes.usePinned { pinned ->
        memcpy(pinned.addressOf(0), data.bytes, data.length)
    }
    return bytes
}
