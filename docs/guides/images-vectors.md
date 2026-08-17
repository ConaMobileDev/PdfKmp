# Images & vectors

## Raster images

PNG and JPEG are decoded everywhere; WebP / HEIF where the platform supports them.

```kotlin
image(bytes = imageBytes, width = 300.dp, contentScale = ContentScale.Fit)

// Square crop:
image(bytes = imageBytes, width = 200.dp, height = 200.dp, contentScale = ContentScale.Crop)

// Width given, height derived from intrinsic aspect ratio:
image(bytes = imageBytes, width = 480.dp)

// Intrinsic pixel size (1px -> 1pt):
image(bytes = imageBytes)
```

`ContentScale` options: `Fit`, `Crop`, `FillBounds` (stretches to the destination
box).

### Downscaling

`image(..., allowDownScale = true)` is the **default**. The platform decoder
subsamples raster bytes so they roughly match the rendered size at 200 DPI before
drawing — this keeps heap and PDF size sane when consumers paste 4000-px photos
into 200-pt thumbnails. Pass `allowDownScale = false` for archival / print
workflows where every original pixel must survive.

```kotlin
image(bytes = imageBytes, width = 240.dp, height = 160.dp, allowDownScale = false)
```

### Decode budget

Independently of `allowDownScale`, every backend caps how many pixels a decode
may allocate, based on the image's **declared header dimensions** — read before
any pixel memory is touched. The default ceiling is 50 megapixels
(`PdfImagePolicy.DEFAULT_MAX_DECODE_PIXELS`).

An image over the ceiling is **sub-sampled down to fit**, not dropped —
`inSampleSize` on Android, `ImageReadParam.setSourceSubsampling` on the JVM,
`CGImageSourceCreateThumbnailAtIndex` on iOS — and the reduction is reported
through `PdfLog`. This is what stops a *dimension bomb*: a few dozen bytes of
PNG header can claim 50 000 × 50 000 px, and without a pre-decode bound the
platform decoder obliges with a multi-gigabyte allocation and takes the process
with it.

Raise the ceiling once at startup when a document legitimately carries very
large imagery — A0 at 300 DPI is ~139 MP, well over the untrusted default:

```kotlin
PdfImagePolicy.maxDecodePixels = 200_000_000L
```

!!! warning "Web (Wasm) refuses instead of sampling"
    The pure-Kotlin writer embeds encoded streams verbatim and owns no decoder,
    so it has nothing to sample with. An over-budget image is skipped there with
    a `PdfLog` warning rather than handed on to the reader. Raising
    `maxDecodePixels` lets it through on that target too.

### Accessibility alt text

Pass `altText` to carry an accessibility description into backends that write
tagged structure (the JVM/Desktop backend records it as the image's `/Alt`):

```kotlin
image(bytes = chartBytes, width = 300.dp, altText = "Q1 revenue bar chart")
```

!!! note "Compose Resources users"
    Pass a typed `Res.drawable.*` reference straight in:
    `image(Res.drawable.cover_photo, width = 480.dp)` from the
    `pdfkmp-compose-resources` module (inside `pdfAsync { }`).

## Vector / SVG

Both Android `<vector>` XML and W3C `<svg>` are accepted by
`VectorImage.parse(...)`. Vectors stay vector inside the PDF — no rasterisation,
sharp at any zoom.

```kotlin
val star = VectorImage.parse(starXml) // parse once, reuse many times

vector(image = star, width = 64.dp)
vector(image = star, width = 64.dp, tint = PdfColor.Red)              // override fill
vector(image = star, width = 64.dp, strokeMode = VectorStrokeMode.Disabled)

// Inline parsing for one-offs:
vector(xml = """<svg ...>...</svg>""", width = 48.dp)
```

### Support matrix

| Category | Supported |
|---|---|
| Fills | solid, linear & radial gradients |
| Paths | `M/L/Q/C/Z` + elliptical arcs (`A`/`a`) |
| SVG shapes | `<rect>` (incl. rounded), `<circle>`, `<ellipse>`, `<line>`, `<polyline>`, `<polygon>` |
| Transforms | `<g transform="translate/rotate/scale">`, nested `<g>` |
| Styling | inline `style=""`, opacity attributes |
| Colours | `rgb()`, ~20 named colours, hex |
| Coordinates | viewBox offsets |

!!! note "Compose Resources users"
    Skip the manual `VectorImage.parse(...)` and pass a typed reference directly:
    `vector(Res.drawable.logo, width = 64.dp, tint = PdfColor.Blue)` (inside
    `pdfAsync { }`).

## Free-form vector drawing (`freeDraw`)

When the primitives don't cover a shape, `freeDraw(width, height) { path { … } }`
lets you author paths in a local `(0, 0)`–`(width, height)` coordinate space that
scales into the node's final rectangle. The pen model matches any 2D canvas —
`moveTo` / `lineTo` / `quadTo` / `cubicTo` / `rect` / `close` — and each `path`
can fill (solid or gradient) and / or stroke:

```kotlin
freeDraw(width = 60.dp, height = 60.dp) {
    path(fill = PdfColor(1f, 0.8f, 0.2f), strokeColor = PdfColor.Black, strokeWidth = 2f) {
        moveTo(30f, 4f); lineTo(56f, 52f); lineTo(4f, 52f); close()
    }
    path(fill = PdfColor.Black) { rect(27f, 20f, 6f, 18f); rect(27f, 42f, 6f, 6f) }
}
```

## See also

- [QR, barcodes & charts](graphics.md) — built on `freeDraw`.
- [Decorations & effects](decorations.md) — circles, ellipses, gradients.
- `Samples.withImage()`, `Samples.slicedImage()`, `Samples.imageDownscale()`, `Samples.vectorShowcase()`, `Samples.vectorAdvanced()`.
