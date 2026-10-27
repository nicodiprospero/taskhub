package dev.nicodiprospero.glyphwave.glyph

/**
 * Which cells of the square n×n grid have a physical LED.
 *
 * The Glyph Matrix is round: a cell (x, y) exists when `(2dx)² + (2dy)² ≤ n²`, with dx/dy measured
 * from the centre cell. For n = 25 this yields 489 LEDs with row widths
 * 7, 11, 15, 17, 19, 21, 21, 23, 23, 25×7, … — identical, cell by cell, to the LED allocation
 * diagram in Nothing's GlyphMatrix Developer Kit (see CircleMaskTest).
 */
class CircleMask(val length: Int) {

    private val lit = BooleanArray(length * length)

    /** Number of physical LEDs. */
    val ledCount: Int

    init {
        require(length > 0) { "length must be > 0" }
        val c = length / 2
        // Integer form of (dx)² + (dy)² ≤ (n/2)², exact for odd n. For even n the centre is between
        // cells, so measure from the cell-centre midpoint instead.
        var count = 0
        for (y in 0 until length) {
            for (x in 0 until length) {
                val dx2 = if (length % 2 == 1) 2 * (x - c) else 2 * x - (length - 1)
                val dy2 = if (length % 2 == 1) 2 * (y - c) else 2 * y - (length - 1)
                if (dx2 * dx2 + dy2 * dy2 <= length * length) {
                    lit[y * length + x] = true
                    count++
                }
            }
        }
        ledCount = count
    }

    fun contains(x: Int, y: Int): Boolean =
        x in 0 until length && y in 0 until length && lit[y * length + x]

    fun contains(index: Int): Boolean = lit[index]

    /** Zeroes every cell without a physical LED. In place, allocation-free. */
    fun apply(frame: IntArray) {
        for (i in frame.indices) if (!lit[i]) frame[i] = 0
    }

    /** True when the LED is on the rim (has a 4-neighbour that is not an LED). */
    fun isEdge(x: Int, y: Int): Boolean =
        contains(x, y) && (!contains(x - 1, y) || !contains(x + 1, y) || !contains(x, y - 1) || !contains(x, y + 1))

    /** First and last lit column of row [y], or null if the row is empty. */
    fun rowSpan(y: Int): IntRange? {
        var first = -1
        var last = -1
        for (x in 0 until length) if (contains(x, y)) {
            if (first < 0) first = x
            last = x
        }
        return if (first < 0) null else first..last
    }
}
