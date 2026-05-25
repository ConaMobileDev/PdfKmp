package com.conamobile.pdfkmp.viewer

import com.conamobile.pdfkmp.PdfDocument
import com.conamobile.pdfkmp.text.PdfHyperlink
import com.conamobile.pdfkmp.text.PdfTextRun

/**
 * Unified, sealed description of *where* a PDF comes from.
 *
 * Earlier revisions of the library mixed two concerns: in-memory
 * payloads were modelled as `PdfSource` while remote / disk / asset
 * loads travelled as a bare `uri: String`. That string had to be
 * pattern-matched ("does it start with `https://`?"), couldn't carry
 * per-shape configuration (headers, timeouts), and made "URI" a
 * misnomer for what was in practice five different transports.
 *
 * Every variant now lives under [PdfSource], split along two axes:
 *
 * - **In-memory** — [Bytes], [Document]. The bytes are already in
 *   hand; the viewer renders synchronously.
 * - **Async** — [FilePath], [Remote], [ContentUri], [Asset]. The
 *   viewer fetches the bytes on a background dispatcher, showing a
 *   loading state until they arrive.
 *
 * Use the matching constructor directly when you know the shape
 * (e.g. `PdfSource.Remote("https://…", headers = mapOf(…))`), or
 * fall back to [auto] when you only have an opaque string.
 *
 * [PdfDocument] payloads stay special-cased because the viewer can
 * overlay an invisible, selectable text layer on top of each
 * rasterised page — that overlay needs the captured [PdfTextRun]s
 * and [PdfHyperlink]s, which only the PdfKmp DSL produces.
 */
public sealed interface PdfSource {

    /**
     * Opaque PDF payload — bytes only, no associated text-position
     * data. Yields a viewer with rasterised pages but no selectable
     * text layer.
     *
     * @property bytes raw `%PDF-…` bytes. The viewer keeps a reference
     *   for the lifetime of the composition; do not mutate the array
     *   after passing it in.
     */
    public class Bytes(public val bytes: ByteArray) : PdfSource

    /**
     * PDF payload paired with the laid-out text runs and hyperlink
     * annotations the renderer produced. Lets [PdfViewer] mount a
     * transparent, selectable text overlay on each page and attach a
     * clickable layer for hyperlink targets.
     *
     * @property bytes raw `%PDF-…` bytes — same shape as [Bytes].
     * @property textRuns every text run captured during rendering,
     *   indexed by page via [PdfTextRun.pageIndex].
     * @property hyperlinks every hyperlink annotation captured during
     *   rendering, indexed by page via [PdfHyperlink.pageIndex].
     */
    public class Document(
        public val bytes: ByteArray,
        public val textRuns: List<PdfTextRun>,
        public val hyperlinks: List<PdfHyperlink>,
    ) : PdfSource

    /**
     * PDF stored on the local filesystem. Accepts bare absolute paths
     * (`/storage/emulated/0/…`) and `file://` URLs alike — both forms
     * resolve through the platform's standard file I/O.
     *
     * @property path absolute path. `file://` prefix is stripped
     *   automatically by the resolver.
     */
    public class FilePath(public val path: String) : PdfSource

    /**
     * PDF fetched from an HTTP(S) endpoint. Bytes are downloaded in
     * full to a transient buffer before rendering starts — this
     * implementation is not streaming. Apps that need progress
     * reporting or streaming should fetch the bytes themselves and
     * hand them in as [Bytes].
     *
     * @property url full `http://` or `https://` URL.
     * @property headers extra request headers (auth tokens, etc.).
     *   Forwarded verbatim to the platform's URL loader.
     * @property timeoutMillis connection + read timeout. Defaults to
     *   30 seconds. The platform loader interprets the value with
     *   best-effort semantics — exact behaviour depends on the
     *   underlying API.
     */
    public class Remote(
        public val url: String,
        public val headers: Map<String, String> = emptyMap(),
        public val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    ) : PdfSource

