package com.conamobile.pdfkmp.style

import kotlin.jvm.JvmInline

/**
 * Font weight on the standard 100..900 axis. Renderers map this to the closest
 * available weight in the resolved font.
 */
@JvmInline
public value class FontWeight(public val value: Int) {
    public companion object {
        public val Thin: FontWeight = FontWeight(100)
        public val ExtraLight: FontWeight = FontWeight(200)
        public val Light: FontWeight = FontWeight(300)
        public val Normal: FontWeight = FontWeight(400)
        public val Medium: FontWeight = FontWeight(500)
        public val SemiBold: FontWeight = FontWeight(600)
        public val Bold: FontWeight = FontWeight(700)
        public val ExtraBold: FontWeight = FontWeight(800)
        public val Black: FontWeight = FontWeight(900)
    }
}

/** Italic vs normal. Renderers may synthesize italic if the font has no italic glyphs. */
public enum class FontStyle {
    Normal,
    Italic,
}
