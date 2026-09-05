package it.krpng.cassa.feature.importmenu

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OdsDocumentSelectionTest {
    @Test
    fun `picker accepts only the OpenDocument spreadsheet MIME type`() {
        val mimeTypes = OdsDocumentSelection.pickerMimeTypes()

        assertArrayEquals(arrayOf(OdsDocumentSelection.MIME_TYPE), mimeTypes)
        assertFalse(mimeTypes.any { it.contains('*') })
        assertFalse(mimeTypes.any { it.contains("excel", ignoreCase = true) })
        assertTrue(mimeTypes.single().endsWith("opendocument.spreadsheet"))
    }

    @Test
    fun `picker MIME array is returned defensively`() {
        val first = OdsDocumentSelection.pickerMimeTypes()
        first[0] = "*/*"

        assertArrayEquals(
            arrayOf(OdsDocumentSelection.MIME_TYPE),
            OdsDocumentSelection.pickerMimeTypes(),
        )
    }
}
