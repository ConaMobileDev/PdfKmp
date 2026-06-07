package com.conamobile.pdfkmp.render

import com.conamobile.pdfkmp.geometry.PageSize
import com.conamobile.pdfkmp.metadata.PdfEncryption
import com.conamobile.pdfkmp.metadata.PdfMetadata
import com.conamobile.pdfkmp.style.PdfFont
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readBytes
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreGraphics.CGPDFContextSetOutline
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.kCGPDFContextAllowsCopying
import platform.CoreGraphics.kCGPDFContextAllowsPrinting
import platform.CoreGraphics.kCGPDFContextAuthor
import platform.CoreGraphics.kCGPDFContextCreator
import platform.CoreGraphics.kCGPDFContextKeywords
import platform.CoreGraphics.kCGPDFContextOwnerPassword
import platform.CoreGraphics.kCGPDFContextSubject
import platform.CoreGraphics.kCGPDFContextTitle
import platform.CoreGraphics.kCGPDFContextUserPassword
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSMutableArray
import platform.Foundation.NSMutableData
import platform.Foundation.NSMutableDictionary
import platform.Foundation.NSNumber
import platform.Foundation.setValue
import platform.UIKit.UIGraphicsBeginPDFContextToData
import platform.UIKit.UIGraphicsBeginPDFPageWithInfo
import platform.UIKit.UIGraphicsEndPDFContext
import platform.UIKit.UIGraphicsGetCurrentContext