    /**
     * Android-only payload referenced by a `content://` URI. Resolved
     * through the host's [android.content.ContentResolver]. On iOS,
     * the resolver throws — content URIs are an Android concept.
     *
     * @property uri full `content://...` URI.
     */
    public class ContentUri(public val uri: String) : PdfSource

    /**
     * PDF packaged with the host application. Resolved through
     * `Context.assets` on Android and `Bundle.main` on iOS so the
     * same `Asset("manuals/quickstart.pdf")` works on both
     * platforms.
     *
     * @property path resource-relative path inside the app bundle /
     *   assets folder.
     */
    public class Asset(public val path: String) : PdfSource

    public companion object {

        /** Default network timeout used by [Remote]. */
        public const val DEFAULT_TIMEOUT_MILLIS: Long = 30_000L

        /**
         * Convenience factory for a [PdfDocument] built through the
         * PdfKmp DSL. Returns the [Document] variant so the viewer
         * can surface the document's text runs as a selectable
         * overlay and its hyperlinks as a clickable layer.
         */
        public fun of(document: PdfDocument): PdfSource = Document(
            bytes = document.toByteArray(),
            textRuns = document.textRuns,
            hyperlinks = document.hyperlinks,
        )

        /** Convenience factory for raw bytes — same as `Bytes(bytes)`. */
        public fun of(bytes: ByteArray): PdfSource = Bytes(bytes)

        /**
         * Best-effort detector that converts an opaque string into
         * the matching async [PdfSource] variant. Used for callers
         * who hold a stringly-typed URI (notification payload, deep
         * link, picker result) and don't want to branch themselves.
         *
         * - `http://`, `https://`           → [Remote]
         * - `content://...`                 → [ContentUri]
         * - `asset:///path` / `bundle:///`  → [Asset]
         * - `file://path`, bare `/path/…`   → [FilePath]
         */
        public fun auto(uri: String): PdfSource = when {
            uri.startsWith("http://") || uri.startsWith("https://") -> Remote(uri)
            uri.startsWith("content://") -> ContentUri(uri)
            uri.startsWith("asset:///") -> Asset(uri.removePrefix("asset:///"))
            uri.startsWith("bundle:///") -> Asset(uri.removePrefix("bundle:///"))
            uri.startsWith("file://") -> FilePath(uri.removePrefix("file://"))
            else -> FilePath(uri)
        }
    }
}

/**
 * Returns the underlying byte array for in-memory variants. Async
 * variants return `null` — call [PdfSource.loadBytes] instead, which
 * resolves the bytes on the platform's loader.
 */
internal fun PdfSource.inMemoryBytesOrNull(): ByteArray? = when (this) {
    is PdfSource.Bytes -> bytes
    is PdfSource.Document -> bytes
    else -> null
}

/** Returns the text runs the source carries, or empty if none were captured. */
internal fun PdfSource.textRuns(): List<PdfTextRun> = when (this) {
    is PdfSource.Document -> textRuns
    else -> emptyList()
}

/** Returns the hyperlinks the source carries, or empty if none were captured. */
internal fun PdfSource.hyperlinks(): List<PdfHyperlink> = when (this) {
    is PdfSource.Document -> hyperlinks
    else -> emptyList()
}

/**
 * Resolves any [PdfSource] to a byte array. In-memory variants return
 * synchronously; async variants delegate to the platform-specific
 * [loadAsyncBytes] expect function (which throws on the [ContentUri]
 * variant on iOS, since the scheme is Android-only).
 */
internal suspend fun PdfSource.loadBytes(): ByteArray = when (this) {
    is PdfSource.Bytes -> bytes
    is PdfSource.Document -> bytes
    else -> loadAsyncBytes(this)
}

/**
 * Resolves an async [PdfSource] variant to a byte array. Implemented
 * per platform — Android maps the four async shapes onto
 * [android.content.ContentResolver], [java.net.URL], assets and
 * [java.io.File]; iOS uses [platform.Foundation.NSData] /
 * [platform.Foundation.NSBundle] (and rejects [PdfSource.ContentUri]).
 */
internal expect suspend fun loadAsyncBytes(source: PdfSource): ByteArray
