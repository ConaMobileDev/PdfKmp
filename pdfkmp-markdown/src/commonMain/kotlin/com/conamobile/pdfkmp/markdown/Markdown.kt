package com.conamobile.pdfkmp.markdown

import com.conamobile.pdfkmp.PdfUrls
import com.conamobile.pdfkmp.dsl.ContainerScope
import com.conamobile.pdfkmp.dsl.TextScope
import com.conamobile.pdfkmp.geometry.Padding
import com.conamobile.pdfkmp.layout.BoxAlignment
import com.conamobile.pdfkmp.layout.VerticalAlignment
import com.conamobile.pdfkmp.style.BorderSides
import com.conamobile.pdfkmp.style.BorderStroke
import com.conamobile.pdfkmp.style.FontWeight
import com.conamobile.pdfkmp.style.PdfColor
import com.conamobile.pdfkmp.style.TableBorder
import com.conamobile.pdfkmp.style.TableColumn
import com.conamobile.pdfkmp.style.TextStyle
import com.conamobile.pdfkmp.unit.Dp
import com.conamobile.pdfkmp.unit.Sp

/**
 * Visual configuration for [markdown] rendering.
 *
 * Every block kind derives its look from this theme so a document can be
 * restyled in one place. Defaults aim at a clean, print-friendly look.
 *
 * @property baseTextStyle style applied to body text; headings scale its
 *   [TextStyle.fontSize] by the matching [headingScales] multiplier, and all
 *   other blocks inherit its colour, font, and size.
 * @property codeBackground fill behind fenced and inline code.
 * @property linkColor colour used for link text (and the underline drawn under
 *   inline links).
 * @property headingScales six multipliers applied to [baseTextStyle]'s font
 *   size for `h1`..`h6`. Shorter lists fall back to `1f` for missing levels.
 * @property blockSpacing vertical gap inserted between consecutive blocks.
 */
public data class MarkdownTheme(
    val baseTextStyle: TextStyle = TextStyle(),
    val codeBackground: PdfColor = PdfColor(0.95f, 0.95f, 0.95f),
    val linkColor: PdfColor = PdfColor.Blue,
    val headingScales: List<Float> = listOf(2.0f, 1.6f, 1.3f, 1.15f, 1.05f, 1.0f),
    val blockSpacing: Dp = Dp(8f),
)

/**
 * Renders [markdown] into this container through the PdfKmp DSL.
 *
 * Supported CommonMark-lite subset (everything else degrades to plain
 * paragraph text — the renderer never throws):
 *
 * - **Headings** — ATX `#`..`######`, scaled per [MarkdownTheme.headingScales].
 * - **Paragraphs** with inline `**bold**`, `*italic*` / `_italic_`,
 *   `***bold italic***`, `` `code` ``, `~~strikethrough~~`, and
 *   `[text](url)` links.
 * - **Lists** — `- ` / `* ` unordered and `1. ` ordered.
 * - **Fenced code blocks** (```), rendered in a [MarkdownTheme.codeBackground]
 *   card.
 * - **Blockquotes** (`> `), rendered as a left-bordered grey column.
 * - **Horizontal rules** (`---` / `***` / `___`).
 * - **GitHub pipe tables** (`| a | b |` + a `|---|---|` separator row).
 *
 * ### Documented limitations
 * - **Links inside a sentence are not clickable.** Standalone links — a
 *   paragraph whose entire content is a single `[text](url)` — render through
 *   the core `link(url) { … }` DSL and are clickable in viewers that support
 *   link annotations. A link that appears *within* running text is styled
 *   coloured + underlined via a rich-text span but has **no** clickable area,
 *   because the inline layout cannot carry a per-span link rectangle.
 * - **Only allowlisted schemes are styled as links.** Targets a PDF annotation
 *   cannot carry — `#anchor`, `./other.md`, a bare `www.` domain, anything
 *   outside [com.conamobile.pdfkmp.PdfUrls.allowedSchemes] — render as plain
 *   body text: no annotation, no link colour, no underline. The rule is the
 *   same in both positions, so one target never looks clickable in a sentence
 *   and plain on a line of its own.
 * - **Code has no guaranteed monospace font.** PdfKmp does not bundle a
 *   monospace face, so code is rendered in the base font at a slightly smaller
 *   size on the [MarkdownTheme.codeBackground]; alignment of code is therefore
 *   approximate, not column-accurate.
 * - **List items support inline styling.** Items are hand-rolled as rows (a
 *   marker box + a rich-text body) rather than via `bulletList` / `numberedList`
 *   so `- **bold** item` keeps its emphasis.
 *
 * @param markdown the Markdown source text.
 * @param theme visual configuration; see [MarkdownTheme].
 */
