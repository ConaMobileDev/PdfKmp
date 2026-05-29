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
 * Installs a platform trackpad pinch-to-zoom hook, forwarding each signed
 * magnification delta to [onPinchDelta]. Returns a disposer.
 *
 * Only macOS Desktop wires anything (Apple's `MagnificationListener`); every
 * other target — Android, iOS, Windows/Linux Desktop — returns a no-op,
 * since touch platforms already get native pinch through Compose's pointer
 * pipeline and non-mac desktops don't expose a magnify gesture.
 */
internal expect fun installTrackpadPinchZoom(onPinchDelta: (Float) -> Unit): () -> Unit
