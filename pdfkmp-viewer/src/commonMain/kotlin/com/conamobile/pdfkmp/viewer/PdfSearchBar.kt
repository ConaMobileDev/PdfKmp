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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.conamobile.pdfkmp.viewer.icons.LucideArrowLeftIcon
import com.conamobile.pdfkmp.viewer.icons.LucideSearchIcon

/**
 * Inline search field that morphs into the place of [PdfViewerTopBar]
 * while the user is searching the document. Mirrors the handoff's
 * "Topbar → search transition" behaviour: same height as the parent
 * topbar, white background, divider underneath, focus-on-mount, with
 * prev / next / close affordances and a live match counter.
 *
 * Drive it from the host: own a `query: String` and `activeIndex: Int`,
 * compute matches via [searchPdfText], pass them to [PdfViewer]'s
 * `searchHighlights` parameter, and update `activeIndex` on
 * [onPrevious] / [onNext]. Closing the bar (back arrow tap or `IME
 * Done`) should clear the query and dismiss the search state on the
 * host side via [onClose].
 *
 * @param query current search string.
 * @param onQueryChange called whenever the user edits the input.
 * @param matchCount total number of matches across the document.
 * @param activeIndex zero-based index of the currently focused match
 *   inside the match list. `-1` when nothing is active (empty query
 *   or no matches).
 * @param onPrevious previous-match tap. Should set `activeIndex` to
 *   `(activeIndex - 1 + matchCount) % matchCount`.
 * @param onNext next-match tap. Should set `activeIndex` to
 *   `(activeIndex + 1) % matchCount`.
 * @param onClose close-the-bar tap. Should reset `query` to `""` and
 *   dismiss the search state on the host so [PdfViewerTopBar] returns.
 * @param modifier applied to the outer container.
 * @param placeholder prompt rendered while the input is empty.
 */
@Composable
public fun PdfSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    matchCount: Int,
    activeIndex: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search in document",
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        // Match the design handoff's "keyboard opens" behaviour —
        // bring focus to the field as soon as the bar mounts.
        focusRequester.requestFocus()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            // Background extends behind the status bar so the morph
            // from PdfViewerTopBar reads as a swap, not as the safe
            // area suddenly turning white.
            .background(SearchBarBackground)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Close — left-aligned, mirrors the topbar's back chip
            // position so the user's thumb finds it in the same spot.
            SearchBarChip(
                icon = LucideArrowLeftIcon,
                contentDescription = "Close search",
                onClick = onClose,
            )

            // Field — flexible, fills the space between the chips.
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SearchFieldBackground)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = LucideSearchIcon,
                    contentDescription = null,
                    tint = SearchFieldHint,
                    modifier = Modifier.size(18.dp),
                )
                Box(modifier = Modifier.weight(1f)) {
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(
                            color = SearchFieldText,
                            fontSize = 15.sp,
                        ),
                        cursorBrush = SolidColor(SearchFieldText),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onNext() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                    )
                    if (query.isEmpty()) {
                        Text(
                            text = placeholder,
                            color = SearchFieldHint,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (matchCount > 0 && activeIndex >= 0) {
                    Text(
                        text = "${activeIndex + 1} / $matchCount",
                        color = SearchFieldHint,
                        fontSize = 12.sp,
                        maxLines = 1,
                    )
                } else if (query.isNotBlank()) {
                    Text(
                        text = "No matches",
                        color = SearchFieldHint,
                        fontSize = 12.sp,
                        maxLines = 1,
                    )
                }
            }

            // Prev / Next arrows. Disabled-look when there's nothing
            // to navigate.
            SearchBarChip(
                icon = ChevronUpIcon,
                contentDescription = "Previous match",
                onClick = onPrevious,
                enabled = matchCount > 0,
            )
            SearchBarChip(
                icon = ChevronDownIcon,
                contentDescription = "Next match",
                onClick = onNext,
                enabled = matchCount > 0,
            )
        }
        // Same hairline as the topbar so the morph reads as a swap,
        // not an insertion.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(SearchBarDivider),
        )
    }
}

@Composable
private fun SearchBarChip(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(if (enabled) SearchChipBackground else SearchChipDisabledBackground)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) SearchChipIcon else SearchChipDisabledIcon,
            modifier = Modifier.size(20.dp),
        )
    }
}

private val SearchBarBackground = Color(0xFFFFFFFF)
private val SearchBarDivider = Color(0xFFF1F1F3)
private val SearchFieldBackground = Color(0xFFF4F4F6)
private val SearchFieldText = Color(0xFF111111)
private val SearchFieldHint = Color(0xFF8E8E93)
private val SearchChipBackground = Color(0xFFF4F4F6)
private val SearchChipDisabledBackground = Color(0xFFFAFAFB)
private val SearchChipIcon = Color(0xFF111111)
private val SearchChipDisabledIcon = Color(0xFFC7C7CC)

private val ChevronUpIcon: ImageVector = lucideChevron(rotateDown = false)
private val ChevronDownIcon: ImageVector = lucideChevron(rotateDown = true)

private fun lucideChevron(rotateDown: Boolean): ImageVector =
    ImageVector.Builder(
        name = if (rotateDown) "LucideChevronDown" else "LucideChevronUp",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            strokeLineMiter = 4f,
            pathFillType = PathFillType.NonZero,
        ) {
            if (rotateDown) {
                moveTo(6f, 9f); lineTo(12f, 15f); lineTo(18f, 9f)
            } else {
                moveTo(6f, 15f); lineTo(12f, 9f); lineTo(18f, 15f)
            }
        }
    }.build()
