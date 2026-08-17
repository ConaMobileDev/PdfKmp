package com.conamobile.pdfkmp.render

import android.graphics.Typeface
import android.os.ParcelFileDescriptor
import android.os.Process
import com.conamobile.pdfkmp.PdfLog
import com.conamobile.pdfkmp.font.ResolvedFont
import com.conamobile.pdfkmp.font.resolveFont
import com.conamobile.pdfkmp.style.FontStyle
import com.conamobile.pdfkmp.style.FontWeight
import com.conamobile.pdfkmp.style.PdfFont
import com.conamobile.pdfkmp.style.TextStyle
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * Per-document cache that maps a [com.conamobile.pdfkmp.font.ResolvedFont]
 * onto the [Typeface] used by Android's drawing primitives.
 *
 * The cache writes any byte-backed font to a temporary file under
 * [cacheDir] and constructs a [Typeface] from that file via
 * [Typeface.Builder]. Bundled-font byte arrays are large (~400 KB each), so
 * caching matters: every call to [typefaceFor] short-circuits to the cached
 * value once the font has been registered.
 *
 * Temporary files persist for the lifetime of the JVM process unless
 * [cleanup] is called. The driver invokes [cleanup] from
 * [com.conamobile.pdfkmp.render.PdfDriver.finish] and, on the renderer's
 * abort path, from [com.conamobile.pdfkmp.render.PdfDriver.close].
 */
internal class AndroidFontRegistry(private val cacheDir: File) {

    private val typefaces = mutableMapOf<String, Typeface>()
    private val tempFiles = mutableListOf<File>()
    private val instanceId = nextInstanceId.getAndIncrement()

    init {
        if (!cacheDir.exists()) cacheDir.mkdirs()
    }

    /**
     * Returns the [Typeface] for the given style, registering its bytes with
     * Android on first use.
     *
     * The returned typeface honours [TextStyle.fontWeight] and
     * [TextStyle.fontStyle] — the bundled Inter set is split across four
     * files, so the resolved font already encodes the right weight/style and
     * no additional Paint flags are required.
     */
    fun typefaceFor(style: TextStyle): Typeface {
        val resolved = resolveFont(style.font, style.fontWeight, style.fontStyle)
        return cached(resolved)
    }

    /** Eagerly registers every custom font referenced by the document. */
    fun preregister(customFonts: List<PdfFont.Custom>) {
        for (font in customFonts) {
            // Register both upright and italic at the user-supplied weight so
            // that `TextStyle(font = customFont, italic = true)` resolves
            // through synthesis on Android even before the user's first draw.
            cached(resolveFont(font, FontWeight.Normal, FontStyle.Normal))
        }
    }

    /** Removes any temporary files created by this registry. */
    fun cleanup() {
        for (file in tempFiles) file.delete()
        tempFiles.clear()
        typefaces.clear()
    }

    private fun cached(resolved: ResolvedFont): Typeface {
        typefaces[resolved.name]?.let { return it }
        val typeface = createTypeface(resolved)
        typefaces[resolved.name] = typeface
        return typeface
    }

    private fun createTypeface(resolved: ResolvedFont): Typeface {
        val bytes = resolved.bytes
            ?: return resolveSystemTypeface(resolved.name)

        val safeName = buildString(resolved.name.length) {
            for (c in resolved.name) append(if (c.isLetterOrDigit() || c == '-' || c == '_') c else '_')
        }
        // The name must be unique per created file, not merely per registry:
        // the sanitized name is lossy ("My Font" and "My.Font" collide), the
        // instance counter restarts per process, and cacheDir is shared
        // across an app's processes — Typeface memory-maps the file, so any
        // second writer corrupts glyphs under a live mapping. pid + instance
        // + per-file sequence make the path unique on all three axes.
        val tempFile = File(cacheDir, "pdfkmp-$processId-$instanceId-${tempFiles.size}-$safeName.ttf")
        var failure: Exception? = null
        val typeface = try {
            tempFile.writeBytes(bytes)
            ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
                .use { pfd ->
                    Typeface.Builder(pfd.fileDescriptor).build()
                }
        } catch (e: Exception) {
            // Two very different failures land here — a full cache partition
            // (IOException from writeBytes) and a malformed font (Builder).
            // Reporting them identically sends the reader hunting in the wrong
            // place, so keep the cause.
            failure = e
            null
        }
        if (typeface == null) {
            tempFile.delete()
            val cause = failure?.let { ": ${it::class.simpleName}: ${it.message ?: "no message"}" }
                ?: " (Typeface.Builder returned null)"
            PdfLog.warn(
                "Custom font '${resolved.name}' could not be loaded; falling back to the default " +
                    "typeface (Android backend)$cause",
            )
            return Typeface.DEFAULT
        }
        tempFiles += tempFile
        return typeface
    }

    private companion object {
        private val nextInstanceId = AtomicInteger()
        private val processId = Process.myPid()
    }

    /**
     * Resolves a comma-separated list of candidate names — used for the
     * cross-platform i18n font references like
     * [com.conamobile.pdfkmp.style.PdfFont.SystemCJK] — by trying each in
     * order. `Typeface.create` silently returns [Typeface.DEFAULT] when
     * the name is unknown, so a candidate is considered "found" only if
     * the result is not the default sans typeface.
     */
    private fun resolveSystemTypeface(name: String): Typeface {
        val candidates = name.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (candidates.isEmpty()) return Typeface.DEFAULT
        for (candidate in candidates) {
            val tf = Typeface.create(candidate, Typeface.NORMAL)
            if (tf != null && tf !== Typeface.DEFAULT) return tf
        }
        // Last resort: return Android's default. The first candidate's
        // name is preserved through the registry cache so subsequent
        // lookups don't re-iterate.
        return Typeface.create(candidates.first(), Typeface.NORMAL) ?: Typeface.DEFAULT
    }
}
