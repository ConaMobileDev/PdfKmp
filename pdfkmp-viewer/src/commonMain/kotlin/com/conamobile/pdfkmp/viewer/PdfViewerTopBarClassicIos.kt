package com.conamobile.pdfkmp.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.conamobile.pdfkmp.viewer.icons.LucideChevronLeftIcon
import com.conamobile.pdfkmp.viewer.icons.LucideDownloadIcon
import com.conamobile.pdfkmp.viewer.icons.LucideHighlighterIcon
import com.conamobile.pdfkmp.viewer.icons.LucidePrinterIcon
import com.conamobile.pdfkmp.viewer.icons.LucideSearchIcon
import com.conamobile.pdfkmp.viewer.icons.LucideShareIcon

/**
 * **Classic iOS Native** topbar variant — matches Mail / Files / Notes
 * conventions for products that target iOS exclusively (or that want
 * to feel native on Apple platforms).
 *
 * Faithful implementation of `Direction 2` from
 * `design_handoff_pdf_topbar/README.md`:
 *
 * - 52dp tall, white background, 0.5dp `rgba(0,0,0,0.08)` hairline.
 * - Three-column grid (`1fr · auto · 1fr`):
 *   - **Leading**: `chevron-left` (28sp, stroke 2.4) + optional back
 *     label (e.g. *"Files"*). Tinted iOS Blue `#0A84FF`.
 *   - **Center**: filename, 17sp semibold `#000`, single line. The
 *     bar measures the leading and trailing columns first and reserves
 *     an equal gutter on each side, so the title stays optically
 *     centered and yields ([titleOverflow]) the moment it would
 *     otherwise crowd the icons — it can never push them off the bar.
 *   - **Trailing**: up to four 36×36 icon buttons (search, share,
 *     print, download), all tinted iOS Blue, equal weight — emphasis
 *     comes from position rather than colour. Each can be hidden via
 *     the matching `show…` flag. These are measured at their natural
 *     size and never shrink, regardless of how long the title is.
 *
 * @param title filename / document name centered between the two
 *   columns.
 * @param titleOverflow how the title behaves when it is too long to
 *   fit the reserved center gutter — [PdfTopBarTitleOverflow.Ellipsis]
 *   (default) truncates with `…`, [PdfTopBarTitleOverflow.Marquee]
 *   scrolls it horizontally.
 * @param backLabel optional label rendered next to the chevron. Drop
 *   to `null` for chevron-only back navigation.
 * @param onBack tap callback for the leading column (entire chevron +
 *   label is the hit target). Ignored when [showBack] is `false`.
 * @param onSearch tap callback for the search button.
 * @param onShare tap callback for the share button.
 * @param onPrint tap callback for the print button.
 * @param onDownload tap callback for the download button.
 * @param onAnnotate tap callback for the highlight-annotation toggle.
 * @param showBack hide / show the back chevron + label.
 * @param showSearch hide / show the search button.
 * @param showShare hide / show the share button.
 * @param showPrint hide / show the print button. `false` by default
 *   because printing is opt-in — wire [onPrint] *and* set this to
 *   `true` to surface it.
 * @param showDownload hide / show the download button.
 * @param showAnnotate hide / show the highlight-annotation toggle.
 *   `false` by default — annotation tools are opt-in.
 * @param annotateActive whether annotation mode is on; the toggle
 *   tints its background iOS-blue while active so the engaged mode is
 *   visible.
 * @param modifier applied to the outer [Column] container.
 */
