package com.conamobile.pdfkmp.viewer

import com.conamobile.pdfkmp.PdfDocument

/**
 * **Imperative counterpart of [KmpPdfViewer].**
 *
 * Use this when you need to open the viewer from outside a
 * `@Composable` scope — a click handler, a `LaunchedEffect`, a
 * suspend function, a notification tap, etc. The launcher hosts
 * [KmpPdfViewer] inside a platform-native shell:
 *
 * - **Android** — opens a translucent full-screen `Activity` shipped
 *   by the library. The activity inherits your application theme and
 *   finishes itself when the user taps back.
 * - **iOS** — presents a `UIViewController` (full-screen modal)
 *   built around `ComposeUIViewController`. Dismisses on back tap.
 *
 * The launcher reads the host's process-global context (set up by
 * the library's `:pdfkmp-viewer:internal:ViewerContextInitializer`
 * App Startup provider on Android, or the key window's root view
 * controller on iOS), so callers don't have to thread an `Activity`
 * or `UIViewController` reference through their domain code:
 *
 * ```kotlin
 * Button(onClick = {
 *     scope.launch {
 *         val pdf = pdfAsync { … }
 *         KmpPdfLauncher.open(pdf, title = "Invoice")
 *     }
 * })
 * ```
 *
 * Every overload accepts the same configuration knobs that
 * [KmpPdfViewer] does — visibility toggles, zoom and gesture
 * switches, the iOS-only [backLabel] string next to the chevron,
 * and the numeric `renderDensity` / `maxZoom`. Compose-typed
 * parameters (background colour, padding, page spacing) are
 * intentionally not exposed here; reach for the [KmpPdfViewer]
 * composable when you need that level of theming control.
 *
 * **When to prefer the composable [KmpPdfViewer]** — when you're
 * already inside a Compose-based navigation graph
 * (`NavHost` / Voyager / Decompose). The composable form integrates
 * with the host's back stack and theming directly, no Intent /
 * `presentViewController` ceremony.
 *
 * The per-function parameter docs on the individual `open(...)`
 * overloads spell out the meaning of each flag — they mirror the
 * companion options on [KmpPdfViewer] one-to-one.
 */
public expect object KmpPdfLauncher {

    /**
     * Opens [uri] in a hosted viewer screen. Bytes are fetched on a
     * background dispatcher via the platform's native loader, so the
     * call site itself returns immediately.
     *
     * Prefer [open] with an explicit [PdfSource] when you know the
     * transport — strings hide the scheme and can't carry HTTP
     * headers / timeouts.
     */
    public fun open(
        uri: String,
        title: String = "Document",
        fileName: String = "document.pdf",
        backLabel: String? = null,
        showTopBar: Boolean = true,
        showSearch: Boolean = true,
        showShare: Boolean = true,
        showDownload: Boolean = true,
        showPageIndicator: Boolean = true,
        zoomEnabled: Boolean = true,
        doubleTapToZoom: Boolean = true,
        textSelectable: Boolean = true,
        hyperlinksEnabled: Boolean = true,
        renderDensity: Float = pdfViewerDefaultRenderDensity,
        maxZoom: Float = 5f,
        cacheStrategy: PdfPageCacheStrategy = PdfPageCacheStrategy.Auto,
    )

    /** Opens raw [bytes] in a hosted viewer screen. */
    public fun open(
        bytes: ByteArray,
        title: String = "Document",
        fileName: String = "document.pdf",
        backLabel: String? = null,
        showTopBar: Boolean = true,
        showSearch: Boolean = true,
        showShare: Boolean = true,
        showDownload: Boolean = true,
        showPageIndicator: Boolean = true,
        zoomEnabled: Boolean = true,
        doubleTapToZoom: Boolean = true,
        textSelectable: Boolean = true,
        hyperlinksEnabled: Boolean = true,
        renderDensity: Float = pdfViewerDefaultRenderDensity,
        maxZoom: Float = 5f,
        cacheStrategy: PdfPageCacheStrategy = PdfPageCacheStrategy.Auto,
    )

    /**
     * Opens a PdfKmp-built [document] in a hosted viewer screen. The
     * library snapshots the encoded bytes plus the captured text
     * runs and hyperlinks, so text selection and link navigation
     * survive the hop into the launcher's hosted shell.
     */
    public fun open(
        document: PdfDocument,
        title: String = "Document",
        fileName: String = "document.pdf",
        backLabel: String? = null,
        showTopBar: Boolean = true,
        showSearch: Boolean = true,
        showShare: Boolean = true,
        showDownload: Boolean = true,
        showPageIndicator: Boolean = true,
        zoomEnabled: Boolean = true,
        doubleTapToZoom: Boolean = true,
        textSelectable: Boolean = true,
        hyperlinksEnabled: Boolean = true,
        renderDensity: Float = pdfViewerDefaultRenderDensity,
        maxZoom: Float = 5f,
        cacheStrategy: PdfPageCacheStrategy = PdfPageCacheStrategy.Auto,
    )
}
