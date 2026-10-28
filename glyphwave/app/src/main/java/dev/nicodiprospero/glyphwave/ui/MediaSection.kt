package dev.nicodiprospero.glyphwave.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.os.SystemClock
import dev.nicodiprospero.glyphwave.GlyphWaveApp
import dev.nicodiprospero.glyphwave.engine.MediaState
import dev.nicodiprospero.glyphwave.media.MediaBridge
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Media session diagnostics + transport controls (Phase 1). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MediaSection(onGrantNotificationAccess: () -> Unit) {
    val app = LocalContext.current.applicationContext as GlyphWaveApp
    val scope = rememberCoroutineScope()
    val media by MediaBridge.state.collectAsStateWithLifecycle()
    val art by MediaBridge.artBitmap.collectAsStateWithLifecycle()
    val policy by app.settings.mediaSourcePolicy.collectAsStateWithLifecycle(MediaBridge.SourcePolicy.YT_MUSIC_ONLY)

    // Ticks the extrapolated position while playing.
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(media.isPlaying) {
        while (media.isPlaying) {
            now = SystemClock.elapsedRealtime()
            delay(500)
        }
        now = SystemClock.elapsedRealtime()
    }

    Section("MEDIA SESSION") {
        val accessOk = media.access != MediaState.Access.NO_NOTIFICATION_ACCESS
        StatusRow("Notification access", if (accessOk) "GRANTED" else "MISSING", accessOk)
        if (!accessOk) {
            Text(
                "Needed to read YouTube Music's player. Grant it to \"GlyphWave media sync\".",
                style = GwType.Body,
            )
            Button(
                onClick = onGrantNotificationAccess,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = GwColors.Red),
                shape = RoundedCornerShape(50),
            ) { Text("GRANT NOTIFICATION ACCESS", style = GwType.Label.copy(color = GwColors.White)) }
            return@Section
        }

        val sessionText = when (media.access) {
            MediaState.Access.OK -> media.packageName ?: "—"
            MediaState.Access.YT_MUSIC_NOT_INSTALLED -> "YT MUSIC NOT INSTALLED"
            MediaState.Access.NO_SESSION -> "NONE — open YouTube Music and play"
            MediaState.Access.NO_NOTIFICATION_ACCESS -> "—"
        }
        StatusRow("Session", sessionText, media.access == MediaState.Access.OK)

        Text("SOURCE", style = GwType.Label)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("YT Music only", selected = policy == MediaBridge.SourcePolicy.YT_MUSIC_ONLY) {
                scope.launch { app.settings.setMediaSourcePolicy(MediaBridge.SourcePolicy.YT_MUSIC_ONLY) }
            }
            Chip("Any media app", selected = policy == MediaBridge.SourcePolicy.ANY_MEDIA_APP) {
                scope.launch { app.settings.setMediaSourcePolicy(MediaBridge.SourcePolicy.ANY_MEDIA_APP) }
            }
        }

        if (media.access != MediaState.Access.OK) return@Section

        Spacer(Modifier.size(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            art?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "Album art",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(8.dp)),
                )
                Spacer(Modifier.size(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(media.title ?: "—", style = GwType.Body)
                Text(media.artist ?: "—", style = GwType.Label.copy(color = GwColors.White))
                Text(media.album ?: "", style = GwType.Label)
            }
        }

        val pos = media.positionAt(now)
        if (media.durationMs > 0) {
            LinearProgressIndicator(
                progress = { pos.toFloat() / media.durationMs },
                modifier = Modifier.fillMaxWidth(),
                color = GwColors.White,
                trackColor = GwColors.Line,
            )
        }
        Text(
            "${media.playback.name} · ${formatTime(pos)} / ${formatTime(media.durationMs)}" +
                (media.art?.let { " · art ${it.size}×${it.size}" } ?: " · no art"),
            style = GwType.Mono,
        )

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GwOutlined("⏮") { MediaBridge.previous() }
            GwOutlined(if (media.isPlaying) "⏸" else "▶") { MediaBridge.playPause() }
            GwOutlined("⏭") { MediaBridge.next() }
            GwOutlined("+15 S") { MediaBridge.seekBy(15_000) }
        }

        Text("CUSTOM ACTIONS (${media.customActions.size})", style = GwType.Label)
        if (media.customActions.isEmpty()) {
            Text("None exposed by this session.", style = GwType.Body)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                media.customActions.forEach { a -> GwOutlined(a.name.uppercase()) { MediaBridge.sendCustomAction(a.action) } }
            }
            media.customActions.forEach { Text("${it.name} → ${it.action}", style = GwType.Mono) }
        }
    }
}

private fun formatTime(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}