public fun ContainerScope.markdown(markdown: String, theme: MarkdownTheme = MarkdownTheme()) {
    val blocks = BlockParser.parse(markdown)
    MarkdownRenderer(theme).render(this, blocks)
}

/**
 * Internal walker that maps parsed [MarkdownBlock]s onto the PdfKmp DSL.
 *
 * Kept separate from the public surface so the parser (testable in isolation)
 * and the rendering (which needs the DSL receiver) stay decoupled.
 */
/**
 * Whether a markdown link target can become a real PDF annotation — and so
 * whether it should be painted to look clickable.
 *
 * Both render paths consult this one predicate. They used to decide
 * separately, and drifted: the standalone-link path honoured the scheme
 * allowlist while the inline-span path styled every target blue and
 * underlined, so the same `[docs](#anchor)` rendered as plain body text on a
 * line of its own and as clickable-looking text inside a sentence.
 */
internal fun isClickableTarget(url: String?): Boolean =
    url != null && PdfUrls.isSafeExternalUrl(url)

internal class MarkdownRenderer(private val theme: MarkdownTheme) {

    private val base: TextStyle = theme.baseTextStyle

    /** Renders [blocks] into [scope], inserting [MarkdownTheme.blockSpacing] between them. */
    fun render(scope: ContainerScope, blocks: List<MarkdownBlock>) {
        scope.column(spacing = theme.blockSpacing) {
            for (block in blocks) {
                when (block) {
                    is MarkdownBlock.Heading -> renderHeading(block)
                    is MarkdownBlock.Paragraph -> renderParagraph(block.text)
                    is MarkdownBlock.ListBlock -> renderList(block)
                    is MarkdownBlock.CodeBlock -> renderCode(block)
                    is MarkdownBlock.BlockQuote -> renderQuote(block)
                    MarkdownBlock.HorizontalRule -> divider()
                    is MarkdownBlock.Table -> renderTable(block)
                }
            }
        }
    }

    private fun ContainerScope.renderHeading(block: MarkdownBlock.Heading) {
        val scale = theme.headingScales.getOrElse(block.level - 1) { 1f }
        val style = base.copy(
            fontSize = Sp(base.fontSize.value * scale),
            fontWeight = FontWeight.Bold,
        )
        renderInline(block.text, style)
    }

    private fun ContainerScope.renderParagraph(text: String) {
        renderInline(text, base)
    }

    /**
     * Emits one paragraph from inline-styled [text].
     *
     * Fast path: a paragraph that is *only* a single `[label](url)` link is
     * rendered through the clickable `link(url) { … }` DSL. Anything else goes
     * through [richText] spans (inline links there are styled but not
     * clickable — see [markdown]).
     */
    private fun ContainerScope.renderInline(text: String, style: TextStyle) {
        val spans = InlineParser.parse(text)
        val onlyLink = spans.singleOrNull()?.takeIf { it.flags.link != null }
        if (onlyLink != null) {
            val url = onlyLink.flags.link!!
            // Markdown is full of link targets no PDF annotation can carry —
            // "#anchor", "./other.md", bare "www." domains. Calling link() with
            // one would only make it drop the annotation and log a warning, so
            // a whole document of internal cross-references floods the host's
            // logger with notices about links it never expected to be clickable.
            // Decide here instead, and style to match: no annotation, no link
            // colour, no underline.
            if (isClickableTarget(url)) {
                link(url) {
                    text(onlyLink.text) {
                        color = theme.linkColor
                        underline = true
                        applyFlags(onlyLink.flags)
                    }
                }
            } else {
                text(onlyLink.text) { applyFlags(onlyLink.flags) }
            }
            return
        }
        richText {
            defaultSpanStyle = style
            for (s in spans) {
                span(s.text) { applySpanFlags(s.flags) }
            }
        }
    }

