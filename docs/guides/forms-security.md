# Forms & security

Document-level navigation and security features differ by platform because the
native PDF generators expose different capabilities. See
[Platform parity](platform-parity.md) for the full matrix.

## Forms (AcroForm)

`textField(name, …)` and `checkBox(name, …)` add named form fields:

```kotlin
text("Full name") { bold = true }
textField("fullName", width = 300.dp)
textField("email", width = 300.dp, value = "user@example.com")
textField("notes", width = 420.dp, height = 80.dp, multiline = true)

row(spacing = 8.dp, verticalAlignment = VerticalAlignment.Center) {
    checkBox("agree", checked = true)
    text("I agree to the terms")
}
```

!!! warning "Interactive only on Desktop / JVM"
    Fields are **interactive only on Desktop / JVM** (real PdfBox `PDTextField` /
    `PDCheckBox`). On Android and iOS they render as consistent **static visuals** —
    a bordered box / a checkbox square — because the native PDF generators expose
    no AcroForm API. Field-name collisions get a numeric suffix (`-2`, `-3`, …)
    from the backend.

## Encryption

`encryption { … }` password-protects the document with permission flags:

```kotlin
pdf {
    encryption {
        ownerPassword = "owner"          // required, non-empty
        userPassword = "user"            // empty = opens without a prompt, flags still apply
        allowPrinting = false
        allowCopying = false
        allowModification = false
    }
    page { /* … */ }
}
```

!!! warning "Encryption support"
    - **Desktop / JVM** — full, AES-256.
    - **iOS** — owner / user passwords plus printing & copying are honoured via
      Core Graphics keys, but there is no Core Graphics flag for "allow
      modification" so `allowModification` is ignored.
    - **Android** — a **no-op** (`PdfDocument` has no encryption API). Encrypt
      out-of-band if you target Android.
    - **Web (Wasm)** — not supported (warned via `PdfLog`, skipped).

## File attachments

`attachment(fileName, bytes, mimeType)` embeds a file — the canonical use is a
ZUGFeRD / Factur-X invoice carrying its structured `factur-x.xml`:

```kotlin
attachment("factur-x.xml", invoiceXmlBytes, mimeType = "application/xml")
```

!!! warning "Desktop / JVM only"
    Registered in the catalog's embedded-files name tree on JVM. Silently skipped
    on Android, iOS, and Web.

## Best-effort PDF/A & tagged-PDF

Request best-effort PDF/A-2b via `pdfA(true)` (or
`metadata { pdfACompliance = true }`) and set a `language` for accessibility:

```kotlin
pdf {
    metadata { language = "en"; pdfACompliance = true }
    page { /* … */ }
}
```

!!! note "Honest scope"
    This is **best-effort** — XMP id + sRGB output intent + document-info
    alignment + `MarkInfo /Marked` + image `/Alt` entries. Full veraPDF
    conformance is **not** guaranteed. **JVM / Desktop only**; Android and iOS
    ignore it.

## Digital signing (`PdfSigner`)

`PdfSigner` (a **JVM-only** API in `jvmMain`) signs finished PDF bytes with an
incremental update so the signed byte range stays valid. The recommended path
takes a CMS callback (key stays in your HSM / signing service); a convenience
overload builds the signature from a `KeyStore` using BouncyCastle (a
`compileOnly` dependency — add `org.bouncycastle:bcpkix-jdk18on` to your runtime
classpath to use it):

```kotlin
import com.conamobile.pdfkmp.sign.PdfSigner

val signed = PdfSigner.sign(pdfBytes, name = "Jane Doe", reason = "Approved") { content ->
    myCmsService.signDetached(content)   // return DER-encoded CMS/PKCS#7 SignedData
}
// or, with a key store on the classpath:
val signed2 = PdfSigner.sign(pdfBytes, keyStore, alias = "mykey", password = pwd.toCharArray())
```

## `PdfTools` (JVM/Desktop) — post-processing

