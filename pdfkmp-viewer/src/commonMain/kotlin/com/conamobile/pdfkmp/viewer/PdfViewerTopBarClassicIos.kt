package com.conamobile.pdfkmp.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.conamobile.pdfkmp.viewer.icons.LucideChevronLeftIcon
import com.conamobile.pdfkmp.viewer.icons.LucideDownloadIcon
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
 *   - **Center**: filename, 17sp semibold `#000`, single line +
 *     ellipsis, max-width ~180dp.
 *   - **Trailing**: three 36×36 icon buttons (search, share,
 *     download), all tinted iOS Blue, equal weight — emphasis comes
 *     from position rather than colour. Each can be hidden via the
 *     matching `show…` flag.
 *
 * @param title filename / document name centered between the two
 *   columns.
 * @param backLabel optional label rendered next to the chevron. Drop
 *   to `null` for chevron-only back navigation.
 * @param onBack tap callback for the leading column (entire chevron +
 *   label is the hit target). Ignored when [showBack] is `false`.
 * @param onSearch tap callback for the search button.
 * @param onShare tap callback for the share button.
 * @param onDownload tap callback for the download button.
 * @param showBack hide / show the back chevron + label.
 * @param showSearch hide / show the search button.
 * @param showShare hide / show the share button.
 * @param showDownload hide / show the download button.
 * @param modifier applied to the outer [Column] container.
 */
@Composable
public fun PdfViewerTopBarClassicIos(
    title: String,
    modifier: Modifier = Modifier,
    backLabel: String? = null,
    onBack: () -> Unit = {},
    onSearch: () -> Unit = {},
    onShare: () -> Unit = {},
    onDownload: () -> Unit = {},
    showBack: Boolean = true,
    showSearch: Boolean = false,
    showShare: Boolean = true,
    showDownload: Boolean = true,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ClassicIosBackground),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(ClassicIosHeight)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Leading column — flexible (1fr).
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(ClassicIosHeight),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (showBack) {
                    ClassicIosBackButton(
                        label = backLabel,
                        onClick = onBack,
                    )
                }
            }
            // Center column — title, naturally narrow (auto).
            Box(
                modifier = Modifier.height(ClassicIosHeight),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = title,
                    color = ClassicIosTitleColor,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.4).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
            // Trailing column — flexible (1fr), right-aligned.
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(ClassicIosHeight)
                    .padding(end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
            ) {
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
                if (showDownload) {
                    ClassicIosTrailingButton(
                        icon = LucideDownloadIcon,
                        onClick = onDownload,
                        contentDescription = "Download",
                    )
                }
            }
        }
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
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
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
private val ClassicIosDivider = Color(0x14000000) // ≈ rgba(0,0,0,0.08)
