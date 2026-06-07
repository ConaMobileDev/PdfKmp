package com.conamobile.pdfkmp.sampledesktop

import com.conamobile.pdfkmp.PdfDocument
import com.conamobile.pdfkmp.pdf
import com.conamobile.pdfkmp.geometry.Padding
import com.conamobile.pdfkmp.style.PdfColor
import com.conamobile.pdfkmp.unit.dp
import com.conamobile.pdfkmp.unit.sp

// Demo passwords, surfaced in the list UI so the document can actually be
// opened. The user password unlocks viewing; the owner password lifts the
// print/copy restrictions in readers that honour permission flags.
internal const val ENCRYPTED_SAMPLE_USER_PASSWORD = "1234"
internal const val ENCRYPTED_SAMPLE_OWNER_PASSWORD = "owner-secret"

/**
 * AES-256-encrypted document demonstrating the `encryption { }` DSL on the
 * JVM/PdfBox backend — the only backend with full support (passwords + all
 * three permission flags). Lives in its own file because the PdfKmp `dp`/`sp`
 * units would clash with the Compose ones imported by `Main.kt`.
 */
internal fun encryptedSampleDoc(): PdfDocument = pdf {
    metadata { title = "Encrypted PDF — PdfKmp" }

    encryption {
        ownerPassword = ENCRYPTED_SAMPLE_OWNER_PASSWORD
        userPassword = ENCRYPTED_SAMPLE_USER_PASSWORD
        allowPrinting = false
        allowCopying = false
        allowModification = false
    }

    page {
        spacing = 14.dp

        text("AES-256 Encrypted Document") {
            fontSize = 26.sp
            bold = true
            color = PdfColor.Blue
        }
        text(
            "This PDF was encrypted at generation time by the JVM / PdfBox " +
                "backend. You are reading it because the viewer was given the " +
                "user password.",
        )
        divider()

        card(
            background = PdfColor.fromRgb(0xF5F5F5),
            cornerRadius = 10.dp,
            padding = Padding.all(14.dp),
        ) {
            text("Passwords") { bold = true; fontSize = 14.sp }
            text("User password (opens the file):  $ENCRYPTED_SAMPLE_USER_PASSWORD")
            text("Owner password (full access):    $ENCRYPTED_SAMPLE_OWNER_PASSWORD")
        }

        text("Permissions baked into this file") { bold = true; fontSize = 14.sp }
        bulletList(
            items = listOf(
                "Printing — denied",
                "Copying text & graphics — denied",
                "Modifying the document — denied",
            ),
        )

        text(
            "Try it outside the sample: use the download button in the topbar, " +
                "then open the saved file in Chrome or Adobe Reader — it will " +
                "prompt for the password above.",
        ) {
            fontSize = 11.sp
            color = PdfColor.Gray
        }
    }
}
