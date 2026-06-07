package com.conamobile.pdfkmp.markdown

/**
 * Inline-level style flags carried by a parsed [InlineSpan].
 *
 * Several flags can stack on the same run (e.g. bold *and* italic for
 * `***text***`, or a link rendered as coloured + underlined text), so this is
 * modelled as an immutable set of boolean toggles rather than an enum.
 *
 * @property bold rendered with [com.conamobile.pdfkmp.style.FontWeight.Bold].
 * @property italic rendered with [com.conamobile.pdfkmp.style.FontStyle.Italic].
 * @property code rendered in the inline-code style (background + smaller size).
 * @property strikethrough rendered with a line through the glyphs.
 * @property link non-null when the run is the visible text of a `[text](url)`
 *   link; carries the destination URL. Inline links inside a sentence are
 *   styled (coloured + underlined) but are **not** clickable — see the
 *   limitation documented on [markdown].
 */
internal data class InlineStyleFlags(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
    val strikethrough: Boolean = false,
    val link: String? = null,
)

/** One styled run produced by the inline tokenizer. */
internal data class InlineSpan(
    val text: String,
    val flags: InlineStyleFlags,
)

/**
 * Tiny hand-rolled tokenizer for CommonMark-lite inline syntax.
 *
 * Supported markers, applied left-to-right with best-effort nesting:
 * - `**bold**`
 * - `*italic*` and `_italic_`
 * - `***bold italic***`
 * - `` `code` `` (verbatim — no inner markers are interpreted)
 * - `~~strikethrough~~`
 * - `[text](url)` links
 *
 * The parser never throws: an unterminated marker is emitted as literal text,
 * which is exactly how a permissive Markdown renderer should degrade.
 */
internal object InlineParser {

    /**
     * Splits [input] into a flat list of styled [InlineSpan]s.
     *
     * The implementation is a single forward scan with a small style stack:
     * each opening marker pushes a flag, the matching closer pops it. When a
     * closer has no opener (or vice-versa) the marker characters fall through
     * as literal text, so malformed input is preserved verbatim.
     */
    fun parse(input: String): List<InlineSpan> {
        val spans = mutableListOf<InlineSpan>()
        val buffer = StringBuilder()
        var bold = false
        var italic = false
        var strike = false

        fun currentFlags() = InlineStyleFlags(bold = bold, italic = italic, strikethrough = strike)

        fun flush() {
            if (buffer.isNotEmpty()) {
                spans += InlineSpan(buffer.toString(), currentFlags())
                buffer.clear()
            }
        }

        var i = 0
        val n = input.length
        while (i < n) {
            val c = input[i]
            when {
                // Inline code: copy verbatim up to the next backtick. No inner
                // markers are interpreted inside a code span.
                c == '`' -> {
                    val end = input.indexOf('`', i + 1)
                    if (end == -1) {
                        buffer.append(c)
                        i++
                    } else {
                        flush()
                        spans += InlineSpan(
                            text = input.substring(i + 1, end),
                            flags = currentFlags().copy(code = true),
                        )
                        i = end + 1
                    }
                }

                // Link: [text](url). Falls through to literal if either bracket
                // pair is missing.
                c == '[' -> {
                    val parsed = parseLink(input, i)
                    if (parsed == null) {
                        buffer.append(c)
                        i++
                    } else {
                        flush()
                        // The link label may itself carry emphasis; recurse so
                        // `[**bold**](url)` keeps both the link and the bold.
                        val inner = parse(parsed.label)
                        for (span in inner) {
                            spans += span.copy(
                                flags = span.flags.copy(
                                    bold = span.flags.bold || bold,
                                    italic = span.flags.italic || italic,
                                    strikethrough = span.flags.strikethrough || strike,
                                    link = parsed.url,
                                ),
                            )
                        }
                        i = parsed.next
                    }
                }

                // ***bold italic***
                c == '*' && startsWith(input, i, "***") -> {
                    flush()
                    bold = !bold
                    italic = !italic
                    i += 3
                }

                // **bold**
                c == '*' && startsWith(input, i, "**") -> {
                    flush()
                    bold = !bold
                    i += 2
                }

                // *italic*
                c == '*' -> {
                    flush()
                    italic = !italic
                    i++
                }

                // _italic_
                c == '_' -> {
                    flush()
                    italic = !italic
                    i++
                }

                // ~~strikethrough~~
                c == '~' && startsWith(input, i, "~~") -> {
                    flush()
                    strike = !strike
                    i += 2
                }

                else -> {
                    buffer.append(c)
                    i++
                }
            }
        }
        flush()
        return spans
    }

    private data class ParsedLink(val label: String, val url: String, val next: Int)

    /**
     * Attempts to parse a `[label](url)` link starting at [start] (which must
     * point at the opening `[`). Returns `null` when the syntax does not match,
     * so the caller can fall back to treating `[` as a literal character.
     */
    private fun parseLink(input: String, start: Int): ParsedLink? {
        val labelEnd = input.indexOf(']', start + 1)
        if (labelEnd == -1 || labelEnd + 1 >= input.length || input[labelEnd + 1] != '(') return null
        val urlEnd = input.indexOf(')', labelEnd + 2)
        if (urlEnd == -1) return null
        val label = input.substring(start + 1, labelEnd)
        val url = input.substring(labelEnd + 2, urlEnd)
        return ParsedLink(label = label, url = url, next = urlEnd + 1)
    }

    private fun startsWith(input: String, index: Int, token: String): Boolean =
        input.regionMatches(index, token, 0, token.length)
}
