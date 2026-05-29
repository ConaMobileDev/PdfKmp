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