@Composable
public fun PdfViewerTopBarClassicIos(
    title: String,
    modifier: Modifier = Modifier,
    titleOverflow: PdfTopBarTitleOverflow = PdfTopBarTitleOverflow.Ellipsis,
    backLabel: String? = null,
    onBack: () -> Unit = {},
    onSearch: () -> Unit = {},
    onShare: () -> Unit = {},
    onPrint: () -> Unit = {},
    onDownload: () -> Unit = {},
    onAnnotate: () -> Unit = {},
    showBack: Boolean = true,
    showSearch: Boolean = false,
    showShare: Boolean = true,
    showPrint: Boolean = false,
    showDownload: Boolean = true,
    showAnnotate: Boolean = false,
    annotateActive: Boolean = false,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            // Background applied BEFORE statusBarsPadding so the white
            // surface extends behind the status bar — content sits
            // in the safe area beneath it.
            .background(ClassicIosBackground)
            .statusBarsPadding(),
    ) {
        // Custom three-slot layout instead of a weighted Row: a weighted
        // Row measures the (unweighted) center title first and lets it
        // eat the whole bar, collapsing the weighted side columns to
        // zero — which is exactly how the trailing icons used to shrink
        // and vanish. Here the side slots are measured at their natural
        // width and the title is handed only the symmetric gutter that
        // remains, so the icons are inviolable and the title stays
        // optically centered.
        ClassicIosNavRow(
            modifier = Modifier
                .fillMaxWidth()
                .height(ClassicIosHeight)
                .padding(horizontal = 8.dp),
            leading = {
                if (showBack) {
                    ClassicIosBackButton(
                        label = backLabel,
                        onClick = onBack,
                    )
                }
            },
            title = {
                ClassicIosTitle(
                    title = title,
                    overflow = titleOverflow,
                )
            },
            trailing = {
                // Right-aligned trailing icons. Padding end = 4dp keeps
                // the last icon off the bar edge.
                Row(
                    modifier = Modifier.padding(end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                ) {
                    if (showAnnotate) {
                        ClassicIosTrailingButton(
                            icon = LucideHighlighterIcon,
                            onClick = onAnnotate,
                            contentDescription = "Highlight",
                            active = annotateActive,
                        )
                    }
                    if (showSearch) {
                        ClassicIosTrailingButton(
                            icon = LucideSearchIcon,
                            onClick = onSearch,
                            contentDescription = "Search",
                        )
                    }
                    if (showShare) {
                        ClassicIosTrailingButton(
                            icon = LucideShareIcon,
                            onClick = onShare,
                            contentDescription = "Share",
                        )
                    }
                    if (showPrint) {
                        ClassicIosTrailingButton(
                            icon = LucidePrinterIcon,
                            onClick = onPrint,
                            contentDescription = "Print",
                        )
                    }
                    if (showDownload) {
                        ClassicIosTrailingButton(
                            icon = LucideDownloadIcon,
                            onClick = onDownload,
                            contentDescription = "Download",
                        )
                    }
                }
            },
        )
        // 0.5dp hairline — Compose can't render sub-pixel; we use 1dp at
        // a slightly higher alpha to approximate the visual weight.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(ClassicIosDivider),
        )
    }
}

/**
 * iOS title slot — a single line whose overflow strategy is chosen by
 * [overflow]. Lives in its own composable so the [ClassicIosNavRow]
 * measure pass can hand it a bounded width and let it ellipsize or
 * marquee within exactly the gutter the icons leave behind.
 */
@Composable
private fun ClassicIosTitle(
    title: String,
    overflow: PdfTopBarTitleOverflow,
) {
    val base = Modifier.padding(horizontal = 8.dp)
    when (overflow) {
        PdfTopBarTitleOverflow.Ellipsis -> Text(
            text = title,
            color = ClassicIosTitleColor,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.4).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = base,
        )

        PdfTopBarTitleOverflow.Marquee -> Text(
            text = title,
            color = ClassicIosTitleColor,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.4).sp,
            maxLines = 1,
            softWrap = false,
            // basicMarquee only animates when the text overflows the
            // bounded width; shorter titles render statically.
            modifier = base.basicMarquee(),
        )
    }
}

