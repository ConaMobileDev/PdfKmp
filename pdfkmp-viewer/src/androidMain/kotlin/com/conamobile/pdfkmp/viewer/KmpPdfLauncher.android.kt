package com.conamobile.pdfkmp.viewer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.conamobile.pdfkmp.PdfDocument
import com.conamobile.pdfkmp.viewer.internal.ViewerContextHolder

/**
 * Android implementation of the imperative launcher. Each `open(...)`
 * builds an [Intent] targeting [KmpPdfViewerHostActivity], stuffs the
 * payload into either a primitive extra (URI / small byte arrays) or
 * a [KmpPdfLauncherRegistry] token, and starts the activity using the
 * application context the library captured at startup via
 * `ViewerContextInitializer`.
 *
 * `FLAG_ACTIVITY_NEW_TASK` is set unconditionally — the captured
 * context is the application context, not an activity, so a fresh
 * task is the only legal launch mode. Apps that want the viewer to
 * sit in their existing task should use the [KmpPdfViewer]
 * composable inside their own navigation graph instead.
 */
public actual object KmpPdfLauncher {

    /** Intent payloads kept under one extras key so we can switch between them. */
    internal const val EXTRA_TITLE: String = "kmp.pdf.title"
    internal const val EXTRA_FILE_NAME: String = "kmp.pdf.fileName"
    internal const val EXTRA_URI: String = "kmp.pdf.uri"
    internal const val EXTRA_BYTES: String = "kmp.pdf.bytes"
    internal const val EXTRA_TOKEN: String = "kmp.pdf.token"

    /** Cap on inline byte payloads — Bundle parcelling fails near the 1 MiB mark. */
    private const val INLINE_BYTES_LIMIT: Int = 512 * 1024

    public actual fun open(uri: String, title: String, fileName: String) {
        launch { putExtra(EXTRA_URI, uri).addCommon(title, fileName) }
    }

    public actual fun open(bytes: ByteArray, title: String, fileName: String) {
        if (bytes.size < INLINE_BYTES_LIMIT) {
            launch { putExtra(EXTRA_BYTES, bytes).addCommon(title, fileName) }
        } else {
            val token = KmpPdfLauncherRegistry.put(PdfSource.Bytes(bytes))
            launch { putExtra(EXTRA_TOKEN, token).addCommon(title, fileName) }
        }
    }

    public actual fun open(document: PdfDocument, title: String, fileName: String) {
        // Always go through the registry so we can carry the
        // captured text runs + hyperlinks — primitives can't hold
        // that metadata across the IPC boundary.
        val token = KmpPdfLauncherRegistry.put(PdfSource.of(document))
        launch { putExtra(EXTRA_TOKEN, token).addCommon(title, fileName) }
    }

    private inline fun launch(crossinline configure: Intent.() -> Intent) {
        val context = ViewerContextHolder.get()
        val intent = Intent(context, KmpPdfViewerHostActivity::class.java)
            .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            .configure()
        context.startActivity(intent)
    }

    private fun Intent.addCommon(title: String, fileName: String): Intent =
        putExtra(EXTRA_TITLE, title).putExtra(EXTRA_FILE_NAME, fileName)
}

/**
 * Hosted activity that mounts [KmpPdfViewer] full-screen. Reads the
 * payload from the launching Intent, forwards every back tap (chip,
 * gesture, system) to [finish].
 *
 * Declared in `:pdfkmp-viewer`'s `AndroidManifest.xml` so consumers
 * don't have to register anything by hand.
 */
internal class KmpPdfViewerHostActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val title = intent.getStringExtra(KmpPdfLauncher.EXTRA_TITLE) ?: "Document"
        val fileName = intent.getStringExtra(KmpPdfLauncher.EXTRA_FILE_NAME) ?: "document.pdf"
        val uri = intent.getStringExtra(KmpPdfLauncher.EXTRA_URI)
        val bytes = intent.getByteArrayExtra(KmpPdfLauncher.EXTRA_BYTES)
        val token = intent.getStringExtra(KmpPdfLauncher.EXTRA_TOKEN)

        setContent {
            MaterialTheme {
                when {
                    uri != null -> KmpPdfViewer(
                        uri = uri,
                        title = title,
                        fileName = fileName,
                        onBack = { finish() },
                    )

                    bytes != null -> KmpPdfViewer(
                        bytes = bytes,
                        title = title,
                        fileName = fileName,
                        onBack = { finish() },
                    )

                    token != null -> {
                        val source = remember(token) { KmpPdfLauncherRegistry.take(token) }
                        if (source != null) {
                            KmpPdfViewer(
                                source = source,
                                title = title,
                                fileName = fileName,
                                onBack = { finish() },
                            )
                        } else {
                            // Stale token (process restart, double launch).
                            // Dismiss immediately so the user isn't
                            // staring at a blank screen.
                            LaunchedEffect(Unit) { finish() }
                        }
                    }

                    else -> LaunchedEffect(Unit) { finish() }
                }
            }
        }
    }
}

