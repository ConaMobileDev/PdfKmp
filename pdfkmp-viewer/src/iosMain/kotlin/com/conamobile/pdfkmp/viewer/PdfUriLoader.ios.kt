@file:OptIn(ExperimentalForeignApi::class)

package com.conamobile.pdfkmp.viewer

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.posix.memcpy

/**
 * iOS resolver for the async [PdfSource] variants.
 *
 * - [PdfSource.FilePath]   → `NSData(contentsOfURL:)` against a
 *   `fileURLWithPath` URL
 * - [PdfSource.Remote]     → `NSData(contentsOfURL:)` against an
 *   `http(s)://` URL. **Note:** the iOS resolver does *not* honour
 *   [PdfSource.Remote.headers] / [PdfSource.Remote.timeoutMillis] —
 *   `NSData(contentsOfURL:)` doesn't expose either knob, and the
 *   alternative (`NSURLSession.dataTaskWithRequest:completionHandler:`)
 *   isn't reliably callable through the Kotlin/Native Foundation
 *   bindings. Apps that need authenticated downloads or custom
 *   timeouts should fetch the bytes via a Swift/Obj-C helper and
 *   pass them in as [PdfSource.Bytes].
 * - [PdfSource.Asset]      → `NSBundle.mainBundle.URLForResource`
 * - [PdfSource.ContentUri] → throws, the scheme is Android-only
 *
 * All I/O happens on [Dispatchers.Default]. The viewer wraps the
 * call in its own `try` so failures surface as an inline error UI.
 */
internal actual suspend fun loadAsyncBytes(source: PdfSource): ByteArray =
    withContext(Dispatchers.Default) {
        when (source) {
            is PdfSource.FilePath -> readFile(source.path)
            is PdfSource.Remote -> readRemote(source.url)
            is PdfSource.Asset -> readAsset(source.path)
            is PdfSource.ContentUri ->
                error("content:// URIs are Android-only — use PdfSource.FilePath on iOS")
            is PdfSource.Bytes, is PdfSource.Document ->
                error("loadAsyncBytes() called on in-memory variant ${source::class.simpleName}")
        }
    }

private fun readFile(rawPath: String): ByteArray {
    val path = rawPath.removePrefix("file://")
    val url = if (path.startsWith("/")) {
        NSURL.fileURLWithPath(path)
    } else {
        NSURL.URLWithString(rawPath)
            ?: error("Could not resolve file path: $rawPath")
    }
    val data = NSData.dataWithContentsOfURL(url)
        ?: error("NSData.dataWithContentsOfURL returned null for $rawPath")
    return data.toBytes()
}

private fun readAsset(path: String): ByteArray {
    val (name, ext) = path.split('.', limit = 2)
        .let { it.first() to it.getOrNull(1) }
    val url = NSBundle.mainBundle.URLForResource(name, withExtension = ext)
        ?: error("Bundle resource not found: $path")
    val data = NSData.dataWithContentsOfURL(url)
        ?: error("NSData.dataWithContentsOfURL returned null for bundle:///$path")
    return data.toBytes()
}

private fun readRemote(rawUrl: String): ByteArray {
    val url = NSURL.URLWithString(rawUrl)
        ?: error("Could not parse URL: $rawUrl")
    val data = NSData.dataWithContentsOfURL(url)
        ?: error("NSData.dataWithContentsOfURL returned null for $rawUrl")
    return data.toBytes()
}

private fun NSData.toBytes(): ByteArray {
    val length = length.toInt()
    if (length == 0) return ByteArray(0)
    val out = ByteArray(length)
    out.usePinned { pinned ->
        memcpy(pinned.addressOf(0), bytes, this.length)
    }
    return out
}