`PdfTools` is a **JVM/Desktop-only** object (in `jvmMain`, backed by PdfBox) that
operates on **already-encoded PDF bytes** — from PdfKmp or any other producer.
There is no Android, iOS, or Web counterpart: those platforms expose only
PDF-*writing* APIs, while merging / splitting / stamping needs a full
parser/manipulator. Every method reads the input, operates in memory, and returns
fresh bytes — inputs are never mutated.

| Method | What it does |
|---|---|
| `merge(vararg documents)` | Concatenate PDFs in argument order. |
| `split(pdf)` | One single-page PDF per page (`List<ByteArray>`). |
| `extractPages(pdf, range)` | Pull a 1-based inclusive page `range` (e.g. `2..4`) into a new PDF. |
| `addWatermarkText(pdf, text, …)` | Stamp a diagonal, semi-transparent watermark across every page. |
| `overlay(pdf, overlayPdf)` | Draw the overlay's first page in front of every base page (letterhead / frame). |
| `validatePdfABasics(pdf)` | Quick heuristic self-check; returns human-readable findings (empty = none found). |
| `attachFacturX(pdf, invoice)` | Embed a Factur-X `factur-x.xml` (see below). |

```kotlin
import com.conamobile.pdfkmp.tools.PdfTools
import com.conamobile.pdfkmp.zugferd.FacturXInvoice

// Merge a cover + body, then stamp a watermark across the result:
val merged = PdfTools.merge(coverBytes, bodyBytes)
val stamped = PdfTools.addWatermarkText(merged, "CONFIDENTIAL")   // 96pt, 45°, light gray, 30% alpha

// Attach a Factur-X / ZUGFeRD MINIMUM-profile invoice to a human-readable PDF:
val invoice = FacturXInvoice(
    invoiceNumber = "INV-2026-00042",
    issueDateYyyymmdd = "20260607",
    sellerName = "Acme GmbH",
    buyerName = "Beispiel AG",
    currencyCode = "EUR",
    taxBasisTotal = "100.00",
    taxTotal = "19.00",
    grandTotal = "119.00",
    duePayable = "119.00",
    sellerVatId = "DE123456789",
    sellerCountryCode = "DE",
)
val facturX = PdfTools.attachFacturX(stamped, invoice)
```

### Factur-X / ZUGFeRD

`FacturXInvoice` is a **common-code** data model for the Factur-X / ZUGFeRD EN 16931
**MINIMUM** profile — the smallest level a `factur-x.xml` may declare (parties,
document identity, currency, four monetary totals). `invoice.toXml()` builds the
`CrossIndustryInvoice` XML; `PdfTools.attachFacturX(pdf, invoice)` (JVM-only)
embeds it under the catalog's embedded-files name tree with the file name
`factur-x.xml`, MIME `text/xml`, and the spec-mandated `/AFRelationship /Data`.

!!! warning "Honest scope"
    The MINIMUM profile has no line items or payment terms, and the builder
    performs **no** semantic validation (it will happily emit totals that don't add
    up). `attachFacturX` only attaches the XML — it does **not** add or verify the
    PDF/A conformance Factur-X also requires. For a conformant document, produce the
    base PDF with PDF/A enabled and validate the result with a real Factur-X
    validator (Mustangproject, the FNFE-MPE checker, or veraPDF).

## Diagnostics — `PdfLog`

PdfKmp handles a handful of conditions gracefully but silently: undecodable image
bytes, a missing glyph falling back to the default font, an encryption/attachment
request on a backend that can't honour it. Install a `PdfLog` logger during
development to surface those "the document still renders, but not the way you
meant" warnings:

```kotlin
import com.conamobile.pdfkmp.PdfLog

PdfLog.logger = { message -> println("PdfKmp: $message") }
```

`PdfLog.logger` is a plain `((String) -> Unit)?` — `null` (the default) disables
logging entirely, so the library never writes to your console uninvited. Set it
once at startup; clear it (`PdfLog.logger = null`) for release builds.

## See also

- [Platform parity](platform-parity.md) — the per-platform feature matrix.
- [Pages, headers & watermarks](page-setup.md) — the `metadata { }` block.
- `Samples.formsAndAccessibility()`.
