package com.conamobile.pdfkmp.node

import com.conamobile.pdfkmp.barcode.QrErrorCorrection
import com.conamobile.pdfkmp.style.PdfColor
import com.conamobile.pdfkmp.unit.Dp

/**
 * The 1D barcode symbology a [BarcodeNode] is encoded with.
 *
 * Every variant renders through the same vertical-bar drawing path; they differ
 * only in how the payload becomes a bar/space module pattern.
 */
public enum class BarcodeSymbology {
    /** Code 128 (code sets B/C) — printable ASCII, variable length. */
    Code128,

    /** EAN-13 — 12 digits (check computed) or 13 digits (check verified). */
    Ean13,

    /** UPC-A — 11 digits (check computed) or 12 digits (check verified). */
    UpcA,
}

/**
 * A QR code symbol rendered as crisp vector squares — no rasterisation,
 * so it scans reliably at any print size.
 *
 * The matrix is computed in common code by
 * [com.conamobile.pdfkmp.barcode.QrCodeGenerator]; the version (symbol
 * size) is chosen automatically as the smallest that fits [data] at the
 * requested [errorCorrection] level.
 *
 * Leave some quiet space around the symbol (the QR spec asks for 4
 * modules) — placing it inside a padded container or against the page
 * margin is usually enough.
 *
 * @property data payload encoded in byte mode (UTF-8) — URLs, text, vCards.
 * @property errorCorrection redundancy level; higher levels survive more
 *   damage but produce denser symbols.
 * @property size rendered edge length; `null` falls back to 100 points.
 * @property color module (dark square) colour.
 * @property background fill behind the symbol; `null` leaves it
 *   transparent so the page background shows through.
 */
public data class QrCodeNode(
    val data: String,
    val errorCorrection: QrErrorCorrection = QrErrorCorrection.M,
    val size: Dp? = null,
    val color: PdfColor = PdfColor.Black,
    val background: PdfColor? = PdfColor.White,
) : PdfNode

/**
 * A Code 128 barcode rendered as vector bars.
 *
 * Encoding (code sets B / C with automatic digit-run compression and a
 * mod-103 checksum) happens in common code via
 * [com.conamobile.pdfkmp.barcode.Code128Encoder]. Characters outside
 * printable ASCII (32–126) are rejected at build time.
 *
 * Code 128 readers expect a quiet zone of ~10 modules on both sides;
 * give the node some horizontal breathing room.
 *
 * @property data payload; the accepted alphabet depends on [symbology]
 *   (printable ASCII for Code 128, digits for EAN-13 / UPC-A).
 * @property symbology which 1D encoding to use; defaults to Code 128.
 * @property width rendered width; `null` uses one PDF point per module,
 *   the symbol's natural crisp size.
 * @property height bar height. 1D barcodes carry no data vertically, so
 *   this is purely about scanner ergonomics — taller is easier to scan.
 * @property color bar colour.
 * @property background fill behind the bars; `null` for transparent.
 */
public data class BarcodeNode(
    val data: String,
    val symbology: BarcodeSymbology = BarcodeSymbology.Code128,
    val width: Dp? = null,
    val height: Dp = Dp(50f),
    val color: PdfColor = PdfColor.Black,
    val background: PdfColor? = PdfColor.White,
) : PdfNode

/**
 * A Data Matrix (ECC 200) 2D symbol rendered as crisp vector squares.
 *
 * The module matrix is computed in common code by
 * [com.conamobile.pdfkmp.barcode.DataMatrixEncoder]; the smallest square symbol
 * (10×10 … 52×52) that fits [data] is chosen automatically. ASCII encodation
 * only — bytes above 127 are rejected at build time.
 *
 * Like QR, Data Matrix expects a quiet zone (1 module here) — place it in a
 * padded container or against the page margin.
 *
 * @property data payload; ASCII bytes 0..127.
 * @property size rendered edge length; `null` falls back to 100 points.
 * @property color module (dark square) colour.
 * @property background fill behind the symbol; `null` for transparent.
 */
public data class DataMatrixNode(
    val data: String,
    val size: Dp? = null,
    val color: PdfColor = PdfColor.Black,
    val background: PdfColor? = PdfColor.White,
) : PdfNode
