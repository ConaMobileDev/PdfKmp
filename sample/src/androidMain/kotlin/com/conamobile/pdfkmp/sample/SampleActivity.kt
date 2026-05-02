package com.conamobile.pdfkmp.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.conamobile.pdfkmp.PdfDocument
import com.conamobile.pdfkmp.samples.Samples
import com.conamobile.pdfkmp.viewer.PdfViewer
import com.conamobile.pdfkmp.viewer.rememberPdfSaveAction
import com.conamobile.pdfkmp.viewer.rememberPdfShareAction

/**
 * Hosts the Android demo for PdfKmp.
 *
 * The app shows a list of bundled sample documents; tapping one builds
 * the PDF in-process via PdfKmp and hands the resulting [PdfDocument]
 * straight to the [`:pdfkmp-viewer`][PdfViewer] composable. The viewer
 * itself goes through Android's system [android.graphics.pdf.PdfRenderer]
 * to rasterise pages for display only — the on-disk and shared PDF
 * stays vector and stays sharp at any zoom level.
 */
class SampleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                SampleApp()
            }
        }
    }
}

private data class SampleEntry(
    val title: String,
    val build: suspend (SampleAssets) -> PdfDocument,
)

/**
 * Resources that come from the sample app's APK assets and are passed into
 * the platform-agnostic [Samples] builders. Keeping the bytes here (rather
 * than inside `:pdfkmp`) avoids shipping demo media inside the library.
 */
private class SampleAssets(val sampleImagePng: ByteArray)

