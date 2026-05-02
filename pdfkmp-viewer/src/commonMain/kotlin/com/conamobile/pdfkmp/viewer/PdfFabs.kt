package com.conamobile.pdfkmp.viewer

import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.conamobile.pdfkmp.PdfDocument

/**
 * Material 3 [FloatingActionButton] that hands [bytes] off to the
 * platform share sheet via [rememberPdfShareAction].
 *
 * Drop it inside a [PdfViewer]'s `overlay` slot to add a share button
 * exactly where you want it, with whatever colour / size / alignment
 * suits the surrounding chrome. The convenience overload that takes a
 * [PdfDocument] saves the call site from materialising the bytes
 * eagerly — the ByteArray is realised on tap instead of on every
 * recomposition.
 *
 * @param bytes encoded `%PDF-…` payload to share.
 * @param fileName filename presented to the share sheet (must include
 *   the `.pdf` extension).
 * @param modifier applied to the underlying [FloatingActionButton].
 * @param containerColor background colour of the FAB.
 * @param contentColor colour of the icon glyph.
 */
@Composable
public fun PdfShareFab(
    bytes: ByteArray,
    fileName: String = "document.pdf",
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    val share = rememberPdfShareAction()
    FloatingActionButton(
        onClick = { share(bytes, fileName) },
        containerColor = containerColor,
        contentColor = contentColor,
        elevation = FloatingActionButtonDefaults.elevation(),
        modifier = modifier,
    ) {
        Icon(imageVector = PdfShareIcon, contentDescription = "Share PDF")
    }
}

/**
 * [PdfDocument]-flavoured overload — defers the [PdfDocument.toByteArray]
 * call to the click handler so re-renders don't allocate. Pair with
 * [PdfViewer]'s `overlay` slot for a one-line setup:
 *
 * ```
 * PdfViewer(
 *     document = doc,
 *     showShareButton = false,
 *     overlay = { PdfShareFab(doc, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) },
 * )
 * ```
 */
@Composable
public fun PdfShareFab(
    document: PdfDocument,
    fileName: String = "document.pdf",
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    val share = rememberPdfShareAction()
    FloatingActionButton(
        onClick = { share(document.toByteArray(), fileName) },
        containerColor = containerColor,
        contentColor = contentColor,
        elevation = FloatingActionButtonDefaults.elevation(),
        modifier = modifier,
    ) {
        Icon(imageVector = PdfShareIcon, contentDescription = "Share PDF")
    }
}

/**
 * Material 3 [FloatingActionButton] that persists [bytes] to the
 * user-visible Downloads folder via [rememberPdfSaveAction]. Same
 * placement story as [PdfShareFab] — host it in [PdfViewer]'s
 * `overlay` slot or anywhere a [FloatingActionButton] fits.
 */
@Composable
public fun PdfSaveFab(
    bytes: ByteArray,
    fileName: String = "document.pdf",
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    val save = rememberPdfSaveAction()
    FloatingActionButton(
        onClick = { save(bytes, fileName) },
        containerColor = containerColor,
        contentColor = contentColor,
        elevation = FloatingActionButtonDefaults.elevation(),
        modifier = modifier,
    ) {
        Icon(imageVector = PdfSaveIcon, contentDescription = "Save PDF")
    }
}

/** [PdfDocument]-flavoured overload of [PdfSaveFab]. */
@Composable
public fun PdfSaveFab(
    document: PdfDocument,
    fileName: String = "document.pdf",
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    val save = rememberPdfSaveAction()
    FloatingActionButton(
        onClick = { save(document.toByteArray(), fileName) },
        containerColor = containerColor,
        contentColor = contentColor,
        elevation = FloatingActionButtonDefaults.elevation(),
        modifier = modifier,
    ) {
        Icon(imageVector = PdfSaveIcon, contentDescription = "Save PDF")
    }
}
