package com.conamobile.pdfkmp.markdown

import com.conamobile.pdfkmp.pdf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * End-to-end and parser-level tests for the Markdown module.
 *
 * The core module's `FakePdfDriverFactory` test fixture is not visible here
 * (it lives in `:pdfkmp`'s own `commonTest`), so the end-to-end checks build a
 * real document via [pdf] — on JVM that exercises the PdfBox backend — and
 * assert the `%PDF-` magic bytes. Parser internals are reachable because
 * `internal` declarations are visible to this module's own test source set.
 */
class MarkdownTest {

    // ---------------------------------------------------------------------
    // End-to-end: the public DSL produces a real PDF.
    // ---------------------------------------------------------------------

    private fun pdfBytes(source: String): ByteArray =
        pdf { page { markdown(source) } }.toByteArray()

    private fun assertPdf(bytes: ByteArray) {
        val magic = bytes.copyOfRange(0, 5).decodeToString()
        assertEquals("%PDF-", magic, "output should start with the PDF magic bytes")
    }

    @Test
    fun rendersSimpleDocumentToPdf() {
        assertPdf(pdfBytes("# Hi\n\nSome **bold** text"))
    }

    @Test
    fun rendersEveryBlockKindToPdf() {
        val source = """
            # Heading 1
            ## Heading 2

            A paragraph with **bold**, *italic*, `code`, ~~strike~~ and a
            [link](https://example.com) inside it.

            - first
            - **second**
            - third

            1. one
            2. two

            > a quote
            > second line

            ```
            val x = 1
            fun main() { }
            ```

            ---

            | Name | Score |
            |------|-------|
            | Ann  | 10    |
            | Bob  | 20    |

            [standalone](https://example.org)
        """.trimIndent()
        assertPdf(pdfBytes(source))
    }

    @Test
    fun emptyAndUnknownSyntaxDoesNotThrow() {
        assertPdf(pdfBytes(""))
        assertPdf(pdfBytes("just plain text with no markup at all"))
        assertPdf(pdfBytes("[broken link(no closing paren"))
        assertPdf(pdfBytes("**unterminated bold"))
    }

    // ---------------------------------------------------------------------
    // Block parser.
    // ---------------------------------------------------------------------

    @Test
    fun headingsParseWithCorrectLevel() {
        val blocks = BlockParser.parse("# One\n## Two\n###### Six")
        assertEquals(3, blocks.size)
        val h1 = blocks[0] as MarkdownBlock.Heading
        val h2 = blocks[1] as MarkdownBlock.Heading
        val h6 = blocks[2] as MarkdownBlock.Heading
        assertEquals(1, h1.level)
        assertEquals("One", h1.text)
        assertEquals(2, h2.level)
        assertEquals(6, h6.level)
        assertEquals("Six", h6.text)
    }

    @Test
    fun sevenHashesIsNotAHeading() {
        // ATX headings cap at six; #######... degrades to a paragraph.
        val blocks = BlockParser.parse("####### too many")
        assertTrue(blocks.single() is MarkdownBlock.Paragraph)
    }

    @Test
    fun unorderedListGroupsItems() {
        val blocks = BlockParser.parse("- a\n- b\n* c")
        val list = blocks.single() as MarkdownBlock.ListBlock
        assertFalse(list.ordered)
        assertEquals(listOf("a", "b", "c"), list.items)
    }

    @Test
    fun orderedListGroupsItems() {
        val blocks = BlockParser.parse("1. first\n2. second\n3. third")
        val list = blocks.single() as MarkdownBlock.ListBlock
        assertTrue(list.ordered)
        assertEquals(listOf("first", "second", "third"), list.items)
    }

    @Test
    fun fencedCodeCapturesContentVerbatim() {
        val blocks = BlockParser.parse("```\nline 1\n  indented\n```")
        val code = blocks.single() as MarkdownBlock.CodeBlock
        assertEquals("line 1\n  indented", code.code)
    }

    @Test
    fun fencedCodeDoesNotInterpretInnerMarkers() {
        val blocks = BlockParser.parse("```\n# not a heading\n- not a list\n```")
        val code = blocks.single() as MarkdownBlock.CodeBlock
        assertEquals("# not a heading\n- not a list", code.code)
    }

    @Test
    fun blockquoteStripsMarker() {
        val blocks = BlockParser.parse("> quoted\n> two")
        val quote = blocks.single() as MarkdownBlock.BlockQuote
        assertEquals(listOf("quoted", "two"), quote.lines)
    }

    @Test
    fun horizontalRuleVariants() {
        assertTrue(BlockParser.parse("---").single() is MarkdownBlock.HorizontalRule)
        assertTrue(BlockParser.parse("***").single() is MarkdownBlock.HorizontalRule)
        assertTrue(BlockParser.parse("___").single() is MarkdownBlock.HorizontalRule)
    }

    @Test
    fun tableRowsSplitCorrectly() {
        val source = "| a | b | c |\n|---|---|---|\n| 1 | 2 | 3 |\n| x | y | z |"
        val table = BlockParser.parse(source).single() as MarkdownBlock.Table
        assertEquals(listOf("a", "b", "c"), table.header)
        assertEquals(2, table.rows.size)
        assertEquals(listOf("1", "2", "3"), table.rows[0])
        assertEquals(listOf("x", "y", "z"), table.rows[1])
    }

    @Test
    fun tableShortRowIsPaddedToHeaderWidth() {
        val source = "| a | b | c |\n|---|---|---|\n| 1 | 2 |"
        val table = BlockParser.parse(source).single() as MarkdownBlock.Table
        assertEquals(listOf("1", "2", ""), table.rows.single())
    }

    @Test
    fun paragraphFoldsSoftWrappedLines() {
        val blocks = BlockParser.parse("line one\nline two\n\nnext block")
        assertEquals(2, blocks.size)
        assertEquals("line one line two", (blocks[0] as MarkdownBlock.Paragraph).text)
        assertEquals("next block", (blocks[1] as MarkdownBlock.Paragraph).text)
    }

    // ---------------------------------------------------------------------
    // Inline tokenizer.
    // ---------------------------------------------------------------------

    @Test
    fun inlineEmitsPlainText() {
        val spans = InlineParser.parse("hello world")
        assertEquals(1, spans.size)
        assertEquals("hello world", spans[0].text)
        assertEquals(InlineStyleFlags(), spans[0].flags)
    }

    @Test
    fun inlineBoldItalicCode() {
        val spans = InlineParser.parse("a **b** c *d* e `f`")
        // Expected: "a " | "b"(bold) | " c " | "d"(italic) | " e " | "f"(code)
        assertEquals("a ", spans[0].text)
        assertEquals(InlineStyleFlags(), spans[0].flags)

        assertEquals("b", spans[1].text)
        assertTrue(spans[1].flags.bold)

        assertEquals(" c ", spans[2].text)

        assertEquals("d", spans[3].text)
        assertTrue(spans[3].flags.italic)

        assertEquals(" e ", spans[4].text)

        assertEquals("f", spans[5].text)
        assertTrue(spans[5].flags.code)
    }

    @Test
    fun inlineUnderscoreItalicAndStrike() {
        val spans = InlineParser.parse("_em_ and ~~gone~~")
        assertEquals("em", spans[0].text)
        assertTrue(spans[0].flags.italic)
        val strike = spans.first { it.flags.strikethrough }
        assertEquals("gone", strike.text)
    }

    @Test
    fun inlineTripleStarIsBoldItalic() {
        val spans = InlineParser.parse("***x***")
        assertEquals("x", spans.single().text)
        assertTrue(spans.single().flags.bold)
        assertTrue(spans.single().flags.italic)
    }

    @Test
    fun inlineLinkCarriesUrl() {
        val spans = InlineParser.parse("see [docs](https://example.com) now")
        val link = spans.first { it.flags.link != null }
        assertEquals("docs", link.text)
        assertEquals("https://example.com", link.flags.link)
        // Surrounding text is plain.
        assertEquals("see ", spans[0].text)
        assertNull(spans[0].flags.link)
    }

    @Test
    fun unterminatedMarkersDegradeToLiteral() {
        // A bold opener with no closer toggles bold on for the rest of the run;
        // the key contract is simply that parsing does not throw and preserves
        // all the visible characters.
        val spans = InlineParser.parse("**oops")
        val text = spans.joinToString("") { it.text }
        assertEquals("oops", text)

        val broken = InlineParser.parse("[label](no-close")
        // No matching ")", so the bracket is literal.
        assertEquals("[label](no-close", broken.joinToString("") { it.text })
    }
}
