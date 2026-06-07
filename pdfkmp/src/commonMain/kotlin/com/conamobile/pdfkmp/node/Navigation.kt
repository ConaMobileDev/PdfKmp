package com.conamobile.pdfkmp.node

import com.conamobile.pdfkmp.style.TextStyle
import com.conamobile.pdfkmp.unit.Dp

/**
 * Placeholder for an automatically generated table of contents.
 *
 * The renderer replaces this node before layout: it collects every
 * [BookmarkNode] in the document, resolves their final page numbers with
 * a dry-run pass, and expands the placeholder into one clickable row per
 * bookmark — title, dotted leader, page number — wired to the bookmark's
 * position through an internal link.
 *
 * Only valid in a page body. Headers, footers, and watermarks are laid
 * out per physical page and cannot host a TOC.
 *
 * @property maxLevel deepest bookmark level included (0 = chapters only).
 * @property style text style for the entry rows.
 * @property indentPerLevel horizontal indent applied per bookmark level.
 * @property spacing vertical gap between entry rows.
 */
public data class TocNode(
    val maxLevel: Int,
    val style: TextStyle,
    val indentPerLevel: Dp,
    val spacing: Dp,
) : PdfNode

/**
 * Zero-size marker that adds an entry to the document outline (the
 * bookmark sidebar in PDF readers) pointing at the marker's rendered
 * position.
 *
 * Supported by the iOS and JVM backends; Android's `PdfDocument` API has
 * no outline support, so the marker is silently ignored there.
 *
 * @property title text shown in the reader's outline panel.
 * @property level nesting depth — `0` for chapters, `1` for sections
 *   inside the previous level-0 entry, and so on.
 */
public data class BookmarkNode(
    val title: String,
    val level: Int = 0,
) : PdfNode

/**
 * Zero-size marker registering a named jump target at the marker's
 * rendered position. Pair with [InternalLinkNode] to build clickable
 * cross-references and tables of contents.
 *
 * @property id document-unique destination name. Registering the same id
 *   twice keeps the last occurrence.
 */
public data class AnchorNode(
    val id: String,
) : PdfNode

/**
 * Wraps [child] in a clickable region that jumps to the [AnchorNode]
 * registered under [anchorId]. Forward references are fine — the target
 * anchor may appear later in the document. Links to anchors that never
 * get registered are silently inert.
 *
 * Supported by the iOS and JVM backends; Android draws the visual
 * styling only.
 */
public data class InternalLinkNode(
    val anchorId: String,
    val child: PdfNode,
) : PdfNode
