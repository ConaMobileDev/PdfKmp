# QR, barcodes & charts

All of these emit pure vector geometry — no rasterisation, no external
dependencies — and render identically on every platform.

## QR codes

`qrCode(data)` is a full ISO 18004 Model 2 generator (versions 1–40, all four EC
levels, Reed-Solomon, all 8 masks); the smallest version that fits the payload is
chosen automatically:

```kotlin
qrCode("https://github.com/conamobiledev/PdfKmp", size = 110.dp)

qrCode(
    data = "https://github.com/conamobiledev/PdfKmp",
    size = 110.dp,
    errorCorrection = QrErrorCorrection.H,   // L / M (default) / Q / H
    color = PdfColor(0.1f, 0.2f, 0.5f),      // brand colour; keep strong contrast
)
```

## Barcodes — Code 128, EAN-13, UPC-A

`barcode(data, symbology = …)` draws a 1D barcode as pure vector bars. Pick the
encoding with the `symbology` parameter (`BarcodeSymbology`, default `Code128`).
The human-readable caption is not drawn automatically — add a `text(data)` below:

```kotlin
column(spacing = 4.dp) {
    barcode("INV-2026-00042", height = 50.dp)   // Code 128 — the default
    text("INV-2026-00042") { fontSize = 10.sp }
}

column(spacing = 4.dp) {
    // 12 digits → the EAN-13 check digit is computed for you (13 → verified).
    barcode("400638133393", symbology = BarcodeSymbology.Ean13, height = 50.dp)
    text("EAN-13 4006381333931") { fontSize = 10.sp }
}

column(spacing = 4.dp) {
    // 11 digits → the UPC-A check digit is computed (12 → verified).
    barcode("036000291452", symbology = BarcodeSymbology.UpcA, height = 50.dp)
    text("UPC-A 036000291452") { fontSize = 10.sp }
}
```

| `BarcodeSymbology` | Payload | Notes |
|---|---|---|
| `Code128` (default) | Printable ASCII 32–126 | Digit runs compress via code set C; mod-103 checksum appended for you. |
| `Ean13` | 12 digits (check computed) or 13 (check verified) | Retail product codes. |
| `UpcA` | 11 digits (check computed) or 12 (check verified) | Rendered as the equivalent EAN-13 symbol. |

Invalid input for the chosen symbology throws `IllegalArgumentException` at build time.

## Data Matrix (ECC 200)

`dataMatrix(data)` draws a 2D Data Matrix (ECC 200) symbol as crisp vector
squares. ASCII encodation only — bytes above 127 are rejected at build time — and
the smallest fitting square symbol (10×10 … 52×52) is chosen automatically:

```kotlin
dataMatrix("PdfKmp", size = 90.dp)
dataMatrix("SN-2026-00042", size = 90.dp, color = PdfColor(0.1f, 0.2f, 0.5f))
```

!!! note "PDF417 is not offered"
    PdfKmp ships QR, Code 128, EAN-13/UPC-A, and Data Matrix — but **not** PDF417.
    Its 2,787-entry cluster tables can't be reproduced reliably without the spec
    at hand, and a wrong table would render unscannable symbols, so it was
    deliberately left out rather than shipped subtly broken.

!!! note "Quiet zone"
    Leave a quiet zone around any symbol — page padding or a padded container
    usually suffices.

## Charts

Bar, line, pie, and donut charts are extension functions on any container scope,
composed entirely from the public DSL (`freeDraw`, `row`, `text`) so every glyph
and slice stays vector. Bars / pie / donut take a `List<ChartSeries>` (label +
value + colour).

```kotlin
val series = listOf(
    ChartSeries("Q1", 10f, PdfColor.Red),
    ChartSeries("Q2", 20f, PdfColor.Green),
    ChartSeries("Q3", 15f, PdfColor.Blue),
)

barChart(series, width = 300.dp, height = 160.dp)          // value captions + label row included
lineChart(listOf(3f, 7f, 4f, 9f, 6f), width = 300.dp, height = 120.dp, fillUnderLine = true)
pieChart(series, diameter = 160.dp)                         // swatch + percentage legend by default
donutChart(series, diameter = 160.dp, holeRatio = 0.55f)
```

Signatures (defaults shown):

```kotlin
barChart(
    series: List<ChartSeries>, width: Dp, height: Dp,
    showValues: Boolean = true, showAxis: Boolean = true,
    gridLines: Int = 0, axisColor: PdfColor = PdfColor.Gray, gridColor: PdfColor = PdfColor.LightGray,
)

stackedBarChart(
    groups: List<StackedBarGroup>, width: Dp, height: Dp,
    showAxis: Boolean = true, gridLines: Int = 0, showLegend: Boolean = true,
    axisColor: PdfColor = PdfColor.Gray, gridColor: PdfColor = PdfColor.LightGray,
)

lineChart(
    points: List<Float>, width: Dp, height: Dp,
    color: PdfColor = PdfColor.Blue, strokeWidth: Float = 2f, fillUnderLine: Boolean = false,
    gridLines: Int = 0, axisColor: PdfColor = PdfColor.Gray, gridColor: PdfColor = PdfColor.LightGray,
)

// Multi-series overload — one LineSeries per line, with a legend:
lineChart(
    series: List<LineSeries>, width: Dp, height: Dp,
    strokeWidth: Float = 2f, gridLines: Int = 0, showLegend: Boolean = true,
    axisColor: PdfColor = PdfColor.Gray, gridColor: PdfColor = PdfColor.LightGray,
)

pieChart(slices: List<ChartSeries>, diameter: Dp, showLegend: Boolean = true)
donutChart(slices: List<ChartSeries>, diameter: Dp, holeRatio: Float = 0.55f, showLegend: Boolean = true)
```

`showAxis` toggles (and `axisColor` colours) the always-present baseline + value
axis on bar / stacked-bar / line charts; `gridLines` (0 = none) draws that many
evenly-spaced reference lines behind the data in `gridColor`.

!!! note "Empty / degenerate data draws nothing"
    Empty data, all-zero, or all-negative values draw nothing rather than
    throwing. A `lineChart` needs at least two points.

!!! note "No bundled chart sample"
    Charts have no dedicated `Samples.*` document yet — the snippets above are the
    canonical, copy-pasteable examples. (`Samples.barcodes()` covers QR + Code 128
    + EAN-13/UPC-A + Data Matrix; the design samples exercise `freeDraw`, which
    charts build on.)

## See also

- [Images & vectors](images-vectors.md) — `freeDraw`, the primitive charts build on.
- `Samples.barcodes()` for QR + Code 128 + EAN-13/UPC-A + Data Matrix;
  `Samples.designExtras()` for free-form vector design.
