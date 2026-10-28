package dev.nicodiprospero.glyphwave.ui

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.nicodiprospero.glyphwave.GlyphWaveApp
import dev.nicodiprospero.glyphwave.engine.AudioFrame
import dev.nicodiprospero.glyphwave.engine.Engine
import dev.nicodiprospero.glyphwave.glyph.MatrixSpec
import dev.nicodiprospero.glyphwave.media.MediaBridge
import dev.nicodiprospero.glyphwave.modes.BandFocus
import dev.nicodiprospero.glyphwave.settings.SettingsRepository
import kotlinx.coroutines.launch

/**
 * Live 25×25 preview of exactly what goes to the matrix, plus mode / focus / display settings.
 * While the toy is rendering, the preview mirrors its frames; otherwise the app drives the same shared
 * renderer itself, so modes can be tuned without flipping the phone.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VisualizerSection() {
    val app = LocalContext.current.applicationContext as GlyphWaveApp
    val scope = rememberCoroutineScope()
    val modeId by app.settings.visualizerMode.collectAsStateWithLifecycle(SettingsRepository.DEFAULT_MODE)
    val focus by app.settings.bandFocus.collectAsStateWithLifecycle(BandFocus.FULL)
    val gamma by app.settings.gamma.collectAsStateWithLifecycle(SettingsRepository.DEFAULT_GAMMA)
    val brightness by app.settings.brightness.collectAsStateWithLifecycle(SettingsRepository.DEFAULT_BRIGHTNESS)
    val fps by app.settings.fps.collectAsStateWithLifecycle(30)
    val pattern by Engine.testPattern.collectAsStateWithLifecycle()

    val renderer = remember { Engine.renderer() }
    val frame = remember { IntArray(MatrixSpec.DEFAULT_LENGTH * MatrixSpec.DEFAULT_LENGTH) }
    val audio = remember { AudioFrame() }
    var tick by remember { mutableLongStateOf(0L) }
    var source by remember { mutableStateOf("—") }

    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { }
            val now = SystemClock.elapsedRealtime()
            // Toy rendered within the last 200 ms → mirror it; else drive the renderer here.
            if (now - Engine.matrixFrame.publishedAtMs < 200) {
                Engine.matrixFrame.readInto(frame)
                source = "MIRRORING GLYPH MATRIX"
            } else if (Engine.testPattern.value == null) {
                Engine.audioFrames.readInto(audio)
                renderer.render(audio, MediaBridge.state.value, now, frame)
                source = "IN-APP RENDER (toy not active)"
            } else {
                source = "TEST PATTERN — select the toy to see it"
            }
            tick++
        }
    }

    Section("VISUALIZER") {
        MatrixPreview(
            frame = frame,
            mask = renderer.mask,
            gamma = gamma,
            tick = tick,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 360.dp)
                .align(Alignment.CenterHorizontally)
                .padding(8.dp),
        )
        Text(source, style = GwType.Label)
        if (pattern != null) {
            Chip("Back to visualizer", selected = false) { Engine.testPattern.value = null }
        }

        Text("MODE", style = GwType.Label)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            renderer.modes.forEach { m ->
                Chip(m.name, selected = m.id == modeId) { scope.launch { app.settings.setVisualizerMode(m.id) } }
            }
        }
        Text("FOCUS (Radial · Bars)", style = GwType.Label)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BandFocus.entries.forEach { f ->
                Chip(f.label, selected = f == focus) { scope.launch { app.settings.setBandFocus(f) } }
            }
        }

        Text("BRIGHTNESS ${(brightness * 100).toInt()} %", style = GwType.Label)
        Slider(
            value = brightness,
            onValueChange = { v -> scope.launch { app.settings.setBrightness(v) } },
            valueRange = 0.05f..1f,
            colors = gwSliderColors(),
        )
        Text("GAMMA %.1f".format(gamma), style = GwType.Label)
        Slider(
            value = gamma,
            onValueChange = { v -> scope.launch { app.settings.setGamma((v * 10).toInt() / 10f) } },
            valueRange = 1f..3f,
            colors = gwSliderColors(),
        )
        Text("FRAME RATE", style = GwType.Label)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(30, 60).forEach { v -> Chip("$v fps", selected = fps == v) { scope.launch { app.settings.setFps(v) } } }
        }
        Text("Long press on the Glyph Button → next mode.", style = GwType.Label)
    }
}

@Composable
private fun gwSliderColors() = SliderDefaults.colors(
    thumbColor = GwColors.White,
    activeTrackColor = GwColors.Red,
    inactiveTrackColor = GwColors.Line,
)

