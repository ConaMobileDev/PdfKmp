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
 * [KmpPdfViewer] does — visibility toggles for search / share /
 * download / page indicator, zoom and gesture switches, the
 * iOS-only [backLabel] string next to the chevron, and the
 * `renderDensity` / `maxZoom` numerics. Compose-typed parameters
 * (background colour, padding, page spacing) are intentionally not
 * exposed here; reach for the [KmpPdfViewer] composable when you
 * need that level of theming control.
 *
 * **When to prefer the composable [KmpPdfViewer]** — when you're
 * already inside a Compose-based navigation graph
 * (`NavHost` / Voyager / Decompose). The composable form integrates
 * with the host's back stack and theming directly, no Intent /
 * `presentViewController` ceremony.
 *
 * @param title shown in the topbar's centered title (Classic iOS) /
 *   bold first line (Minimal Mono).
 * @param fileName user-visible filename surfaced to the share sheet
 *   and the "Saved to Downloads" entry. Must include `.pdf`.
 * @param backLabel iOS-only previous-screen label rendered next to
 *   the chevron (e.g. `"Files"`). Ignored on Android.
 * @param showSearch hide / show the search affordance. Auto-
 *   suppressed when the source carries no text runs.
 * @param showShare hide / show the share affordance.
 * @param showDownload hide / show the download affordance.
 * @param showPageIndicator hide / show the bottom-centre page chip.
 * @param zoomEnabled master switch for pinch + double-tap zoom.
 * @param doubleTapToZoom independent toggle for the double-tap
 *   shortcut.
 * @param textSelectable toggles the invisible selectable text
 *   overlay. Only effective on documents loaded from a [PdfDocument].
 * @param hyperlinksEnabled toggles the invisible clickable layer that
 *   opens hyperlink annotations in the system browser. Same
 *   `PdfDocument`-only caveat as [textSelectable].
 * @param renderDensity baseline scaling factor applied during
 *   rasterisation.
 * @param maxZoom upper bound for the pinch gesture.
 */
public expect object KmpPdfLauncher {

    /**
     * Opens [uri] in a hosted viewer screen. Bytes are fetched on a
     * background dispatcher via [loadPdfBytesFromUri], so the call
     * site itself returns immediately.
     */
    public fun open(
        uri: String,
        title: String = "Document",
        fileName: String = "document.pdf",
        backLabel: String? = null,
        showSearch: Boolean = true,
        showShare: Boolean = true,
        showDownload: Boolean = true,
        showPageIndicator: Boolean = true,
        zoomEnabled: Boolean = true,
        doubleTapToZoom: Boolean = true,
        textSelectable: Boolean = true,
        hyperlinksEnabled: Boolean = true,
        renderDensity: Float = 2f,
        maxZoom: Float = 5f,
    )

    /** Opens raw [bytes] in a hosted viewer screen. */
    public fun open(
        bytes: ByteArray,
        title: String = "Document",
        fileName: String = "document.pdf",
        backLabel: String? = null,
        showSearch: Boolean = true,
        showShare: Boolean = true,
        showDownload: Boolean = true,
        showPageIndicator: Boolean = true,
        zoomEnabled: Boolean = true,
        doubleTapToZoom: Boolean = true,
        textSelectable: Boolean = true,
        hyperlinksEnabled: Boolean = true,
        renderDensity: Float = 2f,
        maxZoom: Float = 5f,
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
        showSearch: Boolean = true,
        showShare: Boolean = true,
        showDownload: Boolean = true,
        showPageIndicator: Boolean = true,
        zoomEnabled: Boolean = true,
        doubleTapToZoom: Boolean = true,
        textSelectable: Boolean = true,
        hyperlinksEnabled: Boolean = true,
        renderDensity: Float = 2f,
        maxZoom: Float = 5f,
    )
}
