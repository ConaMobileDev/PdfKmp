package com.conamobile.pdfkmp.vector

/**
 * One element of an XML tree as parsed by [MiniXml].
 *
 * Only the bits that vector / SVG documents actually use are modelled —
 * processing instructions, DOCTYPEs, namespace declarations, and CDATA are
 * skipped silently. Attribute keys keep their namespace prefix
 * (`android:fillColor`); callers that don't care about it should match on
 * [localName] instead of [name].
 */
internal data class XmlElement(
    val name: String,
    val attributes: Map<String, String>,
    val children: List<XmlElement>,
) {
    /** Element name with any `prefix:` removed — handy for ignoring `android:` etc. */
    val localName: String
        get() = name.substringAfter(':')

    /**
     * Returns the value of the named attribute, looked up first by exact
     * [name] and then by any namespace-stripped sibling. Useful for vector
     * XML where the same attribute may be `android:fillColor` or `fill`
     * depending on the source format.
     */
    fun attribute(name: String): String? {
        attributes[name]?.let { return it }
        val suffix = ":$name"
        return attributes.entries.firstOrNull { it.key.endsWith(suffix) }?.value
    }
}

/**
 * Hand-rolled XML parser tuned for `<vector>` and `<svg>` documents.
 *
 * Supports: nested elements, attribute lists with single or double quotes,
 * self-closing tags, comments (`<!-- ... -->`), and the five core entity
 * references (`&lt;`, `&gt;`, `&amp;`, `&quot;`, `&apos;`).
 *
 * Does **not** support: DOCTYPEs (skipped), processing instructions
 * (skipped), CDATA, namespaces beyond keeping the prefix in the name,
 * arbitrary entities, or external DTDs. Vector and SVG icons in the wild
 * never need any of those.
 */
internal object MiniXml {

    fun parse(source: String): XmlElement {
        val cursor = Cursor(source)
        cursor.skipPrologueAndComments()
        val root = parseElement(cursor)
            ?: throw VectorParseException("Document has no root element")
        return root
    }

    private fun parseElement(cursor: Cursor): XmlElement? {
        cursor.skipWhitespace()
        if (!cursor.consume('<')) return null
        if (cursor.peek() == '/') {
            // Closing tag — caller handles this.
            cursor.rewind('<')
            return null
        }
        val name = cursor.readName()
        val attributes = mutableMapOf<String, String>()
        while (true) {
            cursor.skipWhitespace()
            when (cursor.peek()) {
                '/' -> {
                    cursor.expect('/')
                    cursor.expect('>')
                    return XmlElement(name, attributes, emptyList())
                }
                '>' -> {
                    cursor.expect('>')
                    val children = parseChildren(cursor)
                    cursor.expect('<')
                    cursor.expect('/')
                    val closing = cursor.readName()
                    if (closing != name) {
                        throw VectorParseException("Mismatched closing tag: <$name> closed by </$closing>")
                    }
                    cursor.skipWhitespace()
                    cursor.expect('>')
                    return XmlElement(name, attributes, children)
                }
                null -> throw VectorParseException("Unexpected end of input inside <$name>")
                else -> {
                    val attrName = cursor.readName()
                    cursor.skipWhitespace()
                    cursor.expect('=')
                    cursor.skipWhitespace()
                    attributes[attrName] = cursor.readQuotedValue()
                }
            }
        }
    }

    private fun parseChildren(cursor: Cursor): List<XmlElement> {
        val children = mutableListOf<XmlElement>()
        while (true) {
            cursor.skipWhitespace()
            cursor.skipCommentsAndCdata()
            if (cursor.peek() == '<' && cursor.peekAt(1) == '/') return children
            // Skip arbitrary text content (vector/svg doesn't carry meaningful text).
            cursor.skipText()
            cursor.skipCommentsAndCdata()
            if (cursor.peek() == '<' && cursor.peekAt(1) == '/') return children
            val child = parseElement(cursor) ?: return children
            children += child
        }
    }

    private class Cursor(private val source: String) {
        private var index = 0

