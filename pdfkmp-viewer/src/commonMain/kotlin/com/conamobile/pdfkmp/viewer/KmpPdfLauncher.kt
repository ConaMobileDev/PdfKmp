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
 * **When to prefer the composable [KmpPdfViewer]** — when you're
 * already inside a Compose-based navigation graph
 * (`NavHost` / Voyager / Decompose). The composable form integrates
 * with the host's back stack and theming directly, no Intent /
 * `presentViewController` ceremony.
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
    )

    /** Opens raw [bytes] in a hosted viewer screen. */
    public fun open(
        bytes: ByteArray,
        title: String = "Document",
        fileName: String = "document.pdf",
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
    )
}
