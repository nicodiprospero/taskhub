package dev.nicodiprospero.glyphwave.glyph

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CircleMaskTest {

    /**
     * Lit LEDs per row of the Phone (3) matrix, read cell by cell from
     * GlyphMatrix-Developer-Kit/image/23111_25111_LED_allocation.svg.
     */
    private val phone3RowWidths = intArrayOf(
        7, 11, 15, 17, 19, 21, 21, 23, 23, 25, 25, 25, 25, 25, 25, 25, 23, 23, 21, 21, 19, 17, 15, 11, 7,
    )

    @Test
    fun `phone 3 mask matches the kit LED allocation diagram`() {
        val mask = CircleMask(MatrixSpec.DEFAULT_LENGTH)
        assertEquals(489, mask.ledCount)
        for (y in 0 until mask.length) {
            val span = mask.rowSpan(y)!!
            val width = phone3RowWidths[y]
            assertEquals("row $y width", width, span.last - span.first + 1)
            // Rows are centred and contiguous.
            assertEquals("row $y start", (mask.length - width) / 2, span.first)
            for (x in span) assertTrue("row $y col $x", mask.contains(x, y))
        }
    }

    @Test
    fun `corners are dark and centre is lit`() {
        val mask = CircleMask(MatrixSpec.DEFAULT_LENGTH)
        val last = mask.length - 1
        for ((x, y) in listOf(0 to 0, last to 0, 0 to last, last to last)) assertFalse(mask.contains(x, y))
        assertTrue(mask.contains(mask.length / 2, mask.length / 2))
    }

    @Test
    fun `mask is symmetric on both axes and the diagonal`() {
        val mask = CircleMask(MatrixSpec.DEFAULT_LENGTH)
        val n = mask.length
        for (y in 0 until n) for (x in 0 until n) {
            val v = mask.contains(x, y)
            assertEquals(v, mask.contains(n - 1 - x, y))
            assertEquals(v, mask.contains(x, n - 1 - y))
            assertEquals(v, mask.contains(y, x))
        }
    }

    @Test
    fun `phone 4a pro 13x13 has 5 LEDs on the top row`() {
        // Same formula generalises to the smaller matrix in the kit diagram.
        val mask = CircleMask(13)
        assertEquals(4..8, mask.rowSpan(0))
        assertEquals(0..12, mask.rowSpan(6))
    }

    @Test
    fun `apply zeroes only cells without LEDs`() {
        val mask = CircleMask(MatrixSpec.DEFAULT_LENGTH)
        val frame = IntArray(mask.length * mask.length) { 100 }
        mask.apply(frame)
        assertEquals(489, frame.count { it == 100 })
        assertEquals(625 - 489, frame.count { it == 0 })
    }

    @Test
    fun `rim is one LED wide and closed`() {
        val mask = CircleMask(MatrixSpec.DEFAULT_LENGTH)
        val n = mask.length
        // Every row has at least its two end LEDs on the rim.
        for (y in 0 until n) {
            val span = mask.rowSpan(y)!!
            assertTrue(mask.isEdge(span.first, y))
            assertTrue(mask.isEdge(span.last, y))
        }
        // Centre is never on the rim.
        assertFalse(mask.isEdge(n / 2, n / 2))
    }
}
