package com.conamobile.pdfkmp.sampledesktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.conamobile.pdfkmp.PdfDocument
import com.conamobile.pdfkmp.barcode.QrErrorCorrection
import com.conamobile.pdfkmp.geometry.PageSize
import com.conamobile.pdfkmp.geometry.Padding
import com.conamobile.pdfkmp.layout.BoxAlignment
import com.conamobile.pdfkmp.layout.HorizontalAlignment
import com.conamobile.pdfkmp.pdf
import com.conamobile.pdfkmp.style.PdfColor
import com.conamobile.pdfkmp.style.TableBorder
import com.conamobile.pdfkmp.style.TableColumn
import com.conamobile.pdfkmp.style.TextAlign
import com.conamobile.pdfkmp.style.TextStyle
import com.conamobile.pdfkmp.viewer.KmpPdfViewer
import kotlinx.coroutines.delay
import com.conamobile.pdfkmp.unit.dp as pdfDp
import com.conamobile.pdfkmp.unit.sp as pdfSp

/**
 * Developer **live-preview playground** for the PdfKmp DSL.
 *
 * The intent is to give library users an *instant feel* for the `pdf { … }`
 * DSL without an edit-compile-run cycle. We cannot compile arbitrary Kotlin at
 * runtime, so instead of a free-form code editor this playground exposes the
 * DSL's knobs as **live parameter controls** — title, body, font size, page
 * size + orientation, paragraph alignment (including the new `Justify`), an
 * accent colour, and feature toggles (a `qrCode()` of the title, a small
 * `table(…)` demo, and a dark diagonal watermark).
 *
 * Every control change rebuilds a real [PdfDocument] through the *same*
 * `pdf { }` DSL the rest of the library uses (see [buildPlaygroundDocument])
 * and re-renders it in the embedded [KmpPdfViewer] on the right. Rebuilds are
 * debounced ~300 ms so dragging the font-size slider or typing in the body
 * doesn't thrash the PdfBox renderer.
 *
 * Saving reuses the viewer's own toolbar **download** affordance, which on
 * Desktop opens a native `java.awt.FileDialog` save sheet — no bespoke save
 * button needed.
 *
 * This screen intentionally lives only in the Desktop sample; it is a
 * developer aid, not part of the published library surface.
 */

/** Page-size presets offered by the playground, with portrait/landscape applied separately. */
private enum class PagePreset(val label: String, val base: PageSize) {
    A4("A4", PageSize.A4),
    A5("A5", PageSize.A5),
    Letter("Letter", PageSize.Letter),
}

/** Paragraph alignment options surfaced as a dropdown. */
private enum class AlignOption(val label: String, val align: TextAlign) {
    Start("Start", TextAlign.Start),
    Center("Center", TextAlign.Center),
    End("End", TextAlign.End),
    Justify("Justify", TextAlign.Justify),
}

/** Accent-colour presets — the swatch shown in the UI and the matching [PdfColor]. */
private enum class AccentPreset(val label: String, val ui: Color, val pdf: PdfColor) {
    Indigo("Indigo", Color(0xFF4F46E5), PdfColor.fromRgb(0x4F46E5)),
    Emerald("Emerald", Color(0xFF059669), PdfColor.fromRgb(0x059669)),
    Rose("Rose", Color(0xFFE11D48), PdfColor.fromRgb(0xE11D48)),
    Amber("Amber", Color(0xFFD97706), PdfColor.fromRgb(0xD97706)),
    Slate("Slate", Color(0xFF334155), PdfColor.fromRgb(0x334155)),
}

/** Immutable snapshot of every control value — the single input to [buildPlaygroundDocument]. */
private data class PlaygroundParams(
    val title: String,
    val body: String,
    val fontSize: Int,
    val page: PagePreset,
    val landscape: Boolean,
    val align: AlignOption,
    val accent: AccentPreset,
    val showQr: Boolean,
    val showTable: Boolean,
    val showWatermark: Boolean,
)

private val DEFAULT_BODY = """
PdfKmp is a Compose-style PDF generator for Kotlin Multiplatform. This live preview rebuilds a real document through the pdf { } DSL on every change — adjust the controls on the left and watch the page update.

Try switching the alignment to Justify, bump the font size, flip to landscape, or toggle the QR code and table demos below. There is no compile step: the bytes you see are produced by the exact same renderer your app would call.
""".trim()

/**
 * Builds a [PdfDocument] from the playground [params] using the real
 * `pdf { }` DSL. Exercises `justify` alignment and `qrCode()` (and optionally
 * a `table(…)` and a watermark) so the new core features are demonstrated
 * end-to-end through the same path a library consumer would use.
 */
