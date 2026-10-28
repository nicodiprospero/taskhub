package dev.nicodiprospero.glyphwave.glyph

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TestPatternsTest {

    private val mask = CircleMask(MatrixSpec.DEFAULT_LENGTH)
    private val canvas = MatrixCanvas(MatrixSpec.DEFAULT_LENGTH)

    @Test
    fun `every pattern stays in range and respects the mask`() {
        for (p in TestPattern.entries) {
            TestPatterns.render(p, canvas, mask, level = 0.6f)
            canvas.pixels.forEachIndexed { i, v ->
                assertTrue("$p value $v", v in 0..MatrixSpec.MAX_VALUE)
                if (!mask.contains(i)) assertEquals("$p leaks outside mask at $i", 0, v)
            }
            assertTrue("$p draws something", canvas.pixels.any { it > 0 })
        }
    }

    @Test
    fun `value range pattern ramps to max and has both reference halves`() {
        TestPatterns.render(TestPattern.VALUE_RANGE, canvas, mask)
        val n = canvas.length
        val mid = n / 2
        // Row mid-1 is the widest ramp row: full width 0..max.
        val row = (0 until n).map { canvas.pixels[(mid - 1) * n + it] }
        assertEquals(0, row.first())
        assertEquals(MatrixSpec.MAX_VALUE, row.last())
        assertTrue(row.zipWithNext().all { (a, b) -> b >= a })
        assertEquals(255, canvas.pixels[(mid + 3) * n + 3])
        assertEquals(MatrixSpec.MAX_VALUE, canvas.pixels[(mid + 3) * n + n - 4])
    }

    @Test
    fun `next cycles through all patterns`() {
        var p = TestPattern.entries.first()
        repeat(TestPattern.entries.size) { p = p.next() }
        assertEquals(TestPattern.entries.first(), p)
    }

    @Test
    fun `resolveLength falls back to the default`() {
        assertEquals(MatrixSpec.DEFAULT_LENGTH, MatrixSpec.resolveLength(null))
        assertEquals(MatrixSpec.DEFAULT_LENGTH, MatrixSpec.resolveLength(0))
        assertEquals(13, MatrixSpec.resolveLength(13))
    }
}
