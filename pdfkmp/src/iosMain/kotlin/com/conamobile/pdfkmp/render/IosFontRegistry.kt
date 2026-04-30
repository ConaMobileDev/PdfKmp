package com.conamobile.pdfkmp.render

import com.conamobile.pdfkmp.font.ResolvedFont
import com.conamobile.pdfkmp.font.resolveFont
import com.conamobile.pdfkmp.style.FontStyle
import com.conamobile.pdfkmp.style.FontWeight
import com.conamobile.pdfkmp.style.PdfFont
import com.conamobile.pdfkmp.style.TextStyle
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGDataProviderCreateWithData
import platform.CoreGraphics.CGFontCreateWithDataProvider
import platform.CoreGraphics.CGFontRef
import platform.CoreText.CTFontManagerRegisterGraphicsFont
import platform.CoreText.CTFontManagerUnregisterGraphicsFont
import platform.UIKit.UIFont

/**
 * Per-document cache that maps a [com.conamobile.pdfkmp.font.ResolvedFont]
 * onto the [UIFont] used by Core Graphics drawing primitives.
 *
 * Custom and bundled font bytes are wrapped in a [CGFontRef] via
 * `CGDataProviderCreateWithData` (raw bytes pointer, no [Foundation.NSData]
 * detour) and registered with [CTFontManagerRegisterGraphicsFont] so that
 * [UIFont.fontWithName] can resolve them by PostScript name. Registrations
 * are reversed in [cleanup] to avoid leaking them across documents — Core
 * Text keeps registered fonts global to the process otherwise.
 *
 * Bundled fonts use a stable mapping from the internal name `PdfKmp.Inter-*`
 * to the PostScript name baked into the Inter TTF (`Inter-*`); custom and
 * system fonts are looked up by their resolved name as-is.
 */
@OptIn(ExperimentalForeignApi::class)
internal class IosFontRegistry {

    private val registeredFonts = mutableMapOf<String, CGFontRef>()

    /** Returns the [UIFont] for [style], registering its bytes on first use. */
    fun fontFor(style: TextStyle): UIFont {
        val resolved = resolveFont(style.font, style.fontWeight, style.fontStyle)
        register(resolved)
        val size = style.fontSize.value.toDouble()
        // For system / bundled font references, support a comma-separated
        // candidate chain so cross-platform i18n helpers like
        // PdfFont.SystemCJK ("Noto Sans CJK SC, PingFang SC") resolve to
        // whichever name iOS actually has installed.
        for (candidate in resolved.postScriptName.split(',').map { it.trim() }.filter { it.isNotEmpty() }) {
            UIFont.fontWithName(candidate, size = size)?.let { return it }
        }
        return UIFont.systemFontOfSize(size)
    }

    /** Eagerly registers every custom font referenced by the document. */
    fun preregister(customFonts: List<PdfFont.Custom>) {
        for (font in customFonts) {
            register(resolveFont(font, FontWeight.Normal, FontStyle.Normal))
        }
    }

    /** Unregisters every font registered by this instance. */
    fun cleanup() {
        for ((_, ref) in registeredFonts) {
            CTFontManagerUnregisterGraphicsFont(ref, null)
        }
        registeredFonts.clear()
    }

    private fun register(resolved: ResolvedFont) {
        if (registeredFonts.containsKey(resolved.name)) return
        val bytes = resolved.bytes ?: return
        val cgFont = bytes.usePinned { pinned ->
            val provider = CGDataProviderCreateWithData(
                info = null,
                data = pinned.addressOf(0),
                size = bytes.size.toULong(),
                releaseData = null,
            ) ?: return@usePinned null
            CGFontCreateWithDataProvider(provider)
        } ?: return
        CTFontManagerRegisterGraphicsFont(cgFont, null)
        registeredFonts[resolved.name] = cgFont
    }
}

/**
 * PostScript name registered for the resolved font.
 *
 * Bundled fonts use a stable mapping from our internal name (`PdfKmp.Inter-*`)
 * to the PostScript name baked into the Inter TTF (`Inter-*`). System and
 * custom fonts use the resolved name as-is.
 */
private val ResolvedFont.postScriptName: String
    get() = when {
        name.startsWith("PdfKmp.") -> name.removePrefix("PdfKmp.")
        else -> name
    }
