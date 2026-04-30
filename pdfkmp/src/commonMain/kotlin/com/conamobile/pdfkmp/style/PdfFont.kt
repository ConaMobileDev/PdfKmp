package com.conamobile.pdfkmp.style

/**
 * A resolvable font. Three flavours are supported:
 *
 * - [Default] — the platform's default sans-serif font. Always available, no
 *   registration needed. Used when no font is specified anywhere.
 * - [System] — a font installed on the device by name (e.g. `"Helvetica"`,
 *   `"Times New Roman"`). If the name doesn't resolve, the renderer falls back
 *   to [Default].
 * - [Custom] — a font supplied as raw bytes (TTF/OTF). Registered with the
 *   platform font manager once per document; can be used by [name] thereafter.
 *
 * Two [PdfFont] instances compare equal iff they refer to the same source —
 * see each subtype's [equals] for details.
 */
public sealed interface PdfFont {

    /** Stable identifier used for caching the resolved platform font. */
    public val key: String

    /**
     * Platform-default font. Never registered explicitly; the renderer maps
     * this to whatever the OS uses for sans-serif (Roboto on Android,
     * Helvetica on iOS).
     */
    public data object Default : PdfFont {
        override val key: String = "default"
    }

    /**
     * A font referenced by name. The name is looked up against the platform
     * font registry at render time. Falls back to [Default] when missing.
     *
     * For scripts that ship under different system font names on each
     * platform (e.g. CJK, Arabic), pass a comma-separated [name] like
     * `"Noto Sans CJK SC, PingFang SC"` — the renderer tries each name
     * in order and uses the first one that resolves.
     */
    public data class System(val name: String) : PdfFont {
        override val key: String = "system:$name"

        /** Names this reference is allowed to resolve to, in priority order. */
        public val candidates: List<String> get() = name.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    }

    /**
     * A font supplied as TTF/OTF bytes. The [name] is used to register the
     * font with the platform on first use and to look it up on subsequent
     * draws — pick a unique name per font face.
     */
    public class Custom(
        public val name: String,
        public val bytes: ByteArray,
    ) : PdfFont {
        override val key: String = "custom:$name"

        override fun equals(other: Any?): Boolean =
            other is Custom && other.name == name && other.bytes.contentEquals(bytes)

        override fun hashCode(): Int = 31 * name.hashCode() + bytes.contentHashCode()

        override fun toString(): String = "Custom(name='$name', bytes=${bytes.size})"
    }

    /**
     * Pre-set [PdfFont.System] references for the writing systems whose
     * native shaping needs differ from Latin (CJK ideographs, Arabic
     * cursive joining, Persian variants). Pass any of these to
     * [com.conamobile.pdfkmp.style.TextStyle.font] when authoring text in
     * the matching script.
     *
     * Each constant is a *resolution recipe*, not bundled bytes — the
     * Inter font shipped with PdfKmp cannot represent any of these
     * scripts on its own, and bundling a multi-megabyte CJK or Arabic
     * font would balloon the library size. Each helper points the
     * platform font registry at a name that ships in iOS and Android
     * itself, so the right glyphs render without the user having to
     * supply [Custom] bytes:
     *
     * - [SystemCJK] — Noto Sans CJK (Android) / PingFang SC (iOS).
     * - [SystemArabic] — Noto Sans Arabic / Geeza Pro.
     * - [SystemPersian] — falls back through Tahoma → Noto Naskh Arabic.
     *
     * If none of the platform fallbacks resolve on a given device, the
     * renderer drops back to [Default] (Inter), which will tofu-render
     * non-Latin glyphs. In that situation register a [Custom] font with
     * the right script coverage.
     */
    public companion object {
        /** Sans-serif CJK font present on both Android and iOS. */
        public val SystemCJK: System = System("Noto Sans CJK SC, PingFang SC")

        /** Sans-serif Arabic font with the joining tables built in. */
        public val SystemArabic: System = System("Noto Sans Arabic, Geeza Pro")

        /**
         * Persian-leaning Arabic-script font. Persian has a few glyph
         * variants (e.g. yeh, kaf) that differ from standard Arabic;
         * Tahoma is the de-facto default on iOS for these.
         */
        public val SystemPersian: System = System("Noto Naskh Arabic, Tahoma, Geeza Pro")
    }
}