        fun peek(): Char? = if (index < source.length) source[index] else null

        fun peekAt(offset: Int): Char? =
            if (index + offset < source.length) source[index + offset] else null

        fun consume(c: Char): Boolean {
            if (peek() == c) {
                index++
                return true
            }
            return false
        }

        fun expect(c: Char) {
            if (!consume(c)) {
                throw VectorParseException("Expected '$c' at position $index, got '${peek() ?: "EOF"}'")
            }
        }

        fun rewind(c: Char) {
            // Used to undo a single character we already consumed.
            if (index > 0 && source[index - 1] == c) index--
        }

        fun skipWhitespace() {
            while (index < source.length && source[index].isWhitespace()) index++
        }

        /** Skips through `<?...?>` and `<!DOCTYPE ...>`. */
        fun skipPrologueAndComments() {
            skipWhitespace()
            while (true) {
                if (startsWith("<?")) {
                    val end = source.indexOf("?>", index)
                    if (end < 0) throw VectorParseException("Unterminated <? ... ?>")
                    index = end + 2
                } else if (startsWith("<!--")) {
                    val end = source.indexOf("-->", index)
                    if (end < 0) throw VectorParseException("Unterminated <!-- ... -->")
                    index = end + 3
                } else if (startsWith("<!DOCTYPE")) {
                    val end = source.indexOf('>', index)
                    if (end < 0) throw VectorParseException("Unterminated <!DOCTYPE>")
                    index = end + 1
                } else {
                    return
                }
                skipWhitespace()
            }
        }

        fun skipCommentsAndCdata() {
            while (true) {
                if (startsWith("<!--")) {
                    val end = source.indexOf("-->", index)
                    if (end < 0) throw VectorParseException("Unterminated comment")
                    index = end + 3
                    skipWhitespace()
                } else if (startsWith("<![CDATA[")) {
                    val end = source.indexOf("]]>", index)
                    if (end < 0) throw VectorParseException("Unterminated CDATA")
                    index = end + 3
                    skipWhitespace()
                } else {
                    return
                }
            }
        }

        fun skipText() {
            // Consume text content between elements until the next `<`.
            while (index < source.length && source[index] != '<') index++
        }

        fun readName(): String {
            val start = index
            while (index < source.length) {
                val c = source[index]
                if (c.isLetterOrDigit() || c == ':' || c == '-' || c == '_' || c == '.') index++
                else break
            }
            if (start == index) throw VectorParseException("Expected element / attribute name at $index")
            return source.substring(start, index)
        }

        fun readQuotedValue(): String {
            val quote = peek()
            if (quote != '"' && quote != '\'') {
                throw VectorParseException("Expected quoted value at $index, got '${quote ?: "EOF"}'")
            }
            index++
            val start = index
            while (index < source.length && source[index] != quote) index++
            if (index >= source.length) throw VectorParseException("Unterminated attribute value")
            val raw = source.substring(start, index)
            index++ // consume closing quote
            return decodeEntities(raw)
        }

        private fun startsWith(prefix: String): Boolean =
            source.regionMatches(index, prefix, 0, prefix.length)
    }

    private fun decodeEntities(raw: String): String {
        if (!raw.contains('&')) return raw
        val out = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c == '&') {
                val end = raw.indexOf(';', i + 1)
                if (end < 0) {
                    out.append(c); i++; continue
                }
                val entity = raw.substring(i + 1, end)
                val replacement = when (entity) {
                    "lt" -> "<"
                    "gt" -> ">"
                    "amp" -> "&"
                    "quot" -> "\""
                    "apos" -> "'"
                    else -> if (entity.startsWith("#")) {
                        val codePoint = if (entity.startsWith("#x")) {
                            entity.substring(2).toIntOrNull(16) ?: return raw
                        } else {
                            entity.substring(1).toIntOrNull() ?: return raw
                        }
                        codePoint.toChar().toString()
                    } else {
                        // Unknown entity — leave as-is.
                        "&$entity;"
                    }
                }
                out.append(replacement)
                i = end + 1
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString()
    }
}
