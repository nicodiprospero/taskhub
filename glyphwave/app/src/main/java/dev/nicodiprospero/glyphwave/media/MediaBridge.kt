package dev.nicodiprospero.glyphwave.media

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import dev.nicodiprospero.glyphwave.TAG
import dev.nicodiprospero.glyphwave.engine.MediaState
import dev.nicodiprospero.glyphwave.engine.YT_MUSIC_PACKAGE
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Tracks the YouTube Music media session (or any media app, per [policy]) and exposes it as a
 * [MediaState] StateFlow plus transport controls. Process-wide singleton; all session work happens
 * on the main thread.
 */
object MediaBridge {

    enum class SourcePolicy { YT_MUSIC_ONLY, ANY_MEDIA_APP }

    private val _state = MutableStateFlow(MediaState())
    val state: StateFlow<MediaState> = _state.asStateFlow()

    /** Full-resolution art for the companion UI (modes use the luminance grid in [MediaState.art]). */
    private val _artBitmap = MutableStateFlow<Bitmap?>(null)
    val artBitmap: StateFlow<Bitmap?> = _artBitmap.asStateFlow()

    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var app: Context? = null
    private var sessionManager: MediaSessionManager? = null
    private var listenerRegistered = false
    private var controller: MediaController? = null
    private var artJob: Job? = null
    private var lastArtSource: Bitmap? = null
    private var lastLoggedActions: List<MediaState.CustomAction>? = null

    var policy: SourcePolicy = SourcePolicy.YT_MUSIC_ONLY
        set(value) {
            if (field == value) return
            field = value
            Log.i(TAG, "MEDIA policy=$value")
            main.post { refresh() }
        }

    /** Idempotent. Called from Application.onCreate and from the notification listener. */
    fun start(context: Context) {
        if (app == null) {
            app = context.applicationContext
            sessionManager = context.getSystemService(MediaSessionManager::class.java)
        }
        main.post { refresh() }
    }

    private fun listenerComponent(ctx: Context) = ComponentName(ctx, MediaListenerService::class.java)

    fun hasNotificationAccess(): Boolean {
        val ctx = app ?: return false
        return ctx.getSystemService(NotificationManager::class.java)
            .isNotificationListenerAccessGranted(listenerComponent(ctx))
    }

    fun isYtMusicInstalled(): Boolean {
        val ctx = app ?: return false
        return try {
            ctx.packageManager.getPackageInfo(YT_MUSIC_PACKAGE, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    /** Re-evaluates access and the active sessions. Safe to call any time (e.g. Activity.onResume). */
    fun refresh() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post { refresh() }
            return
        }
        val ctx = app ?: return
        val msm = sessionManager ?: return
        if (!hasNotificationAccess()) {
            unregisterSessionsListener()
            attach(null)
            _state.value = MediaState(access = MediaState.Access.NO_NOTIFICATION_ACCESS)
            return
        }
        val sessions = try {
            if (!listenerRegistered) {
                msm.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent(ctx), main)
                listenerRegistered = true
            }
            msm.getActiveSessions(listenerComponent(ctx))
        } catch (e: SecurityException) {
            // Access revoked between the check and the call.
            Log.w(TAG, "MEDIA getActiveSessions denied", e)
            listenerRegistered = false
            _state.value = MediaState(access = MediaState.Access.NO_NOTIFICATION_ACCESS)
            return
        }
        pick(sessions)
    }

    private fun unregisterSessionsListener() {
        if (!listenerRegistered) return
        runCatching { sessionManager?.removeOnActiveSessionsChangedListener(sessionsListener) }
        listenerRegistered = false
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        pick(list.orEmpty())
    }

    private fun pick(sessions: List<MediaController>) {
        Log.i(TAG, "MEDIA sessions=${sessions.map { "${it.packageName}:${stateName(it.playbackState?.state)}" }}")
        val chosen = when (policy) {
            SourcePolicy.YT_MUSIC_ONLY -> sessions.firstOrNull { it.packageName == YT_MUSIC_PACKAGE }
            SourcePolicy.ANY_MEDIA_APP ->
                sessions.firstOrNull { it.packageName == YT_MUSIC_PACKAGE && it.isPlaying() }
                    ?: sessions.firstOrNull { it.isPlaying() }
                    ?: sessions.firstOrNull { it.packageName == YT_MUSIC_PACKAGE }
                    ?: sessions.firstOrNull()
        }
        if (chosen != null && chosen.sessionToken == controller?.sessionToken) {
            publish(chosen) // same session; make sure state is fresh
            return
        }
        attach(chosen)
        if (chosen == null) {
            val access = if (policy == SourcePolicy.YT_MUSIC_ONLY && !isYtMusicInstalled()) {
                MediaState.Access.YT_MUSIC_NOT_INSTALLED
            } else {
                MediaState.Access.NO_SESSION
            }
            _state.value = MediaState(access = access)
            _artBitmap.value = null
            lastArtSource = null
        }
    }

