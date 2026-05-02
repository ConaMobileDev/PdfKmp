package com.conamobile.pdfkmp.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
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
import androidx.compose.material3.Text
import com.conamobile.pdfkmp.viewer.icons.LucideArrowLeftIcon
import com.conamobile.pdfkmp.viewer.icons.LucideDownloadIcon
import com.conamobile.pdfkmp.viewer.icons.LucideSearchIcon
import com.conamobile.pdfkmp.viewer.icons.LucideShareIcon

/**
 * **Minimal Mono** topbar variant — modern, brand-neutral, with the
 * download action visually elevated as the primary CTA.
 *
 * Faithful implementation of `Direction 1` from
 * `design_handoff_pdf_topbar/README.md`:
 *
 * - 64dp tall, white background, single 1dp `#F1F1F3` hairline divider.
 * - Five children left → right, gap 4dp:
 *   1. 38×38 back chip (gray `#F4F4F6` circle, Lucide `arrow-left`)
 *   2. Title block (filename `15sp` semibold `#0A0A0A` + UPPERCASE
 *      `11sp` meta line `#8E8E93` truncated with ellipsis)
 *   3. 38×38 search chip (same gray, hidden when [onSearch] is null)
 *   4. 38×38 share chip (same gray, hidden when [onShare] is null)
 *   5. 38×38 download chip — **primary action**, filled `#111111`
 *      with white icon, hidden when [onDownload] is null.
 *
 * Recommended as the platform-agnostic default, especially on Android
 * where it composes naturally with Material 3 surfaces. Pair it with
 * [PdfViewer] in your own `Scaffold(topBar = { … })`.
 *
 * @param title filename / document name surfaced as the bold first line.
 * @param subtitle optional UPPERCASE meta line — typically
 *   `"PDF · 2.4 MB"` or similar context. Set to `null` to drop the line.
 * @param onBack tap callback for the back chip. Ignored when
 *   [showBack] is `false`.
 * @param onSearch tap callback for the search chip. Ignored when
 *   [showSearch] is `false`.
 * @param onShare tap callback for the share chip. Ignored when
 *   [showShare] is `false`.
 * @param onDownload tap callback for the primary download chip.
 *   Ignored when [showDownload] is `false`.
 * @param showBack hide / show the back chip independently of
 *   [onBack]. `true` by default.
 * @param showSearch hide / show the search chip. `false` by default
 *   because search is opt-in — wire [onSearch] *and* set this to
 *   `true` to surface it.
 * @param showShare hide / show the share chip. `true` by default.
 * @param showDownload hide / show the primary download chip. `true`
 *   by default.
 * @param modifier applied to the outer [Row] container.
 */
@Composable
public fun PdfViewerTopBarMinimalMono(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
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
            // Background applied BEFORE statusBarsPadding so the white
            // surface extends behind the status bar — content (chips,
            // title) sits in the safe area beneath it. Matches the
            // way Material 3 `TopAppBar` consumes
            // `WindowInsets.statusBars` by default.
            .background(MinimalMonoBackground)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(MinimalMonoHeight)
                .padding(
                    PaddingValues(
                        start = 8.dp,
                        end = 8.dp,
                        top = 6.dp,
                        bottom = 10.dp,
                    ),
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (showBack) {
                MinimalMonoChip(
                    icon = LucideArrowLeftIcon,
                    onClick = onBack,
                    contentDescription = "Back",
                )
            }
            MinimalMonoTitleBlock(
                title = title,
                subtitle = subtitle,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 6.dp),
            )
            if (showSearch) {
                MinimalMonoChip(
                    icon = LucideSearchIcon,
                    onClick = onSearch,
                    contentDescription = "Search",
                )
            }
            if (showShare) {
                MinimalMonoChip(
                    icon = LucideShareIcon,
                    onClick = onShare,
                    contentDescription = "Share",
                )
            }
            if (showDownload) {
                MinimalMonoChip(
                    icon = LucideDownloadIcon,
                    onClick = onDownload,
                    contentDescription = "Download",
                    primary = true,
                )
            }
        }
        // Hairline divider — drawn as a plain colored Box so the
        // 1dp height is exact across densities (Material's `HorizontalDivider`
        // adds extra padding behind the scenes).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MinimalMonoDivider),
        )
    }
}

@Composable
private fun MinimalMonoTitleBlock(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            color = MinimalMonoTitleColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.2).sp,
            lineHeight = 18.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle.uppercase(),
                color = MinimalMonoSubtitleColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.2.sp,
                lineHeight = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
    }
}

@Composable
private fun MinimalMonoChip(
    icon: ImageVector,
    onClick: () -> Unit,
    contentDescription: String?,
    primary: Boolean = false,
) {
    val containerColor = if (primary) MinimalMonoPrimary else MinimalMonoChipBackground
    val tint = if (primary) Color.White else MinimalMonoIconColor
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(containerColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(if (primary) 22.dp else 20.dp),
        )
    }
}

private val MinimalMonoHeight = 64.dp
private val MinimalMonoBackground = Color(0xFFFFFFFF)
private val MinimalMonoChipBackground = Color(0xFFF4F4F6)
private val MinimalMonoIconColor = Color(0xFF111111)
private val MinimalMonoPrimary = Color(0xFF111111)
private val MinimalMonoTitleColor = Color(0xFF0A0A0A)
private val MinimalMonoSubtitleColor = Color(0xFF8E8E93)
private val MinimalMonoDivider = Color(0xFFF1F1F3)
