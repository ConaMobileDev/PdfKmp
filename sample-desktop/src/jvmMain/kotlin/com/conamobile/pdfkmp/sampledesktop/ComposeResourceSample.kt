package com.conamobile.pdfkmp.sampledesktop

import com.conamobile.pdfkmp.PdfDocument
import com.conamobile.pdfkmp.composeresources.drawable
import com.conamobile.pdfkmp.pdfAsync
import com.conamobile.pdfkmp.sampledesktop.resources.Res
import com.conamobile.pdfkmp.sampledesktop.resources.pdfkmp_mark
import com.conamobile.pdfkmp.unit.dp
import com.conamobile.pdfkmp.unit.sp

/**
 * Builds a document from a Compose Multiplatform `DrawableResource` through
 * the `:pdfkmp-compose-resources` integration — `pdfAsync { drawable(Res.drawable.*) }`.
 *
 * This exercises `getDrawableResourceBytes` on the Desktop/JVM target
 * end-to-end (resource load → vector parse → vector PDF), proving the
 * `Res.drawable.*` path actually works on Desktop, not just compiles.
 *
 * Kept in its own file so the PdfKmp DSL units (`com.conamobile.pdfkmp.unit.dp`
 * / `sp`) don't collide with Compose UI's `androidx.compose.ui.unit.dp` / `sp`
 * used by the list screen in `Main.kt`.
 */
internal suspend fun composeResourceDoc(): PdfDocument = pdfAsync {
    metadata { title = "Compose Resource → PDF" }
    page {
        text("Compose Multiplatform Resource → PDF") {
            fontSize = 18.sp
            bold = true
        }
        text("Res.drawable.pdfkmp_mark — loaded via getDrawableResourceBytes, drawn as a vector.") {
            fontSize = 11.sp
        }
        drawable(Res.drawable.pdfkmp_mark, width = 240.dp)
    }
}
