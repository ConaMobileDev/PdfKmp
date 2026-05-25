package com.conamobile.pdfkmp.viewer

/**
 * Internal value-object that bundles every configurable option
 * supported by [KmpPdfLauncher.open]. The launcher exposes flat
 * parameters in its public API for ergonomics; this class exists so
 * the Android Intent layer and the iOS presentation layer can pass
 * the configuration around as a single value, and so the defaults
 * stay centralised.
 *
 * Mirrors a curated subset of the [KmpPdfViewer] composable's
 * parameters — namely the ones whose values can survive a hop into
 * a hosted shell. Compose-typed knobs (`backgroundColor`,
 * `pageBackgroundColor`, `contentPadding`, `pageSpacing`) are
 * intentionally omitted here; callers that need to theme the viewer
 * should use the [KmpPdfViewer] composable inside their own Compose
 * tree instead.
 */
internal data class KmpPdfLaunchOptions(
    val title: String = "Document",
    val fileName: String = "document.pdf",
    val backLabel: String? = null,
    val showTopBar: Boolean = true,
    val showSearch: Boolean = true,
    val showShare: Boolean = true,
    val showDownload: Boolean = true,
    val showPageIndicator: Boolean = true,
    val zoomEnabled: Boolean = true,
    val doubleTapToZoom: Boolean = true,
    val textSelectable: Boolean = true,
    val hyperlinksEnabled: Boolean = true,
    val renderDensity: Float = 2f,
    val maxZoom: Float = 5f,
    val cacheStrategy: PdfPageCacheStrategy = PdfPageCacheStrategy.Auto,
)
