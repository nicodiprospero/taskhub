package dev.nicodiprospero.glyphwave.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.nicodiprospero.glyphwave.TAG
import dev.nicodiprospero.glyphwave.debug.GlyphEvent
import dev.nicodiprospero.glyphwave.engine.Engine
import dev.nicodiprospero.glyphwave.media.MediaBridge
import dev.nicodiprospero.glyphwave.media.MediaListenerService

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GlyphWaveTheme {
                DebugScreen(
                    onOpenToysManager = ::openGlyphToysManager,
                    onGrantNotificationAccess = ::openNotificationAccess,
                    onCopy = ::copyToClipboard,
                    onMark = { label ->
                        val e = Engine.glyphEvents.record(
                            SystemClock.elapsedRealtimeNanos(), GlyphEvent.Kind.LIFECYCLE, "MARK $label",
                        )
                        Log.i(TAG, "EVT ${e.format()}")
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Picks up a freshly granted / revoked notification access when returning from Settings.
        MediaBridge.refresh()
    }

    private fun openNotificationAccess() {
        val component = ComponentName(this, MediaListenerService::class.java)
        val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component.flattenToString())
        try {
            startActivity(detail)
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "listener detail settings not found, falling back", e)
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
    }

    private fun openGlyphToysManager() {
        val intent = Intent().setComponent(TOYS_MANAGER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "Glyph Toys manager not found", e)
            Toast.makeText(
                this,
                "Glyph Toys manager not found. Open Settings → Glyph Interface → Glyph Toys.",
                Toast.LENGTH_LONG,
            ).show()
        } catch (e: SecurityException) {
            Log.w(TAG, "Glyph Toys manager not accessible", e)
            Toast.makeText(this, "Open Settings → Glyph Interface → Glyph Toys.", Toast.LENGTH_LONG).show()
        }
    }

    private fun copyToClipboard(label: String, text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
    }

    companion object {
        val TOYS_MANAGER = ComponentName(
            "com.nothing.thirdparty",
            "com.nothing.thirdparty.matrix.toys.manager.ToysManagerActivity",
        )
        const val DEBUG_CMD = "adb shell settings put global nt_glyph_interface_debug_enable 1"
    }
}