private fun buildPlaygroundDocument(params: PlaygroundParams): PdfDocument = pdf {
    metadata { title = params.title.ifBlank { "PdfKmp Playground" } }

    val accent = params.accent.pdf
    val size = if (params.landscape) params.page.base.landscape else params.page.base
    defaultTextStyle = TextStyle(
        fontSize = params.fontSize.pdfSp,
        color = PdfColor.fromRgb(0x33373F),
    )

    page(size) {
        spacing = 14.pdfDp

        if (params.showWatermark) {
            watermark {
                aligned(BoxAlignment.Center) {
                    text("CONFIDENTIAL") {
                        fontSize = 90.pdfSp
                        bold = true
                        // Dark, semi-transparent diagonal-feeling stamp.
                        color = PdfColor(0.10f, 0.10f, 0.12f, 0.10f)
                    }
                }
            }
        }

        // Title row with an accent rule underneath.
        text(params.title.ifBlank { "Untitled document" }) {
            fontSize = (params.fontSize + 14).pdfSp
            bold = true
            color = accent
        }
        divider(thickness = 2.pdfDp, color = accent)

        // Optional QR of the title — demonstrates qrCode() from the DSL.
        if (params.showQr) {
            row(spacing = 12.pdfDp) {
                qrCode(
                    data = params.title.ifBlank { "PdfKmp Playground" },
                    size = 96.pdfDp,
                    errorCorrection = QrErrorCorrection.M,
                    color = accent,
                )
                weighted(1f) {
                    text("Scan to read the document title.") {
                        fontSize = (params.fontSize - 1).coerceAtLeast(6).pdfSp
                        color = PdfColor.Gray
                    }
                }
            }
        }

        // Body — every paragraph honours the chosen alignment, including Justify.
        params.body.split("\n").forEach { paragraph ->
            if (paragraph.isBlank()) {
                spacer(height = 6.pdfDp)
            } else {
                text(paragraph) { align = params.align.align }
            }
        }

        // Optional table demo — fixed + weighted columns, accent header.
        if (params.showTable) {
            spacer(height = 6.pdfDp)
            text("Table demo") {
                fontSize = (params.fontSize + 4).pdfSp
                bold = true
                color = accent
            }
            table(
                columns = listOf(
                    TableColumn.Fixed(60.pdfDp),
                    TableColumn.Weight(2f),
                    TableColumn.Weight(1f),
                ),
                border = TableBorder(color = PdfColor.fromRgb(0xCFD8DC), width = 1.pdfDp),
                cornerRadius = 8.pdfDp,
                cellPadding = Padding.symmetric(horizontal = 10.pdfDp, vertical = 8.pdfDp),
            ) {
                header(background = accent) {
                    cell("#") { color = PdfColor.White; bold = true }
                    cell("Item") { color = PdfColor.White; bold = true }
                    cell("Value", horizontalAlignment = HorizontalAlignment.End) {
                        color = PdfColor.White; bold = true
                    }
                }
                listOf(
                    Triple("1", "Vector text", "Crisp"),
                    Triple("2", "QR codes", "Scannable"),
                    Triple("3", "Tables", "Styled"),
                ).forEachIndexed { index, (num, item, value) ->
                    val zebra = if (index % 2 == 0) PdfColor.White else PdfColor.fromRgb(0xF7F9FA)
                    row(background = zebra) {
                        cell(num) { color = PdfColor.Gray }
                        cell(item)
                        cell(value, horizontalAlignment = HorizontalAlignment.End) {
                            bold = true
                            color = accent
                        }
                    }
                }
            }
        }
    }
}

/**
 * The playground screen: parameter controls on the left, a debounced live
 * [KmpPdfViewer] on the right. The viewer's toolbar download button is the
 * "Save PDF…" path (native save dialog on Desktop).
 */
