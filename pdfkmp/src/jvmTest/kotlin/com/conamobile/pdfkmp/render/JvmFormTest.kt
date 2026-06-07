package com.conamobile.pdfkmp.render

import com.conamobile.pdfkmp.pdf
import com.conamobile.pdfkmp.unit.dp
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.interactive.form.PDCheckBox
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * JVM-backend tests for AcroForm fields. Each document is produced through the
 * public `pdf { }` DSL (which resolves the PdfBox driver on this source set)
 * and re-parsed with PdfBox to assert the interactive fields actually landed
 * in the bytes.
 */
class JvmFormTest {

    @Test
    fun textFieldsAndCheckBoxesLandInAcroForm() {
        val bytes = pdf {
            page {
                textField(name = "fullName", width = 200.dp, value = "Ada Lovelace")
                textField(name = "bio", width = 200.dp, height = 60.dp, multiline = true)
                checkBox(name = "subscribed", checked = true)
                checkBox(name = "terms", checked = false)
            }
        }.toByteArray()

        Loader.loadPDF(bytes).use { loaded ->
            // Read the AcroForm WITHOUT the default fixup: PdfBox's no-arg
            // getAcroForm() applies PDAcroFormDefaultFixup, which generates
            // appearance streams and then flips NeedAppearances back to false.
            // We want to observe what we actually wrote.
            val acroForm = assertNotNull(
                loaded.documentCatalog.getAcroForm(null),
                "document has no AcroForm",
            )
            assertTrue(acroForm.needAppearances, "NeedAppearances should be set")
            assertNotNull(acroForm.defaultResources, "AcroForm has no default resources")

            // Text field value round-trips.
            val fullName = acroForm.getField("fullName") as? PDTextField
            assertNotNull(fullName, "fullName text field missing")
            assertEquals("Ada Lovelace", fullName.value)

            // Multiline flag round-trips.
            val bio = acroForm.getField("bio") as? PDTextField
            assertNotNull(bio, "bio text field missing")
            assertTrue(bio.isMultiline, "bio should be multiline")

            // Checkbox states round-trip.
            val subscribed = acroForm.getField("subscribed") as? PDCheckBox
            assertNotNull(subscribed, "subscribed checkbox missing")
            assertTrue(subscribed.isChecked, "subscribed should be checked")

            val terms = acroForm.getField("terms") as? PDCheckBox
            assertNotNull(terms, "terms checkbox missing")
            assertTrue(!terms.isChecked, "terms should be unchecked")
        }
    }

    @Test
    fun duplicateFieldNamesAreDisambiguated() {
        val bytes = pdf {
            page {
                textField(name = "dup", width = 100.dp, value = "first")
                textField(name = "dup", width = 100.dp, value = "second")
            }
        }.toByteArray()

        Loader.loadPDF(bytes).use { loaded ->
            val acroForm = assertNotNull(loaded.documentCatalog.acroForm)
            // Both fields survive: the second is suffixed -2 to avoid merging.
            val first = acroForm.getField("dup") as? PDTextField
            val second = acroForm.getField("dup-2") as? PDTextField
            assertNotNull(first, "first 'dup' field missing")
            assertNotNull(second, "disambiguated 'dup-2' field missing")
            assertEquals("first", first.value)
            assertEquals("second", second.value)
        }
    }
}