/**
 * Three-slot nav-bar layout with an inviolable, naturally-sized
 * trailing (and leading) column and a title that is optically centered
 * in the whole bar.
 *
 * Measure order is deliberate: the trailing icons go first and are
 * never constrained, so they always render at full size. The leading
 * column is then capped to whatever width is left. Finally the title
 * is given the symmetric gutter `maxWidth − 2·max(leading, trailing)`,
 * which guarantees it can never overlap — let alone displace — either
 * side column, while keeping its center pinned to the bar's center.
 */
@Composable
private fun ClassicIosNavRow(
    modifier: Modifier,
    leading: @Composable () -> Unit,
    title: @Composable () -> Unit,
    trailing: @Composable () -> Unit,
) {
    Layout(
        modifier = modifier,
        content = {
            // Each slot is wrapped in a Box so the layout always sees
            // exactly three measurables, even when a slot emits nothing
            // (e.g. the leading column when the back affordance is
            // hidden). Indexing into the measurable list below relies on
            // this fixed arity.
            Box { leading() }
            Box { title() }
            Box { trailing() }
        },
    ) { measurables, constraints ->
        val maxWidth = constraints.maxWidth
        val loose = constraints.copy(minWidth = 0, minHeight = 0)

        // Trailing icons first — measured unconstrained so they keep
        // their natural width no matter how long the title is.
        val trailingPlaceable = measurables[2].measure(loose)
        // Leading is capped to the space the icons leave, so the two
        // side columns always fit even on a very narrow bar.
        val leadingMax = (maxWidth - trailingPlaceable.width).coerceAtLeast(0)
        val leadingPlaceable = measurables[0].measure(loose.copy(maxWidth = leadingMax))

        // Reserve an equal gutter on both sides so the title stays
        // optically centered; the title gets only what's between them.
        val side = maxOf(leadingPlaceable.width, trailingPlaceable.width)
        val titleMax = (maxWidth - 2 * side).coerceAtLeast(0)
        val titlePlaceable = measurables[1].measure(loose.copy(maxWidth = titleMax))

        val height = if (constraints.hasFixedHeight) {
            constraints.maxHeight
        } else {
            maxOf(leadingPlaceable.height, titlePlaceable.height, trailingPlaceable.height)
        }

        layout(maxWidth, height) {
            leadingPlaceable.placeRelative(
                x = 0,
                y = (height - leadingPlaceable.height) / 2,
            )
            titlePlaceable.placeRelative(
                x = (maxWidth - titlePlaceable.width) / 2,
                y = (height - titlePlaceable.height) / 2,
            )
            trailingPlaceable.placeRelative(
                x = maxWidth - trailingPlaceable.width,
                y = (height - trailingPlaceable.height) / 2,
            )
        }
    }
}

@Composable
private fun ClassicIosBackButton(
    label: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(
            imageVector = LucideChevronLeftIcon,
            contentDescription = "Back",
            tint = ClassicIosAccent,
            modifier = Modifier.size(28.dp),
        )
        if (!label.isNullOrBlank()) {
            Text(
                text = label,
                color = ClassicIosAccent,
                fontSize = 17.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = (-0.4).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ClassicIosTrailingButton(
    icon: ImageVector,
    onClick: () -> Unit,
    contentDescription: String?,
    active: Boolean = false,
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            // A faint iOS-blue wash behind the glyph signals the engaged
            // mode (e.g. annotation toggle on) — neutral background while
            // idle, matching the other trailing buttons.
            .background(if (active) ClassicIosActiveBackground else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = ClassicIosAccent,
            modifier = Modifier.size(22.dp),
        )
    }
}

private val ClassicIosHeight = 52.dp
private val ClassicIosBackground = Color(0xFFFFFFFF)
private val ClassicIosTitleColor = Color(0xFF000000)
private val ClassicIosAccent = Color(0xFF0A84FF)
private val ClassicIosActiveBackground = Color(0x1A0A84FF) // ≈ 10% iOS Blue
private val ClassicIosDivider = Color(0x14000000) // ≈ rgba(0,0,0,0.08)
