package com.conamobile.pdfkmp.viewer

internal actual val pdfViewerIsDesktop: Boolean = false

internal actual fun installTrackpadPinchZoom(onPinchDelta: (Float) -> Unit): () -> Unit = {}