private val SAMPLES = listOf(
    SampleEntry("⭐ Brochure (README hero)") { Samples.brochure() },
    SampleEntry("🧩 Compose Resources (Res.drawable.*)") { ComposeResourcesDemo.build() },
    SampleEntry("Hello world") { Samples.helloWorld() },
    SampleEntry("Typography — text + decorations + alignment + rich") { Samples.typography() },
    SampleEntry("Row & Column with weights") { Samples.rowAndColumn() },
    SampleEntry("Column SpaceBetween") { Samples.columnSpaceBetween() },
    SampleEntry("Tables & lists") { Samples.tableShowcase() },
    SampleEntry("Vectors + circle/ellipse") { Samples.vectorShowcase() },
    SampleEntry("Vector — gradients + arcs + transforms") { Samples.vectorAdvanced() },
    SampleEntry("Custom designs + decorations (gradients, corners, borders)") {
        Samples.customDesigns(it.sampleImagePng)
    },
    SampleEntry("Page chrome — header / footer / page#  / watermark / links / i18n") {
        Samples.pageChrome()
    },
    SampleEntry("Long body — MoveToNextPage") { Samples.longBody() },
    SampleEntry("Long body — Slice") { Samples.slicedBody() },
    SampleEntry("Custom padding") { Samples.customPadding() },
    SampleEntry("Image (Fit + Crop)") { Samples.withImage(it.sampleImagePng) },
    SampleEntry("Tall image — sliced") { Samples.slicedImage(it.sampleImagePng) },
    SampleEntry("Showcase — every v1 feature in one PDF") { Samples.showcase() },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SampleApp() {
    var selected by remember { mutableStateOf<SampleEntry?>(null) }
    var document by remember(selected) { mutableStateOf<PdfDocument?>(null) }
    val context = LocalContext.current
    val shareAction = rememberPdfShareAction()
    val saveAction = rememberPdfSaveAction()

    LaunchedEffect(selected) {
        val current = selected ?: return@LaunchedEffect
        val assets = SampleAssets(
            sampleImagePng = context.assets.open("sample.png").use { it.readBytes() },
        )
        document = current.build(assets)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = selected?.title ?: "PdfKmp samples",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    if (selected != null) {
                        IconButton(onClick = { selected = null }) {
                            Icon(
                                imageVector = BackArrowIcon,
                                contentDescription = "Back",
                            )
                        }
                    }
                },
                actions = {
                    val entry = selected
                    val built = document
                    if (entry != null && built != null) {
                        val fileName = "${entry.title.toFileSlug()}.pdf"
                        IconButton(
                            onClick = { saveAction(built.toByteArray(), fileName) },
                        ) {
                            Icon(
                                imageVector = SaveIcon,
                                contentDescription = "Save to Downloads",
                            )
                        }
                        IconButton(
                            onClick = { shareAction(built.toByteArray(), fileName) },
                        ) {
                            Icon(
                                imageVector = ShareIcon,
                                contentDescription = "Share",
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(),
            )
        },
    ) { padding ->
        if (selected == null) {
            SampleList(
                onPick = { selected = it },
                modifier = Modifier.padding(padding),
            )
        } else {
            SamplePreview(
                document = document,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun SampleList(onPick: (SampleEntry) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(SAMPLES) { entry ->
            Button(onClick = { onPick(entry) }, modifier = Modifier.fillMaxWidth()) {
                Text(entry.title)
            }
        }
    }
}

@Composable
private fun SamplePreview(
    document: PdfDocument?,
    modifier: Modifier = Modifier,
) {
    if (document == null) {
        Column(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { Text("Building…") }
    } else {
        PdfViewer(
            document = document,
            modifier = modifier,
            showShareButton = false,
        )
    }
}

private fun String.toFileSlug(): String =
    lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

/**
 * Inline back-arrow [ImageVector] so the sample doesn't need a
 * dependency on `compose-material-icons-extended` for a single glyph.
 * Path data lifted from the official Material Symbols `arrow_back`
 * (filled, 24dp baseline).
 */
private val BackArrowIcon: ImageVector = ImageVector.Builder(
    name = "BackArrow",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(
        fill = SolidColor(Color.Black),
        stroke = null,
        strokeLineWidth = 0f,
        strokeLineCap = StrokeCap.Butt,
        strokeLineJoin = StrokeJoin.Miter,
        strokeLineMiter = 4f,
        pathFillType = PathFillType.NonZero,
    ) {
        moveTo(20f, 11f)
        horizontalLineTo(7.83f)
        lineToRelative(5.59f, -5.59f)
        lineTo(12f, 4f)
        lineToRelative(-8f, 8f)
        lineToRelative(8f, 8f)
        lineToRelative(1.41f, -1.41f)
        lineTo(7.83f, 13f)
        horizontalLineTo(20f)
        verticalLineToRelative(-2f)
        close()
    }
}.build()

/**
 * Inline Material Symbols `download` glyph used in the top app bar
 * for the "Save to Downloads" action.
 */
private val SaveIcon: ImageVector = ImageVector.Builder(
    name = "Save",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(
        fill = SolidColor(Color.Black),
        stroke = null,
        strokeLineWidth = 0f,
        strokeLineCap = StrokeCap.Butt,
        strokeLineJoin = StrokeJoin.Miter,
        strokeLineMiter = 4f,
        pathFillType = PathFillType.NonZero,
    ) {
        moveTo(19f, 9f)
        horizontalLineToRelative(-4f)
        verticalLineTo(3f)
        horizontalLineTo(9f)
        verticalLineToRelative(6f)
        horizontalLineTo(5f)
        lineToRelative(7f, 7f)
        lineToRelative(7f, -7f)
        close()
        moveTo(5f, 18f)
        verticalLineToRelative(2f)
        horizontalLineToRelative(14f)
        verticalLineToRelative(-2f)
        horizontalLineTo(5f)
        close()
    }
}.build()

/**
 * Inline Material Symbols `share` glyph used in the top app bar. We
 * keep a separate copy from `:pdfkmp-viewer`'s internal share icon so
 * neither side leaks into the other's API surface.
 */
private val ShareIcon: ImageVector = ImageVector.Builder(
    name = "Share",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(
        fill = SolidColor(Color.Black),
        stroke = null,
        strokeLineWidth = 0f,
        strokeLineCap = StrokeCap.Butt,
        strokeLineJoin = StrokeJoin.Miter,
        strokeLineMiter = 4f,
        pathFillType = PathFillType.NonZero,
    ) {
        moveTo(18f, 16.08f)
        curveToRelative(-0.76f, 0f, -1.44f, 0.3f, -1.96f, 0.77f)
        lineTo(8.91f, 12.7f)
        curveToRelative(0.05f, -0.23f, 0.09f, -0.46f, 0.09f, -0.7f)
        reflectiveCurveToRelative(-0.04f, -0.47f, -0.09f, -0.7f)
        lineToRelative(7.05f, -4.11f)
        curveToRelative(0.54f, 0.5f, 1.25f, 0.81f, 2.04f, 0.81f)
        curveToRelative(1.66f, 0f, 3f, -1.34f, 3f, -3f)
        reflectiveCurveToRelative(-1.34f, -3f, -3f, -3f)
        reflectiveCurveToRelative(-3f, 1.34f, -3f, 3f)
        curveToRelative(0f, 0.24f, 0.04f, 0.47f, 0.09f, 0.7f)
        lineTo(8.04f, 9.81f)
        curveTo(7.5f, 9.31f, 6.79f, 9f, 6f, 9f)
        curveToRelative(-1.66f, 0f, -3f, 1.34f, -3f, 3f)
        reflectiveCurveToRelative(1.34f, 3f, 3f, 3f)
        curveToRelative(0.79f, 0f, 1.5f, -0.31f, 2.04f, -0.81f)
        lineToRelative(7.12f, 4.16f)
        curveToRelative(-0.05f, 0.21f, -0.08f, 0.43f, -0.08f, 0.65f)
        curveToRelative(0f, 1.61f, 1.31f, 2.92f, 2.92f, 2.92f)
        reflectiveCurveToRelative(2.92f, -1.31f, 2.92f, -2.92f)
        reflectiveCurveToRelative(-1.31f, -2.92f, -2.92f, -2.92f)
        close()
    }
}.build()
