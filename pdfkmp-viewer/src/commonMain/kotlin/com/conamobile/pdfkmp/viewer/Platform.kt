package com.conamobile.pdfkmp.viewer

/**
 * `true` on Desktop (JVM), `false` on Android and iOS.
 *
 * Used to surface mouse/keyboard-oriented affordances that only make sense
 * on Desktop — e.g. the on-screen zoom controls in [PdfViewer], which stand
 * in for the pinch-to-zoom gesture that touch platforms get for free but
 * that macOS/Windows/Linux trackpads don't deliver to Compose.
 */
internal expect val pdfViewerIsDesktop: Boolean

/**
 * Default base render density (the zoom-`1×` sharpness, before any zoom) for
 * [PdfViewer] / [KmpPdfViewer] / [KmpPdfLauncher] when the caller doesn't
 * specify one.
 *
 * Desktop defaults to a sharper **3×** (`216 DPI`) — desktops have ample RAM
 * and larger / hi-DPI displays where 2× can read slightly soft — while touch
 * platforms keep **2×** (`144 DPI`, retina-crisp) to bound memory. Callers
 * can always override the `renderDensity` parameter explicitly.
 */
internal val pdfViewerDefaultRenderDensity: Float
    get() = if (pdfViewerIsDesktop) 3f else 2f

/**
 * Installs a platform trackpad pinch-to-zoom hook, forwarding each signed
 * magnification delta to [onPinchDelta]. Returns a disposer.
 *
 * Only macOS Desktop wires anything (Apple's `MagnificationListener`); every
 * other target — Android, iOS, Windows/Linux Desktop — returns a no-op,
 * since touch platforms already get native pinch through Compose's pointer
 * pipeline and non-mac desktops don't expose a magnify gesture.
 */
internal expect fun installTrackpadPinchZoom(onPinchDelta: (Float) -> Unit): () -> Unit
