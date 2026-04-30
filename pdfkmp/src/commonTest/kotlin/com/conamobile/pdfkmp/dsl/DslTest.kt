package com.conamobile.pdfkmp.dsl

import com.conamobile.pdfkmp.geometry.PageSize
import com.conamobile.pdfkmp.geometry.Padding
import com.conamobile.pdfkmp.layout.PageBreakStrategy
import com.conamobile.pdfkmp.node.TextNode
import com.conamobile.pdfkmp.style.FontStyle
import com.conamobile.pdfkmp.style.FontWeight
import com.conamobile.pdfkmp.style.PdfColor
import com.conamobile.pdfkmp.style.PdfFont
import com.conamobile.pdfkmp.unit.dp
import com.conamobile.pdfkmp.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Verifies the DSL builders produce the expected resolved [DocumentSpec]. */
class DslTest {

    @Test
    fun simpleDocument_buildsExpectedTree() {
        val scope = DocumentScope().apply {
            metadata { title = "Hello"; author = "Tester" }
            page {
                text("First")
                text("Second") { bold = true }
            }
        }
        val spec = scope.build()

        assertEquals("Hello", spec.metadata.title)
        assertEquals("Tester", spec.metadata.author)
        assertEquals(1, spec.pages.size)

        val children = spec.pages[0].content.children
        assertEquals(2, children.size)
        assertEquals("First", (children[0] as TextNode).text)
        assertEquals(FontWeight.Normal, (children[0] as TextNode).style.fontWeight)
        assertEquals("Second", (children[1] as TextNode).text)
        assertEquals(FontWeight.Bold, (children[1] as TextNode).style.fontWeight)
    }

    @Test
    fun textBlock_inheritsParentStyle_andOverridesIndividualProperties() {
        val scope = DocumentScope().apply {
            defaultTextStyle = defaultTextStyle.copy(
                fontSize = 14.sp,
                color = PdfColor.DarkGray,
            )
            page {
                text("Inherits everything")
                text("Overrides color only") { color = PdfColor.Red }
                text("Overrides everything") {
                    fontSize = 22.sp
                    italic = true
                    color = PdfColor.Blue
                }
            }
        }
        val children = scope.build().pages[0].content.children
        val first = children[0] as TextNode
        val second = children[1] as TextNode
        val third = children[2] as TextNode

        assertEquals(14.sp, first.style.fontSize)
        assertEquals(PdfColor.DarkGray, first.style.color)

        assertEquals(14.sp, second.style.fontSize)
        assertEquals(PdfColor.Red, second.style.color)

        assertEquals(22.sp, third.style.fontSize)
        assertEquals(FontStyle.Italic, third.style.fontStyle)
        assertEquals(PdfColor.Blue, third.style.color)
    }

    @Test
    fun page_inheritsDocumentDefaults_butCanOverrideThem() {
        val scope = DocumentScope().apply {
            defaultPagePadding = Padding.all(50.dp)
            defaultPageBreakStrategy = PageBreakStrategy.Slice
            page { text("Inherits") }
            page {
                padding = Padding.Zero
                pageBreakStrategy = PageBreakStrategy.MoveToNextPage
                text("Overrides")
            }
        }
        val pages = scope.build().pages
        assertEquals(Padding.all(50.dp), pages[0].padding)
        assertEquals(PageBreakStrategy.Slice, pages[0].pageBreakStrategy)
        assertEquals(Padding.Zero, pages[1].padding)
        assertEquals(PageBreakStrategy.MoveToNextPage, pages[1].pageBreakStrategy)
    }

    @Test
    fun customFont_referencedByText_isCollectedIntoDocumentSpec() {
        val custom = PdfFont.Custom("Acme", byteArrayOf(1, 2, 3))
        val scope = DocumentScope().apply {
            page {
                text("Default font")
                text("Custom font") { font = custom }
            }
        }
        val spec = scope.build()
        assertEquals(1, spec.customFonts.size)
        assertEquals("Acme", spec.customFonts[0].name)
    }

    @Test
    fun explicitlyRegisteredCustomFont_isKept_evenIfUnused() {
        val custom = PdfFont.Custom("OnlyRegistered", byteArrayOf(9, 9))
        val scope = DocumentScope().apply {
            registerFont(custom)
            page { text("No custom font here") }
        }
        val spec = scope.build()
        assertTrue(spec.customFonts.any { it.name == "OnlyRegistered" })
    }

    @Test
    fun pageSize_canBeOverriddenPerPage() {
        val scope = DocumentScope().apply {
            page { text("A4") }
            page(PageSize.Letter) { text("Letter") }
        }
        val pages = scope.build().pages
        assertEquals(PageSize.A4, pages[0].size)
        assertEquals(PageSize.Letter, pages[1].size)
    }
}
