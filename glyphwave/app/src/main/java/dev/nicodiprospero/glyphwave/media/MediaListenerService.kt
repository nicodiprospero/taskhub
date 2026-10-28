package dev.nicodiprospero.glyphwave.media

import android.service.notification.NotificationListenerService
import android.util.Log
import dev.nicodiprospero.glyphwave.TAG

/**
 * Exists only so the user can grant "notification access", which is what
 * MediaSessionManager.getActiveSessions() requires. Notifications themselves are ignored.
 * Side effect we rely on: while access is granted, the system keeps this service bound, which keeps
 * the app process (and MediaBridge) alive for the Glyph Toy.
 */
class MediaListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        Log.i(TAG, "MEDIA listener connected")
        MediaBridge.start(applicationContext)
        MediaBridge.refresh()
    }

    override fun onListenerDisconnected() {
        Log.i(TAG, "MEDIA listener disconnected")
        MediaBridge.refresh()
    }
}