@Composable
internal fun PlaygroundScreen(onBack: () -> Unit) {
    var title by remember { mutableStateOf("PdfKmp Live Playground") }
    var body by remember { mutableStateOf(DEFAULT_BODY) }
    var fontSize by remember { mutableStateOf(13f) }
    var page by remember { mutableStateOf(PagePreset.A4) }
    var landscape by remember { mutableStateOf(false) }
    var align by remember { mutableStateOf(AlignOption.Justify) }
    var accent by remember { mutableStateOf(AccentPreset.Indigo) }
    var showQr by remember { mutableStateOf(true) }
    var showTable by remember { mutableStateOf(true) }
    var showWatermark by remember { mutableStateOf(false) }

    val params = PlaygroundParams(
        title = title,
        body = body,
        fontSize = fontSize.toInt(),
        page = page,
        landscape = landscape,
        align = align,
        accent = accent,
        showQr = showQr,
        showTable = showTable,
        showWatermark = showWatermark,
    )

    // Debounce ~300 ms: rebuild the document only after the controls settle,
    // so slider drags / typing don't hammer the PdfBox renderer.
    var debounced by remember { mutableStateOf(params) }
    LaunchedEffect(params) {
        delay(300)
        debounced = params
    }
    val document = remember(debounced) { buildPlaygroundDocument(debounced) }

    Row(Modifier.fillMaxSize()) {
        ControlsPane(
            modifier = Modifier.width(360.dp).fillMaxHeight(),
            onBack = onBack,
            title = title, onTitle = { title = it },
            body = body, onBody = { body = it },
            fontSize = fontSize, onFontSize = { fontSize = it },
            page = page, onPage = { page = it },
            landscape = landscape, onLandscape = { landscape = it },
            align = align, onAlign = { align = it },
            accent = accent, onAccent = { accent = it },
            showQr = showQr, onShowQr = { showQr = it },
            showTable = showTable, onShowTable = { showTable = it },
            showWatermark = showWatermark, onShowWatermark = { showWatermark = it },
        )
        // The viewer re-renders whenever `document` changes; its toolbar
        // download button opens the native Save dialog on Desktop.
        KmpPdfViewer(
            document = document,
            modifier = Modifier.weight(1f).fillMaxHeight(),
            title = title.ifBlank { "Playground" },
            fileName = "playground.pdf",
            showSearch = false,
            showZoomControls = false,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ControlsPane(
    modifier: Modifier,
    onBack: () -> Unit,
    title: String, onTitle: (String) -> Unit,
    body: String, onBody: (String) -> Unit,
    fontSize: Float, onFontSize: (Float) -> Unit,
    page: PagePreset, onPage: (PagePreset) -> Unit,
    landscape: Boolean, onLandscape: (Boolean) -> Unit,
    align: AlignOption, onAlign: (AlignOption) -> Unit,
    accent: AccentPreset, onAccent: (AccentPreset) -> Unit,
    showQr: Boolean, onShowQr: (Boolean) -> Unit,
    showTable: Boolean, onShowTable: (Boolean) -> Unit,
    showWatermark: Boolean, onShowWatermark: (Boolean) -> Unit,
) {
    Card(modifier = modifier, shape = RoundedCornerShape(0.dp)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onBack) { Text("← Back") }
                Spacer(Modifier.width(12.dp))
                Text("Playground", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                "Live DSL preview — every change rebuilds a real pdf { } document, debounced ~300 ms.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = title,
                onValueChange = onTitle,
                label = { Text("Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = body,
                onValueChange = onBody,
                label = { Text("Body") },
                modifier = Modifier.fillMaxWidth().height(160.dp),
            )

            LabeledSlider(
                label = "Font size: ${fontSize.toInt()} sp",
                value = fontSize,
                valueRange = 8f..32f,
                steps = 32 - 8 - 1,
                onValueChange = onFontSize,
            )

            EnumDropdown(
                label = "Page size",
                options = PagePreset.entries,
                selected = page,
                optionLabel = { it.label },
                onSelected = onPage,
            )

            ToggleRow("Landscape", landscape, onLandscape)

            EnumDropdown(
                label = "Alignment",
                options = AlignOption.entries,
                selected = align,
                optionLabel = { it.label },
                onSelected = onAlign,
            )

            Text("Accent colour", fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AccentPreset.entries.forEach { preset ->
                    val selected = preset == accent
                    Box(
                        Modifier
                            .size(32.dp)
                            .background(preset.ui, CircleShape)
                            .border(
                                width = if (selected) 3.dp else 1.dp,
                                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                                shape = CircleShape,
                            )
                            .clickable { onAccent(preset) },
                    )
                }
            }

            Text("Toggles", fontSize = 13.sp, fontWeight = FontWeight.Medium)
            ToggleRow("Show QR of title", showQr, onShowQr)
            ToggleRow("Show table demo", showTable, onShowTable)
            ToggleRow("Dark watermark", showWatermark, onShowWatermark)

            Spacer(Modifier.height(4.dp))
            Text(
                "Save: use the download button in the viewer's toolbar (opens a native Save dialog).",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
) {
    Column {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> EnumDropdown(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onSelected: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(optionLabel(selected))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(optionLabel(option)) },
                        onClick = {
                            onSelected(option)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 14.sp)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
