package com.conamobile.pdfkmp.sample

import com.conamobile.pdfkmp.PdfDocument
import com.conamobile.pdfkmp.composeresources.drawable
import com.conamobile.pdfkmp.pdfAsync
import com.conamobile.pdfkmp.style.PdfColor
import com.conamobile.pdfkmp.unit.dp
import com.conamobile.pdfkmp.unit.sp
import com.conamobile.pdfkmp.sample.generated.resources.Res
import com.conamobile.pdfkmp.sample.generated.resources.sample_photo
import com.conamobile.pdfkmp.sample.generated.resources.star_icon

/**
 * Tiny demo PDF that exercises the inline `:pdfkmp-compose-resources`
 * DSL — typed `Res.drawable.*` references go straight into `drawable(...)`
 * without an upfront suspend load. The auto-detect picks vector vs raster
 * from the file's leading bytes during the preflight pass that
 * [pdfAsync] runs before layout.
 */
internal object ComposeResourcesDemo {

    /**
     * `build` is `suspend` because [pdfAsync] runs the resource preflight
     * pass — but inside the DSL block every call site is synchronous,
     * exactly as in the eager [com.conamobile.pdfkmp.pdf] entry point.
     */
    suspend fun build(): PdfDocument = pdfAsync {
        metadata { title = "PdfKmp – Compose Resources" }

        page {
            spacing = 16.dp

            text("Compose Multiplatform Resources") {
                fontSize = 22.sp; bold = true
            }
            text(
                "Both items below were placed by typed Res.drawable.* " +
                    "references inside a synchronous drawable(...) DSL call. " +
                    "The library auto-detected the XML vector and the PNG " +
                    "raster from the leading bytes during pdfAsync's preflight pass."
            ) {
                fontSize = 11.sp; color = PdfColor.Gray
            }

            text("star_icon.xml — a <vector> drawable rendered as path geometry:") {
                fontSize = 12.sp; color = PdfColor.DarkGray
            }
            drawable(Res.drawable.star_icon, width = 96.dp, tint = PdfColor.fromRgb(0xE6A100))

            text("sample_photo.png — raster bytes, same DSL call:") {
                fontSize = 12.sp; color = PdfColor.DarkGray
            }
            drawable(Res.drawable.sample_photo, width = 320.dp)
        }
    }
}
