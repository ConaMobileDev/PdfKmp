package com.conamobile.pdfkmp.zugferd

/**
 * Minimal ZUGFeRD / Factur-X invoice carrying the fields of the EN 16931
 * **MINIMUM** profile — the smallest conformance level a Factur-X
 * `factur-x.xml` may declare.
 *
 * The MINIMUM profile is deliberately tiny: it covers the parties, the
 * document identity, the currency, and the four monetary totals a tax
 * authority needs to recognise the document as an invoice. It is **not**
 * sufficient for fully automated booking (no line items, no payment terms) —
 * for those, model the richer BASIC / EN 16931 / EXTENDED profiles.
 *
 * Build the XML with [toXml] and embed it next to the human-readable pages
 * with `PdfTools.attachFacturX` (JVM/Desktop) so a single PDF carries both the
 * visual invoice and its machine-readable companion.
 *
 * **Honest scope:** this models the MINIMUM profile only and performs no
 * semantic validation (it will happily emit totals that do not add up). Run the
 * output through a Factur-X validator (e.g. the Mustangproject validator or the
 * FNFE-MPE online checker) before relying on it in production.
 *
 * Monetary amounts are passed as already-formatted decimal strings (e.g.
 * `"100.00"`) so the caller controls rounding and decimal separator — the XML
 * requires a `.`-separated decimal with no thousands separator.
 *
 * @property invoiceNumber the invoice identifier (`ram:ID`). Required.
 * @property typeCode the UNTDID 1001 document type code; defaults to `"380"`
 *   (commercial invoice). `"381"` is a credit note.
 * @property issueDateYyyymmdd the issue date as a `yyyyMMdd` string (the
 *   format MINIMUM mandates, `format="102"`), e.g. `"20260607"`.
 * @property sellerName the seller's trading name (`ram:SellerTradeParty`).
 * @property sellerVatId the seller's VAT registration id (`schemeID="VA"`);
 *   `null` omits the VAT registration element.
 * @property sellerCountryCode the seller's ISO 3166-1 alpha-2 country code,
 *   e.g. `"DE"`.
 * @property buyerName the buyer's trading name (`ram:BuyerTradeParty`).
 * @property currencyCode the invoice currency as an ISO 4217 code, e.g.
 *   `"EUR"`.
 * @property taxBasisTotal the sum of all line net amounts before tax
 *   (`ram:TaxBasisTotalAmount`), as a decimal string.
 * @property taxTotal the total tax amount (`ram:TaxTotalAmount`), as a decimal
 *   string. MINIMUM requires the currency on this element specifically.
 * @property grandTotal the invoice total including tax
 *   (`ram:GrandTotalAmount`), as a decimal string.
 * @property duePayable the amount actually due for payment
 *   (`ram:DuePayableAmount`), as a decimal string — usually equal to
 *   [grandTotal] unless a prepayment was made.
 */
public data class FacturXInvoice(
    val invoiceNumber: String,
    val issueDateYyyymmdd: String,
    val sellerName: String,
    val buyerName: String,
    val currencyCode: String,
    val taxBasisTotal: String,
    val taxTotal: String,
    val grandTotal: String,
    val duePayable: String,
    val typeCode: String = "380",
    val sellerVatId: String? = null,
    val sellerCountryCode: String? = null,
)

/**
 * Builds the `CrossIndustryInvoice` XML for this invoice, declaring the
 * Factur-X **MINIMUM** profile (`urn:factur-x.eu:1p0:minimum`).
 *
 * The result is a self-contained UTF-8 XML string with the four namespaces the
 * MINIMUM profile uses (`rsm`, `ram`, `qdt`, `udt`) and every value escaped for
 * XML text content. It is suitable for embedding verbatim as `factur-x.xml`.
 *
 * Only MINIMUM-profile elements are emitted; there are no line items. See the
 * [FacturXInvoice] class KDoc for the validation caveat.
 */
