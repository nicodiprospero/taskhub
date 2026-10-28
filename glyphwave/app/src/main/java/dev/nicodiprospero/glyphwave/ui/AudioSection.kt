package dev.nicodiprospero.glyphwave.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.nicodiprospero.glyphwave.GlyphWaveApp
import dev.nicodiprospero.glyphwave.TAG
import dev.nicodiprospero.glyphwave.audio.AudioEngineService
import dev.nicodiprospero.glyphwave.audio.SourceId
import dev.nicodiprospero.glyphwave.engine.Engine
import dev.nicodiprospero.glyphwave.engine.YT_MUSIC_PACKAGE
import dev.nicodiprospero.glyphwave.settings.SettingsRepository
import kotlinx.coroutines.launch

/** Audio engine controls + live source statistics (Phase 2 source evaluation). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AudioSection() {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as GlyphWaveApp
    val scope = rememberCoroutineScope()
    val status by Engine.audio.collectAsStateWithLifecycle()
    val source by app.settings.audioSource.collectAsStateWithLifecycle(SettingsRepository.DEFAULT_AUDIO_SOURCE)
    val onlyYt by app.settings.captureOnlyYtMusic.collectAsStateWithLifecycle(false)

    // Bumped after permission results so the granted() checks below recompose.
    var permissionEpoch by remember { mutableIntStateOf(0) }
    val micGranted = remember(permissionEpoch) { ctx.granted(Manifest.permission.RECORD_AUDIO) }
    val notifGranted = remember(permissionEpoch) { ctx.granted(Manifest.permission.POST_NOTIFICATIONS) }

    val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val data = res.data
        if (res.resultCode == Activity.RESULT_OK && data != null) {
            val uid = if (onlyYt) ytMusicUid(ctx) else null
            AudioEngineService.startPlaybackCapture(ctx, res.resultCode, data, uid)
        } else {
            Log.i(TAG, "AUDIO screen-capture consent denied")
            Toast.makeText(ctx, "Playback capture needs the screen-capture consent", Toast.LENGTH_LONG).show()
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionEpoch++
    }

    fun startEngine() {
        if (!micGranted) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        when (source) {
            SourceId.VISUALIZER -> AudioEngineService.startVisualizer(ctx)
            SourceId.PLAYBACK_CAPTURE -> consentLauncher.launch(
                ctx.getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent(),
            )
        }
    }

    Section("AUDIO ENGINE") {
        StatusRow("Record audio", if (micGranted) "GRANTED" else "MISSING", micGranted)
        StatusRow("Notifications", if (notifGranted) "GRANTED" else "MISSING", notifGranted)
        if (!micGranted || !notifGranted) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!micGranted) GwOutlined("GRANT MIC") { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                if (!notifGranted) GwOutlined("GRANT NOTIFICATIONS") { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
            }
        }

        Text("SOURCE", style = GwType.Label)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SourceId.entries.forEach { id ->
                Chip(if (id == SourceId.VISUALIZER) "A · Visualizer" else "B · Playback capture", selected = id == source) {
                    scope.launch { app.settings.setAudioSource(id) }
                }
            }
        }
        if (source == SourceId.PLAYBACK_CAPTURE) {
            Chip("Only YT Music audio", selected = onlyYt) {
                scope.launch { app.settings.setCaptureOnlyYtMusic(!onlyYt) }
            }
        }

        Button(
            onClick = { if (status.running) AudioEngineService.stop(ctx) else startEngine() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = if (status.running) GwColors.Line else GwColors.Red),
            shape = RoundedCornerShape(50),
        ) {
            Text(if (status.running) "STOP ENGINE" else "START ENGINE", style = GwType.Label.copy(color = GwColors.White))
        }

        StatusRow("Engine", if (status.running) "RUNNING · ${status.source?.name}" else "STOPPED", status.running)
        if (status.running) {
            LinearProgressIndicator(
                progress = { status.level },
                modifier = Modifier.fillMaxWidth(),
                color = GwColors.White,
                trackColor = GwColors.Line,
            )
            Text(
                "%.1f blocks/s · %d samples @ %d Hz\nrms %.4f · peak %.3f · level %.2f · silent %d ms".format(
                    status.blockRate, status.blockSize, status.sampleRate, status.rms, status.peak, status.level, status.silentForMs,
                ),
                style = GwType.Mono,
            )
        }
        if (status.silentWhilePlaying) {
            Text(
                "Source delivers SILENCE while YouTube Music is playing. Try the other source.",
                style = GwType.Body.copy(color = GwColors.Red),
            )
        }
        status.error?.let { Text(it, style = GwType.Mono.copy(color = GwColors.Red)) }
    }
}

private fun Context.granted(permission: String) =
    checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

private fun ytMusicUid(ctx: Context): Int? = try {
    ctx.packageManager.getApplicationInfo(YT_MUSIC_PACKAGE, 0).uid
} catch (_: PackageManager.NameNotFoundException) {
    null
}