/**
 * [PdfDriver] backed by `UIGraphicsPDFRenderer`-style APIs.
 *
 * `UIGraphicsBeginPDFContextToData` writes a vector PDF: every Core Graphics
 * call made through the underlying [PdfCanvas] is recorded as a vector
 * operation, so text and shapes stay sharp at any zoom level — exactly the
 * behaviour required by PdfKmp's design rules.
 *
 * Custom and bundled fonts are pre-registered through [IosFontRegistry] so
 * they're queryable by name from the moment the first canvas is requested.
 *
 * The driver is single-use: pair every [beginPage] with an [endPage] and
 * call [finish] exactly once.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal class IosPdfDriver(
    private val metadata: PdfMetadata,
    customFonts: List<PdfFont.Custom>,
) : PdfDriver {

    private val fonts = IosFontRegistry()
    private val backingData = NSMutableData()
    private val metricsImpl = IosFontMetrics(fonts)
    private val navigation = IosNavigation()
    private var open: Boolean = true
    private var pageOpen: Boolean = false

    init {
        fonts.preregister(customFonts)
        UIGraphicsBeginPDFContextToData(
            data = backingData,
            bounds = CGRectMake(0.0, 0.0, 0.0, 0.0),
            documentInfo = buildDocumentInfo(metadata),
        )
    }

    override val fontMetrics: FontMetrics get() = metricsImpl

    override fun beginPage(size: PageSize): PdfCanvas {
        check(open) { "Driver has been finished" }
        check(!pageOpen) { "endPage() must be called before beginPage()" }
        UIGraphicsBeginPDFPageWithInfo(
            bounds = CGRectMake(0.0, 0.0, size.width.value.toDouble(), size.height.value.toDouble()),
            pageInfo = null,
        )
        navigation.currentPage++
        pageOpen = true
        val ctx = UIGraphicsGetCurrentContext()
            ?: error("UIGraphicsGetCurrentContext returned null inside an open PDF page")
        return IosPdfCanvas(ctx, fonts, navigation)
    }

    override fun endPage() {
        check(pageOpen) { "endPage() called without a matching beginPage()" }
        // UIGraphicsBeginPDFPageWithInfo opens a new implicit page on the next
        // call; there's no explicit endPage in UIKit. The flag exists for
        // lifecycle assertions only.
        pageOpen = false
    }

    override fun finish(): ByteArray {
        check(open) { "Driver already finished" }
        check(!pageOpen) { "endPage() must be called before finish()" }
        attachOutline()
        UIGraphicsEndPDFContext()
        open = false
        try {
            return backingData.toByteArray()
        } finally {
            fonts.cleanup()
        }
    }

    /**
     * Writes the collected bookmarks into the PDF context's outline before
     * the context closes. Entries reference their 1-based page number —
     * the only `Destination` form Core Graphics documents for
     * `CGPDFContextSetOutline` (a named-destination string makes the child
     * node creation fail, and Core Graphics then crashes setting a nil
     * `/First`).
     */
    private fun attachOutline() {
        if (navigation.bookmarks.isEmpty()) return
        val ctx = UIGraphicsGetCurrentContext() ?: return
        val outline = buildOutlineDictionary()
        val cfOutline = CFBridgingRetain(outline) as CFDictionaryRef?
        try {
            CGPDFContextSetOutline(ctx, cfOutline)
        } finally {
            cfOutline?.let { CFRelease(it) }
        }
    }

    /**
     * Builds the `{"Children": [{Title, Destination, Children}, …]}`
     * dictionary `CGPDFContextSetOutline` expects, nesting entries by
     * level the same way markdown headings build a tree.
     */
    private fun buildOutlineDictionary(): NSMutableDictionary {
        val root = NSMutableDictionary()
        val rootChildren = NSMutableArray()
        root.setValue(rootChildren, forKey = "Children")

        // (level, entry-dictionary) — entries nest under the most recent
        // entry with a smaller level. A "Children" array is attached to a
        // parent only once it actually gains a child: Core Graphics treats
        // an empty "Children" as "has a first child", finds none, and
        // crashes inserting nil under /First.
        val stack = ArrayDeque<Pair<Int, NSMutableDictionary>>()
        for (bookmark in navigation.bookmarks) {
            val entry = NSMutableDictionary()
            entry.setValue(bookmark.title, forKey = "Title")
            entry.setValue(NSNumber(int = bookmark.page), forKey = "Destination")

            while (stack.isNotEmpty() && stack.last().first >= bookmark.level) {
                stack.removeLast()
            }
            val parentChildren = stack.lastOrNull()?.second?.let { parent ->
                parent.objectForKey("Children") as? NSMutableArray
                    ?: NSMutableArray().also { parent.setValue(it, forKey = "Children") }
            } ?: rootChildren
            parentChildren.addObject(entry)
            stack.addLast(bookmark.level to entry)
        }
        return root
    }

    private fun buildDocumentInfo(metadata: PdfMetadata): Map<Any?, Any>? {
        val attributes = mutableMapOf<Any?, Any>()
        metadata.title?.let { attributes[kCGPDFContextTitle] = it }
        metadata.author?.let { attributes[kCGPDFContextAuthor] = it }
        metadata.subject?.let { attributes[kCGPDFContextSubject] = it }
        metadata.keywords?.let { attributes[kCGPDFContextKeywords] = it }
        metadata.creator?.let { attributes[kCGPDFContextCreator] = it }
        metadata.encryption?.let { applyEncryption(it, attributes) }
        return attributes.takeIf { it.isNotEmpty() }
    }

    /**
     * Folds [PdfEncryption] into the Core Graphics document-info dictionary.
     *
     * `UIGraphicsBeginPDFContextToData` encrypts when it sees the owner/user
     * password keys and reads the printing/copying permissions from the
     * matching boolean keys. There is no Core Graphics flag for "allow
     * modification", so [PdfEncryption.allowModification] has no effect here —
     * see the KDoc on [PdfEncryption].
     *
     * NOTE: iOS cannot be compiled on the Windows host this was authored on;
     * verify on macOS that the produced document is encrypted and that the
     * permission flags round-trip.
     */
    private fun applyEncryption(
        encryption: PdfEncryption,
        attributes: MutableMap<Any?, Any>,
    ) {
        attributes[kCGPDFContextOwnerPassword] = encryption.ownerPassword
        if (encryption.userPassword.isNotEmpty()) {
            attributes[kCGPDFContextUserPassword] = encryption.userPassword
        }
        // Core Graphics expects CFBoolean-compatible values for the permission
        // keys; NSNumber bridges Kotlin's Boolean to the required object type.
        attributes[kCGPDFContextAllowsPrinting] = NSNumber(bool = encryption.allowPrinting)
        attributes[kCGPDFContextAllowsCopying] = NSNumber(bool = encryption.allowCopying)
    }
}

/**
 * Collects the bookmarks registered while pages render so
 * [IosPdfDriver.finish] can attach them as the document outline. Internal
 * links and user-defined anchors need no collection on iOS — UIKit's
 * named-destination APIs handle forward references natively.
 */
internal class IosNavigation {

    internal data class Bookmark(
        val title: String,
        val level: Int,
        /** 1-based number of the page the bookmark was registered on. */
        val page: Int,
    )

    val bookmarks: MutableList<Bookmark> = mutableListOf()

    /** 1-based index of the page currently being rendered; 0 before the first page. */
    var currentPage: Int = 0
}

/**
 * Copies an [NSMutableData] into a Kotlin [ByteArray]. Returns an empty array
 * if the underlying buffer is empty.
 */
@OptIn(ExperimentalForeignApi::class)
private fun NSMutableData.toByteArray(): ByteArray {
    val length = this.length.toInt()
    if (length == 0) return ByteArray(0)
    val pointer = this.bytes ?: return ByteArray(0)
    return pointer.readBytes(length)
}
