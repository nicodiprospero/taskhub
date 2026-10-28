package dev.nicodiprospero.glyphwave.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.nicodiprospero.glyphwave.debug.GlyphEvent
import dev.nicodiprospero.glyphwave.engine.Engine
import dev.nicodiprospero.glyphwave.glyph.CircleMask
import dev.nicodiprospero.glyphwave.glyph.MatrixCanvas
import dev.nicodiprospero.glyphwave.glyph.MatrixSpec
import dev.nicodiprospero.glyphwave.glyph.TestPattern
import dev.nicodiprospero.glyphwave.glyph.TestPatterns

/** Diagnostics screen: media session, toy status, test pattern preview + picker, raw Glyph event log. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DebugScreen(
    onOpenToysManager: () -> Unit,
    onGrantNotificationAccess: () -> Unit,
    onCopy: (label: String, text: String) -> Unit,
    onMark: (String) -> Unit,
) {
    val status by Engine.toyStatus.collectAsStateWithLifecycle()
    val pattern by Engine.testPattern.collectAsStateWithLifecycle()
    val events by Engine.glyphEvents.events.collectAsStateWithLifecycle()

    val length = status.matrixLength ?: MatrixSpec.DEFAULT_LENGTH
    val mask = remember(length) { CircleMask(length) }
    // Same core code the toy uses, so the preview is exactly what goes to setMatrixFrame.
    val frame = remember(length, pattern) {
        MatrixCanvas(length).also { TestPatterns.render(pattern, it, mask) }.pixels
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(GwColors.Black)
            .safeDrawingPadding()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            Text("GLYPHWAVE", style = GwType.Heading)
            Text("PHASE 1 · DIAGNOSTICS", style = GwType.Label)
        }

        item { MediaSection(onGrantNotificationAccess = onGrantNotificationAccess) }

        item {
            Section("TOY") {
                StatusRow("Glyph Toy", status.state.name, status.state == Engine.ToyState.CONNECTED)
                StatusRow("Registered", if (status.registered) "YES" else "NO", status.registered)
                StatusRow("Matrix", status.matrixLength?.let { "${it}×$it · ${mask.ledCount} LEDs" } ?: "—", status.matrixLength != null)
                status.lastError?.let { Text(it, style = GwType.Mono.copy(color = GwColors.Red)) }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onOpenToysManager,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = GwColors.Red),
                    shape = RoundedCornerShape(50),
                ) { Text("ADD TO GLYPH TOYS", style = GwType.Label.copy(color = GwColors.White)) }
                Spacer(Modifier.height(8.dp))
                Text("Glyph debug mode (expires after 48 h):", style = GwType.Label)
                Text(MainActivity.DEBUG_CMD, style = GwType.Mono)
                GwOutlined("COPY COMMAND") { onCopy("adb", MainActivity.DEBUG_CMD) }
            }
        }

        item {
            Section("TEST PATTERN") {
                MatrixPreview(
                    frame = frame,
                    mask = mask,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 360.dp)
                        .align(Alignment.CenterHorizontally)
                        .padding(8.dp),
                )
                Text(pattern.title.uppercase(), style = GwType.Label.copy(color = GwColors.White))
                Text(pattern.question, style = GwType.Body)
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TestPattern.entries.forEach { p ->
                        Chip(p.title, selected = p == pattern) { Engine.testPattern.value = p }
                    }
                }
                Text("Long press on the Glyph Button also cycles patterns.", style = GwType.Label)
            }
        }

        item {
            Section("RAW GLYPH EVENTS") {
                Text(
                    "Tap a marker, then do the gesture on the Glyph Button: 1 long press · 2 quick long presses · " +
                        "3 s hold · 6 s hold.",
                    style = GwType.Body,
                )
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("1xLONG", "2xLONG", "HOLD3S", "HOLD6S").forEach { Chip(it, selected = false) { onMark(it) } }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GwOutlined("COPY LOG") { onCopy("glyph-events", Engine.glyphEvents.dump()) }
                    GwOutlined("CLEAR") { Engine.glyphEvents.clear() }
                }
                if (events.isEmpty()) Text("No events yet.", style = GwType.Label)
            }
        }

        items(events.asReversed(), key = { it.seq }) { e -> EventRow(e) }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
internal fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, GwColors.Line), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, style = GwType.Label)
        content()
    }
}

@Composable
internal fun StatusRow(label: String, value: String, ok: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .background(if (ok) GwColors.Green else GwColors.Red, CircleShape),
        )
        Spacer(Modifier.size(10.dp))
        Text(label, style = GwType.Body, modifier = Modifier.weight(1f))
        Text(value, style = GwType.Mono)
    }
}

@Composable
internal fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, if (selected) GwColors.Red else GwColors.Line),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) GwColors.Red.copy(alpha = 0.15f) else Color.Transparent,
        ),
    ) { Text(text.uppercase(), style = GwType.Label.copy(color = GwColors.White)) }
}

@Composable
internal fun GwOutlined(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, GwColors.Line),
    ) { Text(text, style = GwType.Label.copy(color = GwColors.White)) }
}

@Composable
private fun EventRow(e: GlyphEvent) {
    val color = when (e.kind) {
        GlyphEvent.Kind.BUTTON -> GwColors.White
        GlyphEvent.Kind.LIFECYCLE -> GwColors.Grey
        GlyphEvent.Kind.UNKNOWN -> GwColors.Red
    }
    Text(e.format(), style = GwType.Mono.copy(color = color))
}
