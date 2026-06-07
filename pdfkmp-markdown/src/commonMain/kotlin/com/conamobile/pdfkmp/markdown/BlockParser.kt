package com.conamobile.pdfkmp.markdown

/**
 * A parsed top-level Markdown block.
 *
 * The block parser is line-based and dependency-free: it groups raw lines into
 * these variants, which the renderer then maps onto the PdfKmp DSL. Inline
 * styling (`**bold**`, links, …) is *not* resolved here — that is deferred to
 * [InlineParser] at render time so the block tree stays a faithful, testable
 * representation of the document structure.
 */
internal sealed interface MarkdownBlock {

    /** An ATX heading. [level] is 1..6 (`#`..`######`). */
    data class Heading(val level: Int, val text: String) : MarkdownBlock

    /** A run of non-blank lines forming one paragraph; soft-wrapped on render. */
    data class Paragraph(val text: String) : MarkdownBlock

    /** A bullet (`- ` / `* `) or ordered (`1. `) list. */
    data class ListBlock(val ordered: Boolean, val items: List<String>) : MarkdownBlock

    /** A fenced code block (```), with its lines joined by `\n`. */
    data class CodeBlock(val code: String) : MarkdownBlock

    /** A `> ` blockquote; [lines] are the quoted lines with the marker stripped. */
    data class BlockQuote(val lines: List<String>) : MarkdownBlock

    /** A `---` / `***` / `___` horizontal rule. */
    data object HorizontalRule : MarkdownBlock

    /**
     * A GitHub-style pipe table. [header] holds the header cells and [rows]
     * each body row's cells; every row is normalised to [header].size columns.
     */
    data class Table(val header: List<String>, val rows: List<List<String>>) : MarkdownBlock
}

/**
 * Line-based parser turning raw Markdown text into a list of [MarkdownBlock].
 *
 * It implements a deliberately small CommonMark subset (see [markdown] for the
 * exact feature list) and never throws — anything it does not recognise is
 * emitted as a [MarkdownBlock.Paragraph], so unknown syntax degrades to plain
 * text rather than failing the render.
 */
internal object BlockParser {

    private val HEADING = Regex("^(#{1,6})\\s+(.*)$")
    private val UNORDERED = Regex("^\\s*[-*]\\s+(.*)$")
    private val ORDERED = Regex("^\\s*\\d+\\.\\s+(.*)$")
    private val FENCE = Regex("^\\s*```.*$")
    private val HRULE = Regex("^\\s*([-*_])(\\s*\\1){2,}\\s*$")
    private val BLOCKQUOTE = Regex("^\\s*>\\s?(.*)$")

    /** Parses [source] into block list. Splits CRLF/CR to LF first. */
    fun parse(source: String): List<MarkdownBlock> {
        val lines = source.replace("\r\n", "\n").replace("\r", "\n").split("\n")
        val blocks = mutableListOf<MarkdownBlock>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]

            // Blank line — skip; blocks are blank-line (or syntax) separated.
            if (line.isBlank()) {
                i++
                continue
            }

            // Fenced code block — capture verbatim until the closing fence.
            if (FENCE.matches(line)) {
                val code = StringBuilder()
                i++
                while (i < lines.size && !FENCE.matches(lines[i])) {
                    if (code.isNotEmpty()) code.append('\n')
                    code.append(lines[i])
                    i++
                }
                if (i < lines.size) i++ // consume the closing fence
                blocks += MarkdownBlock.CodeBlock(code.toString())
                continue
            }

            // Horizontal rule.
            if (HRULE.matches(line)) {
                blocks += MarkdownBlock.HorizontalRule
                i++
                continue
            }

            // Heading.
            val headingMatch = HEADING.matchEntire(line)
            if (headingMatch != null) {
                blocks += MarkdownBlock.Heading(
                    level = headingMatch.groupValues[1].length,
                    text = headingMatch.groupValues[2].trim(),
                )
                i++
                continue
            }

            // Table — a header row containing a pipe immediately followed by a
            // separator row of dashes (`|---|---|`).
            if (line.contains('|') && i + 1 < lines.size && isTableSeparator(lines[i + 1])) {
                val header = splitTableRow(line)
                i += 2 // header + separator
                val rows = mutableListOf<List<String>>()
                while (i < lines.size && lines[i].contains('|') && lines[i].isNotBlank()) {
                    rows += normaliseRow(splitTableRow(lines[i]), header.size)
                    i++
                }
                blocks += MarkdownBlock.Table(header = header, rows = rows)
                continue
            }

            // Blockquote — consecutive `>`-prefixed lines.
            if (BLOCKQUOTE.matches(line)) {
                val quoted = mutableListOf<String>()
                while (i < lines.size && BLOCKQUOTE.matches(lines[i])) {
                    quoted += BLOCKQUOTE.matchEntire(lines[i])!!.groupValues[1]
                    i++
                }
                blocks += MarkdownBlock.BlockQuote(quoted)
                continue
            }

            // Lists — consecutive items of the same kind.
            if (UNORDERED.matches(line) || ORDERED.matches(line)) {
                val ordered = ORDERED.matches(line) && !UNORDERED.matches(line)
                val items = mutableListOf<String>()
                while (i < lines.size) {
                    val l = lines[i]
                    val match = if (ordered) ORDERED.matchEntire(l) else UNORDERED.matchEntire(l)
                    if (match == null) break
                    items += match.groupValues[1].trim()
                    i++
                }
                blocks += MarkdownBlock.ListBlock(ordered = ordered, items = items)
                continue
            }

            // Default — a paragraph runs until a blank line or a line that
            // starts a different block kind.
            val para = StringBuilder()
            while (i < lines.size && lines[i].isNotBlank() && isParagraphLine(lines[i])) {
                if (para.isNotEmpty()) para.append(' ')
                para.append(lines[i].trim())
                i++
            }
            // Guard against zero-progress (shouldn't happen, but never loop).
            if (para.isEmpty()) {
                blocks += MarkdownBlock.Paragraph(line.trim())
                i++
            } else {
                blocks += MarkdownBlock.Paragraph(para.toString())
            }
        }
        return blocks
    }

    /** True when [line] could be folded into the current running paragraph. */
    private fun isParagraphLine(line: String): Boolean =
        !FENCE.matches(line) &&
            !HRULE.matches(line) &&
            !HEADING.matches(line) &&
            !BLOCKQUOTE.matches(line) &&
            !UNORDERED.matches(line) &&
            !ORDERED.matches(line)

    /** A GitHub table separator row: pipes and dashes only, e.g. `|---|:--:|`. */
    private fun isTableSeparator(line: String): Boolean {
        val trimmed = line.trim()
        if (!trimmed.contains('-')) return false
        return trimmed.all { it == '|' || it == '-' || it == ':' || it == ' ' }
    }

    /** Splits a `| a | b |` row into trimmed cell strings. */
    internal fun splitTableRow(line: String): List<String> =
        line.trim()
            .removePrefix("|")
            .removeSuffix("|")
            .split('|')
            .map { it.trim() }

    /** Pads / trims [cells] to exactly [size] columns. */
    private fun normaliseRow(cells: List<String>, size: Int): List<String> = when {
        cells.size == size -> cells
        cells.size < size -> cells + List(size - cells.size) { "" }
        else -> cells.take(size)
    }
}