public fun FacturXInvoice.toXml(): String {
    val sellerVatXml = sellerVatId?.let {
        """
            <ram:SpecifiedTaxRegistration>
              <ram:ID schemeID="VA">${xmlEscape(it)}</ram:ID>
            </ram:SpecifiedTaxRegistration>
        """.trimIndent().prependIndentLines("        ")
    } ?: ""

    val sellerCountryXml = sellerCountryCode?.let {
        """
            <ram:PostalTradeAddress>
              <ram:CountryID>${xmlEscape(it)}</ram:CountryID>
            </ram:PostalTradeAddress>
        """.trimIndent().prependIndentLines("        ")
    } ?: ""

    return """
        <?xml version="1.0" encoding="UTF-8"?>
        <rsm:CrossIndustryInvoice
            xmlns:rsm="urn:un:unece:uncefact:data:standard:CrossIndustryInvoice:100"
            xmlns:ram="urn:un:unece:uncefact:data:standard:ReusableAggregateBusinessInformationEntity:100"
            xmlns:qdt="urn:un:unece:uncefact:data:standard:QualifiedDataType:100"
            xmlns:udt="urn:un:unece:uncefact:data:standard:UnqualifiedDataType:100">
          <rsm:ExchangedDocumentContext>
            <ram:GuidelineSpecifiedDocumentContextParameter>
              <ram:ID>urn:factur-x.eu:1p0:minimum</ram:ID>
            </ram:GuidelineSpecifiedDocumentContextParameter>
          </rsm:ExchangedDocumentContext>
          <rsm:ExchangedDocument>
            <ram:ID>${xmlEscape(invoiceNumber)}</ram:ID>
            <ram:TypeCode>${xmlEscape(typeCode)}</ram:TypeCode>
            <ram:IssueDateTime>
              <udt:DateTimeString format="102">${xmlEscape(issueDateYyyymmdd)}</udt:DateTimeString>
            </ram:IssueDateTime>
          </rsm:ExchangedDocument>
          <rsm:SupplyChainTradeTransaction>
            <ram:ApplicableHeaderTradeAgreement>
              <ram:SellerTradeParty>
                <ram:Name>${xmlEscape(sellerName)}</ram:Name>
$sellerCountryXml
$sellerVatXml
              </ram:SellerTradeParty>
              <ram:BuyerTradeParty>
                <ram:Name>${xmlEscape(buyerName)}</ram:Name>
              </ram:BuyerTradeParty>
            </ram:ApplicableHeaderTradeAgreement>
            <ram:ApplicableHeaderTradeDelivery/>
            <ram:ApplicableHeaderTradeSettlement>
              <ram:InvoiceCurrencyCode>${xmlEscape(currencyCode)}</ram:InvoiceCurrencyCode>
              <ram:SpecifiedTradeSettlementHeaderMonetarySummation>
                <ram:TaxBasisTotalAmount>${xmlEscape(taxBasisTotal)}</ram:TaxBasisTotalAmount>
                <ram:TaxTotalAmount currencyID="${xmlEscape(currencyCode)}">${xmlEscape(taxTotal)}</ram:TaxTotalAmount>
                <ram:GrandTotalAmount>${xmlEscape(grandTotal)}</ram:GrandTotalAmount>
                <ram:DuePayableAmount>${xmlEscape(duePayable)}</ram:DuePayableAmount>
              </ram:SpecifiedTradeSettlementHeaderMonetarySummation>
            </ram:ApplicableHeaderTradeSettlement>
          </rsm:SupplyChainTradeTransaction>
        </rsm:CrossIndustryInvoice>
    """.trimIndent().lineSequence()
        // Drop the blank lines left where an optional block was omitted, so the
        // output stays clean whether or not VAT id / country were supplied.
        .filter { it.isNotBlank() }
        .joinToString("\n")
}

/**
 * Escapes [value] for inclusion in XML text content / attribute values.
 * `&` is replaced first so the entity ampersands introduced afterwards are not
 * double-escaped.
 */
private fun xmlEscape(value: String): String = value
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&apos;")

/** Prefixes every non-blank line of this string with [indent]. */
private fun String.prependIndentLines(indent: String): String =
    lineSequence().joinToString("\n") { if (it.isBlank()) it else indent + it }