    private fun attach(newController: MediaController?) {
        controller?.unregisterCallback(controllerCallback)
        controller = newController
        if (newController != null) {
            Log.i(TAG, "MEDIA tracking ${newController.packageName}")
            newController.registerCallback(controllerCallback, main)
            lastLoggedActions = null
            publish(newController)
        }
    }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            controller?.let { publish(it) }
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            controller?.let { publish(it) }
        }

        override fun onSessionDestroyed() {
            Log.i(TAG, "MEDIA session destroyed")
            attach(null)
            refresh()
        }
    }

    private fun publish(c: MediaController) {
        val md = c.metadata
        val ps = c.playbackState
        val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
        val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
        val album = md?.getString(MediaMetadata.METADATA_KEY_ALBUM)
        val trackKey = md?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID) ?: "$title|$artist|$album"
        val actions = ps?.customActions.orEmpty().map { MediaState.CustomAction(it.action, it.name.toString()) }

        val previous = _state.value
        val next = MediaState(
            access = MediaState.Access.OK,
            packageName = c.packageName,
            title = title,
            artist = artist,
            album = album,
            playback = mapState(ps?.state),
            positionMs = ps?.position ?: 0,
            positionUpdatedAtMs = ps?.lastPositionUpdateTime ?: 0,
            playbackSpeed = ps?.playbackSpeed?.takeIf { it > 0f } ?: 1f,
            durationMs = md?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.coerceAtLeast(0) ?: 0,
            customActions = actions,
            trackKey = trackKey,
            art = if (previous.trackKey == trackKey && previous.packageName == c.packageName) previous.art else null,
        )
        _state.value = next

        if (previous.trackKey != next.trackKey || previous.packageName != next.packageName) {
            Log.i(TAG, "MEDIA track \"$title\" · $artist · $album (${next.durationMs} ms) pkg=${c.packageName}")
        }
        if (previous.playback != next.playback) {
            Log.i(TAG, "MEDIA state ${next.playback} pos=${next.positionMs}")
        }
        if (actions != lastLoggedActions) {
            lastLoggedActions = actions
            Log.i(TAG, "MEDIA customActions=${actions.map { "${it.name} [${it.action}]" }}")
        }
        updateArt(md)
    }

    private fun updateArt(md: MediaMetadata?) {
        val bitmap = md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        if (bitmap === lastArtSource) return
        lastArtSource = bitmap
        _artBitmap.value = bitmap
        artJob?.cancel()
        if (bitmap == null) {
            val uri = md?.getString(MediaMetadata.METADATA_KEY_ART_URI)
                ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
            Log.i(TAG, "MEDIA no art bitmap (uri=$uri)")
            _state.value = _state.value.copy(art = null)
            return
        }
        val key = _state.value.trackKey
        artJob = scope.launch {
            val pixels = runCatching { withContext(Dispatchers.Default) { ArtConverter.toLuma(bitmap) } }
                .onFailure { Log.w(TAG, "MEDIA art conversion failed", it) }
                .getOrNull() ?: return@launch
            if (_state.value.trackKey == key) {
                _state.value = _state.value.copy(art = pixels)
                Log.i(TAG, "MEDIA art ${bitmap.width}x${bitmap.height} → ${pixels.size}x${pixels.size} luma")
            }
        }
    }

    // ---- Transport controls (no-ops without a tracked session) ----

    fun play() = transport("play") { it.play() }
    fun pause() = transport("pause") { it.pause() }
    fun playPause() = if (_state.value.isPlaying) pause() else play()
    fun next() = transport("next") { it.skipToNext() }
    fun previous() = transport("previous") { it.skipToPrevious() }

    fun seekBy(deltaMs: Long) {
        val s = _state.value
        var target = s.positionAt(SystemClock.elapsedRealtime()) + deltaMs
        target = if (s.durationMs > 0) target.coerceIn(0, s.durationMs) else target.coerceAtLeast(0)
        transport("seek $target") { it.seekTo(target) }
    }

    fun sendCustomAction(action: String) = transport("custom $action") { it.sendCustomAction(action, null) }

    /** Returns false when there is no session to control (callers may show feedback). */
    private fun transport(name: String, block: (MediaController.TransportControls) -> Unit): Boolean {
        val c = controller
        if (c == null) {
            Log.i(TAG, "MEDIA $name ignored: no session")
            return false
        }
        Log.i(TAG, "MEDIA → $name (${c.packageName})")
        block(c.transportControls)
        return true
    }

    // ---- helpers ----

    private fun MediaController.isPlaying() = playbackState?.state == PlaybackState.STATE_PLAYING

    private fun mapState(state: Int?): MediaState.Playback = when (state) {
        null, PlaybackState.STATE_NONE -> MediaState.Playback.NONE
        PlaybackState.STATE_STOPPED -> MediaState.Playback.STOPPED
        PlaybackState.STATE_PAUSED -> MediaState.Playback.PAUSED
        PlaybackState.STATE_PLAYING -> MediaState.Playback.PLAYING
        PlaybackState.STATE_BUFFERING, PlaybackState.STATE_CONNECTING -> MediaState.Playback.BUFFERING
        PlaybackState.STATE_ERROR -> MediaState.Playback.ERROR
        else -> MediaState.Playback.OTHER
    }

    private fun stateName(state: Int?) = mapState(state).name
}
