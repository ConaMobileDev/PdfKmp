# Decorations & effects

Every container (`column`, `row`, `box`, `card`) accepts the same decoration
vocabulary.

## Background, border & rounded corners

```kotlin
card(
    background = PdfColor.White,
    cornerRadius = 12.dp,
    padding = Padding.all(16.dp),
    border = BorderStroke(1.dp, PdfColor.LightGray),
) {
    text("Material-style card")
}
```

## Per-corner radius

When corners differ — tabs, asymmetric tiles — pass `cornerRadiusEach`:

```kotlin
box(
    width = 240.dp,
    height = 60.dp,
    background = PdfColor.Blue,
    cornerRadiusEach = CornerRadius.top(16.dp), // top-only rounded; bottom flat
) { aligned(BoxAlignment.Center) { text("Active tab") { color = PdfColor.White } } }

// Fully asymmetric:
cornerRadiusEach = CornerRadius(
    topLeft = 24.dp, topRight = 4.dp,
    bottomLeft = 4.dp, bottomRight = 24.dp,
)
```

!!! note
    Pass `cornerRadius` for uniform corners or `cornerRadiusEach` for asymmetric —
    not both. `cornerRadiusEach` wins if both are present.

## Per-side border

Outline only the sides you want — Material-style accent quotes, progress steppers:

```kotlin
card(
    cornerRadius = 0.dp,
    padding = Padding.all(12.dp),
    borderEach = BorderSides(
        bottom = BorderStroke(2.dp, PdfColor.Blue),
        left = BorderStroke(2.dp, PdfColor.Blue),
    ),
) {
    text("Accent quote — bottom + left only")
}
```

Each side is independent — leave any `null` to skip it.

## Dashed / dotted borders

`BorderStroke` and per-side `BorderSides` take a `LineStyle`:

```kotlin
card(cornerRadius = 0.dp, border = BorderStroke(1.dp, PdfColor.Gray, LineStyle.Dashed)) {
    text("Dashed outline — coupon style")
}
card(cornerRadius = 0.dp, border = BorderStroke(1.5.dp, PdfColor.Blue, LineStyle.Dotted)) {
    text("Dotted outline")
}
```

!!! warning "Dashed / dotted needs sharp corners"
    Dashed / dotted borders are drawn edge-by-edge, so they apply to
    sharp-cornered rectangles. A container with a non-zero corner radius falls
    back to a **solid** outline because the dash phase cannot follow the arc — use
    `cornerRadius = 0.dp` for a dashed look.

## Gradient backgrounds

Pass a `PdfPaint` (linear or radial) via `backgroundPaint`. Coordinates are local
to the container — `(0, 0)` is its top-left:

```kotlin
box(
    width = 480.dp, height = 80.dp, cornerRadius = 12.dp,
    backgroundPaint = PdfPaint.linearGradient(
        from = PdfColor.Blue, to = PdfColor.Red,
        endX = 480f, endY = 0f,
    ),
) {
    aligned(BoxAlignment.Center) {
        text("Sunrise banner") { color = PdfColor.White; bold = true }
    }
}

// Radial:
backgroundPaint = PdfPaint.radialGradient(
    from = PdfColor.White, to = PdfColor(0.1f, 0.1f, 0.4f),
    centerX = 240f, centerY = 60f, radius = 240f,
)

// Multi-stop:
backgroundPaint = PdfPaint.LinearGradient(
    startX = 0f, startY = 0f, endX = 480f, endY = 0f,
    stops = listOf(
        GradientStop(0.00f, PdfColor.Red),
        GradientStop(0.33f, PdfColor.Green),
        GradientStop(0.66f, PdfColor.Blue),
        GradientStop(1.00f, PdfColor.Red),
    ),
)
```

## Drop shadows

PDF has no native blur, so the shadow is approximated with a stack of concentric
translucent rounded rectangles — vector, and visually close to a blur at card
sizes:

```kotlin
card(dropShadow = DropShadow()) {                       // default: 6pt blur, 2pt drop, 18% black
    text("Default elevation") { bold = true }
}
card(
    dropShadow = DropShadow(
        color = PdfColor(0f, 0f, 0.6f, 0.30f),
        offsetY = 4.dp,
        blur = 10.dp,
    ),
) { text("Tinted, deeper shadow") }
```

## Rotation & opacity

`rotation` (degrees, clockwise about the container centre) and `opacity` (group
transparency, `0f`–`1f`):

```kotlin
box(width = 120.dp, height = 40.dp, background = PdfColor.Red, rotation = -8f, opacity = 0.85f) {
    aligned(BoxAlignment.Center) { text("SALE") { color = PdfColor.White; bold = true } }
}
```

## Clipping overflow

Containers with a non-zero `cornerRadius` already clip children to the rounded
shape. For square-cornered fixed-size containers, pass `clipToBounds = true`:

```kotlin
box(
    width = 200.dp,
    height = 80.dp,
    background = PdfColor.LightGray,
    clipToBounds = true,    // sharp-rect clip; default is false
) {
    text(longText)          // anything past 80dp tall gets clipped
}
```

Available on `column`, `row`, `box`, and `card`.

## Circles & ellipses

```kotlin
circle(diameter = 60.dp, fill = PdfColor.Red)
circle(
    diameter = 80.dp,
    fillPaint = PdfPaint.radialGradient(
        from = PdfColor.White, to = PdfColor.Blue,
        centerX = 40f, centerY = 40f, radius = 40f,
    ),
)
ellipse(width = 100.dp, height = 60.dp, fill = PdfColor.Green)
circle(diameter = 60.dp, strokeColor = PdfColor.Black, strokeWidth = 2.dp)
```

## See also

- [Layout containers](layout.md) — the containers these decorations apply to.
- [Images & vectors](images-vectors.md) — `freeDraw` for arbitrary vector shapes.
- `Samples.customDesigns()`, `Samples.designExtras()`.
