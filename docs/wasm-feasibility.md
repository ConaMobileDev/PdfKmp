# wasmJs target — feasibility plan

*Written 2026-06. Status: researched and planned; implementation
deferred to its own release because it requires a pure-Kotlin PDF
writer.*

## Why it doesn't exist yet

Every current backend leans on a platform PDF engine: PdfBox (JVM),
Core Graphics (iOS), `android.graphics.pdf` (Android). The browser has
none, so a `wasmJs` target needs PdfKmp to **write the PDF object
structure itself** — fonts included.

## What already exists toward it

The architecture was built for this: `DocumentRenderer` is pure common
code and talks only to `PdfCanvas`/`PdfDriver`. A wasm backend is "just"
one more driver pair. Two recent pieces shrink the gap considerably:

- `com.conamobile.pdfkmp.pdfwriter.PdfPatcher` (added for Android
  link/metadata parity) already contains pure-Kotlin primitives for PDF
  object serialisation: literal-string escaping, UTF-16BE text, xref
  sections, trailers, dictionaries. A from-scratch writer reuses these.
- Every shape/text draw is already expressed as vector commands
  (`PathCommand`, text runs with resolved styles) — the exact level a
  content stream needs.

## Remaining work, in order

1. **`KmpPdfWriter` (commonMain, internal)** — minimal PDF 1.7 writer:
   object table, page tree, content streams (re-using the path/colour
   operators the JVM canvas emits via PdfBox today), Flate compression
   (a pure-Kotlin deflate, or uncompressed streams at first — valid,
   just larger).
2. **Font strategy** — the hard 80%. Options, in increasing effort:
   a. Standard-14 fonts only (Helvetica metrics bundled as static AFM
      tables) — no embedding, ASCII/Latin-1 coverage, smallest first
      release.
   b. Embed the full bundled Inter TTF un-subset (simple, ~300 KB per
      document).
   c. TrueType subsetting in Kotlin (glyf/loca/cmap/hmtx table rewrite)
      — the long-term answer; well-documented format, ~1.5k lines.
3. **`WasmPdfDriver`** implementing `PdfDriver`/`PdfCanvas` over the
   writer + a `FontMetrics` backed by the bundled font's parsed `hmtx`
   (the TTF parser from 2c also yields metrics).
4. **Targets wiring** — add `wasmJs()` to `:pdfkmp` (core is
   dependency-free in common, so only the new backend gates this);
   `:pdfkmp-compose-resources` follows for free (pure common);
   `:pdfkmp-viewer` needs a separate rendering story (no PdfBox/PDFKit
   in the browser — likely pdf.js interop) and should ship later.

## Estimate

- Phase 1 release (standard-14 fonts, uncompressed streams, no images):
  ~2–3 focused days. Enough for text documents and all vector features
  (shapes, QR codes, barcodes, charts — they are pure paths).
- Phase 2 (Inter embedding + JPEG/PNG image XObjects + Flate): +2 days.
- Phase 3 (TTF subsetting, full i18n custom fonts): +3–4 days.

## Recommendation

Ship phases 1–2 as `pdfkmp` 1.3.x behind an `@ExperimentalPdfKmpApi`
annotation on the wasm artefact, gathering issues before committing to
the subsetting work.