    private fun ContainerScope.renderList(block: MarkdownBlock.ListBlock) {
        // Hand-rolled so items keep inline styling (the core bulletList /
        // numberedList take plain strings only).
        column(spacing = Dp(4f)) {
            block.items.forEachIndexed { index, item ->
                val marker = if (block.ordered) "${index + 1}." else "•"
                row(verticalAlignment = VerticalAlignment.Top) {
                    box(width = if (block.ordered) Dp(20f) else Dp(16f)) {
                        aligned(BoxAlignment.TopStart) {
                            text(marker) { color = base.color }
                        }
                    }
                    weighted(1f) {
                        renderInline(item, base)
                    }
                }
            }
        }
    }

    private fun ContainerScope.renderCode(block: MarkdownBlock.CodeBlock) {
        // No bundled monospace face — approximate code with a smaller font on
        // the theme's code background (documented on [markdown]).
        card(background = theme.codeBackground, cornerRadius = Dp(4f)) {
            for (line in block.code.split("\n")) {
                text(line.ifEmpty { " " }) {
                    fontSize = Sp(base.fontSize.value * 0.9f)
                    color = base.color
                }
            }
        }
    }

    private fun ContainerScope.renderQuote(block: MarkdownBlock.BlockQuote) {
        column(
            padding = Padding(
                left = Dp(12f),
                top = Dp(4f),
                right = Dp(4f),
                bottom = Dp(4f),
            ),
            borderEach = BorderSides(
                left = BorderStroke(width = Dp(3f), color = PdfColor.LightGray),
            ),
            spacing = Dp(2f),
        ) {
            val quoteStyle = base.copy(color = PdfColor.Gray)
            for (line in block.lines) {
                if (line.isBlank()) {
                    spacer(height = Dp(4f))
                } else {
                    renderInline(line, quoteStyle)
                }
            }
        }
    }

    private fun ContainerScope.renderTable(block: MarkdownBlock.Table) {
        val columnCount = block.header.size.coerceAtLeast(1)
        table(
            columns = List(columnCount) { TableColumn.Weight(1f) },
            border = TableBorder(),
        ) {
            header {
                for (cellText in block.header) {
                    cell {
                        renderInline(cellText, base.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }
            for (bodyRow in block.rows) {
                row {
                    for (cellText in bodyRow) {
                        cell { renderInline(cellText, base) }
                    }
                }
            }
        }
    }

    /**
     * Applies bold/italic/strike/code flags to a plain [text] scope (used by
     * the standalone-link fast path, which is not a rich-text span).
     */
    private fun TextScope.applyFlags(flags: InlineStyleFlags) {
        if (flags.bold) bold = true
        if (flags.italic) italic = true
        if (flags.strikethrough) strikethrough = true
    }

    /**
     * Applies all inline flags to a rich-text span scope, including the code
     * and inline-link visual treatment.
     */
    private fun TextScope.applySpanFlags(flags: InlineStyleFlags) {
        if (flags.bold) bold = true
        if (flags.italic) italic = true
        if (flags.strikethrough) strikethrough = true
        if (flags.code) {
            fontSize = Sp(fontSize.value * 0.9f)
        }
        // Inline links are styled but NOT clickable (documented on markdown()).
        // The styling still tracks the scheme allowlist: a target that could
        // never become an annotation must not be painted to look like one, or
        // the same "[docs](#anchor)" would render blue and underlined inside a
        // sentence and as plain body text on a line of its own.
        if (isClickableTarget(flags.link)) {
            color = theme.linkColor
            underline = true
        }
    }
}
