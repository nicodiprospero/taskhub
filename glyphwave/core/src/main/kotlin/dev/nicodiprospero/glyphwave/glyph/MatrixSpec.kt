package dev.nicodiprospero.glyphwave.glyph

/**
 * Hardware facts about the Glyph Matrix.
 *
 * [DEFAULT_LENGTH] is the ONLY place the number 25 may appear. At runtime the app asks the SDK
 * (`Common.getDeviceMatrixLength()`) and only falls back to this constant.
 */
object MatrixSpec {
    /** Phone (3) matrix side length (25x25 grid, 489 physical LEDs). */
    const val DEFAULT_LENGTH = 25

    /**
     * Maximum per-pixel value accepted by `setMatrixFrame(IntArray)`.
     * Taken from the SDK's own renderer (`GlyphMatrixUtils.MAX_BRIGHTNESS = 4095`, i.e. 255 × 16).
     * On-device confirmation: DECISIONS.md D-004.
     */
    const val MAX_VALUE = 4095

    /** Sanitises whatever the SDK reports; anything unusable falls back to [DEFAULT_LENGTH]. */
    fun resolveLength(reported: Int?): Int =
        if (reported != null && reported in 5..64) reported else DEFAULT_LENGTH
}
