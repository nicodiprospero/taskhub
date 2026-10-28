package dev.nicodiprospero.glyphwave.glyph

/**
 * Phase-0 diagnostic frames. Each one answers a concrete question on the real hardware.
 * All output is in raw SDK units (0..[MatrixSpec.MAX_VALUE]) and already masked.
 */
enum class TestPattern(val title: String, val question: String) {
    VALUE_RANGE(
        "Value range",
        "Top: 0→4095 ramp left→right. Bottom-left = 255, bottom-right = 4095. " +
            "If the range is 0..4095 the ramp brightens smoothly across the whole width and bottom-left is " +
            "clearly dimmer than bottom-right. If both bottom halves look equal, the device clamps at 255.",
    ),
    LOW_END(
        "Low-end steps",
        "Eight vertical bands: 0, 8, 32, 128, 512, 1024, 2048, 4095. Count how many bands you can tell apart " +
            "— tells us the dimmest usable idle level.",
    ),
    RIM(
        "Mask rim",
        "Only the outermost LED of the circle is lit, plus the centre LED. Expect a clean 1-LED ring with no " +
            "gaps and nothing missing — proves the CircleMask matches the hardware.",
    ),
    ORIENTATION(
        "Orientation",
        "Arrow pointing UP and a dot in the LEFT part of the top rows, as seen from the back of the phone held " +
            "upright. Tells us whether (0,0) is top-left.",
    ),
    AUDIO_LEVEL(
        "Audio level",
        "Live (30 fps): a disc that grows with loudness, auto-gained. Used to judge audio-source latency and " +
            "whether capture keeps running with the screen off. A single dim dot = no audio / engine stopped.",
    ),
    ;

    /** Patterns that change over time and need the render loop. */
    val animated: Boolean get() = this == AUDIO_LEVEL

    fun next(): TestPattern = entries[(ordinal + 1) % entries.size]
}

object TestPatterns {

    /**
     * Renders [pattern] into [canvas] (cleared first) and applies [mask]. [level] (0..1) only drives
     * [TestPattern.AUDIO_LEVEL]. Allocation-free.
     */
    fun render(pattern: TestPattern, canvas: MatrixCanvas, mask: CircleMask, level: Float = 0f) {
        require(canvas.length == mask.length)
        canvas.clear()
        val n = canvas.length
        val mid = n / 2
        val max = canvas.maxValue
        when (pattern) {
            TestPattern.VALUE_RANGE -> {
                for (y in 0 until mid) for (x in 0 until n) {
                    canvas.setRaw(x, y, (x.toLong() * max / (n - 1)).toInt())
                }
                for (y in mid + 1 until n) {
                    for (x in 0 until mid) canvas.setRaw(x, y, 255)
                    for (x in mid + 1 until n) canvas.setRaw(x, y, max)
                }
            }
            TestPattern.LOW_END -> {
                val steps = LOW_END_STEPS
                for (x in 0 until n) {
                    val band = (x * steps.size / n).coerceAtMost(steps.size - 1)
                    for (y in 0 until n) canvas.setRaw(x, y, steps[band].coerceAtMost(max))
                }
            }
            TestPattern.RIM -> {
                for (y in 0 until n) for (x in 0 until n) if (mask.isEdge(x, y)) canvas.setRaw(x, y, max)
                canvas.setRaw(mid, mid, max)
            }
            TestPattern.ORIENTATION -> {
                // Arrow: vertical shaft in the centre column, head at the top.
                for (y in 3 until n - 3) canvas.setRaw(mid, y, max)
                for (d in 1..4) {
                    canvas.setRaw(mid - d, 3 + d, max)
                    canvas.setRaw(mid + d, 3 + d, max)
                }
                // Marker dot: left side, upper area (inside the circle).
                canvas.setRaw(mid - 7, 4, max)
                canvas.setRaw(mid - 6, 4, max)
                canvas.setRaw(mid - 7, 5, max)
                canvas.setRaw(mid - 6, 5, max)
            }
            TestPattern.AUDIO_LEVEL -> {
                val r = level.coerceIn(0f, 1f) * (n / 2f)
                val r2 = r * r
                for (y in 0 until n) for (x in 0 until n) {
                    val dx = (x - mid).toFloat()
                    val dy = (y - mid).toFloat()
                    if (dx * dx + dy * dy <= r2) canvas.setRaw(x, y, max)
                }
                // Idle marker: centre LED at a low but visible level (value 32, see D-004).
                if (r < 1f) canvas.setRaw(mid, mid, 32)
            }
        }
        mask.apply(canvas.pixels)
    }

    val LOW_END_STEPS = intArrayOf(0, 8, 32, 128, 512, 1024, 2048, 4095)
}
