package com.ajudi46.tempo.mobile

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import androidx.core.content.ContextCompat

/**
 * Bound by the system once the user grants notification access. That grant is what lets us
 * list and control other apps' media sessions. We watch them and push changes to the watch.
 */
class MediaSyncService : NotificationListenerService() {

    private val handler = Handler(Looper.getMainLooper())
    private val publish = Runnable { MediaBridge.publish(this) }
    private var controllers: List<MediaController> = emptyList()

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: android.media.session.PlaybackState?) = schedule()
        override fun onMetadataChanged(metadata: android.media.MediaMetadata?) = schedule()
        override fun onSessionDestroyed() = schedule()
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener {
        track(it.orEmpty())
        schedule()
    }

    // Fires when the phone's own volume buttons are used
    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = schedule()
    }

    private val component get() = ComponentName(this, MediaSyncService::class.java)

    override fun onListenerConnected() {
        val msm = getSystemService(MediaSessionManager::class.java)
        msm.addOnActiveSessionsChangedListener(sessionsListener, component)
        track(msm.getActiveSessions(component))
        ContextCompat.registerReceiver(
            this, volumeReceiver, IntentFilter("android.media.VOLUME_CHANGED_ACTION"),
            ContextCompat.RECEIVER_EXPORTED
        )
        schedule()
    }

    override fun onListenerDisconnected() {
        getSystemService(MediaSessionManager::class.java).removeOnActiveSessionsChangedListener(sessionsListener)
        controllers.forEach { it.unregisterCallback(controllerCallback) }
        controllers = emptyList()
        runCatching { unregisterReceiver(volumeReceiver) }
    }

    private fun track(list: List<MediaController>) {
        controllers.forEach { it.unregisterCallback(controllerCallback) }
        controllers = list
        controllers.forEach { it.registerCallback(controllerCallback, handler) }
    }

    /** State and metadata callbacks arrive together; coalesce them into one update. */
    private fun schedule() {
        handler.removeCallbacks(publish)
        handler.postDelayed(publish, 150)
    }
}
