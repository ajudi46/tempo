package com.ajudi46.tempo.mobile

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import java.io.ByteArrayOutputStream

const val PATH_NOW_PLAYING = "/now_playing"

/** Reads the phone's active media session, runs watch commands against it, and publishes state to the watch. */
object MediaBridge {

    fun activeController(ctx: Context): MediaController? {
        val msm = ctx.getSystemService(MediaSessionManager::class.java)
        val sessions = try {
            msm.getActiveSessions(ComponentName(ctx, MediaSyncService::class.java))
        } catch (_: SecurityException) {
            return null // notification access not granted yet
        }
        return sessions.firstOrNull { it.playbackState?.isPlaying() == true } ?: sessions.firstOrNull()
    }

    fun handle(ctx: Context, command: String) {
        val audio = ctx.getSystemService(AudioManager::class.java)
        val controller = activeController(ctx)
        when {
            command == "toggle" -> controller?.let {
                if (it.playbackState?.isPlaying() == true) it.transportControls.pause()
                else it.transportControls.play()
            }
            command == "play" -> controller?.transportControls?.play()
            command == "pause" -> controller?.transportControls?.pause()
            command == "next" -> controller?.transportControls?.skipToNext()
            command == "prev" -> controller?.transportControls?.skipToPrevious()
            command == "vol_up" -> audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0)
            command == "vol_down" -> audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0)
            command.startsWith("vol_set:") -> {
                val target = command.substringAfter(':').toIntOrNull() ?: return
                val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, target.coerceIn(0, max), 0)
            }
        }
        publish(ctx)
    }

    fun publish(ctx: Context) {
        val controller = activeController(ctx)
        val audio = ctx.getSystemService(AudioManager::class.java)
        val meta = controller?.metadata

        val req = PutDataMapRequest.create(PATH_NOW_PLAYING)
        req.dataMap.apply {
            putBoolean("has_session", controller != null)
            putString("title", meta?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty())
            putString("artist", meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST).orEmpty())
            putString("app_name", controller?.let { appLabel(ctx, it.packageName) }.orEmpty())
            putBoolean("playing", controller?.playbackState?.isPlaying() == true)
            // Position is estimated "as of now" so the watch can animate the ring from here
            val state = controller?.playbackState
            val speed = state?.playbackSpeed ?: 1f
            val position = if (state == null || state.position < 0) -1L else {
                val elapsed = if (state.isPlaying()) SystemClock.elapsedRealtime() - state.lastPositionUpdateTime else 0L
                state.position + (elapsed * speed).toLong()
            }
            putLong("position_ms", position)
            putLong("duration_ms", meta?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L)
            putFloat("speed", speed)
            putInt("volume", audio.getStreamVolume(AudioManager.STREAM_MUSIC))
            putInt("max_volume", audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC))
            putLong("ts", System.currentTimeMillis()) // forces onDataChanged even if nothing else changed
            artworkBytes(meta)?.let { putAsset("artwork", Asset.createFromBytes(it)) }
        }
        Wearable.getDataClient(ctx).putDataItem(req.asPutDataRequest().setUrgent())
    }

    private fun PlaybackState.isPlaying() =
        state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING

    private fun appLabel(ctx: Context, pkg: String): String = try {
        val pm = ctx.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) {
        pkg
    }

    private fun artworkBytes(meta: MediaMetadata?): ByteArray? {
        val bmp = meta?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            ?: return null
        // Drop letterbox bars, then centre-crop to a square so it fills the round watch screen
        val square = centerSquare(trimBlackBars(bmp))
        val side = 400
        val scaled = if (square.width > side) Bitmap.createScaledBitmap(square, side, side, true) else square
        return ByteArrayOutputStream().also { scaled.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
    }

    /** Removes near-black rows/columns along the edges (e.g. square art inside a 16:9 video thumbnail). */
    private fun trimBlackBars(src: Bitmap): Bitmap {
        val pw = 64
        val ph = (pw.toFloat() * src.height / src.width).toInt().coerceAtLeast(1)
        val probe = Bitmap.createScaledBitmap(src, pw, ph, true)
        fun dark(x: Int, y: Int): Boolean {
            val c = probe.getPixel(x, y)
            val luma = 0.299 * android.graphics.Color.red(c) + 0.587 * android.graphics.Color.green(c) +
                0.114 * android.graphics.Color.blue(c)
            return luma < 20
        }
        fun rowDark(y: Int) = (0 until pw).all { dark(it, y) }
        fun colDark(x: Int) = (0 until ph).all { dark(x, it) }

        var top = 0
        while (top < ph - 1 && rowDark(top)) top++
        var bottom = ph - 1
        while (bottom > top && rowDark(bottom)) bottom--
        var left = 0
        while (left < pw - 1 && colDark(left)) left++
        var right = pw - 1
        while (right > left && colDark(right)) right--

        val keptW = right - left + 1
        val keptH = bottom - top + 1
        // Nothing to trim, or the picture is just dark: leave it alone
        if ((keptW == pw && keptH == ph) || keptW < pw * 0.3 || keptH < ph * 0.3) return src

        val sx = src.width / pw.toFloat()
        val sy = src.height / ph.toFloat()
        val x0 = (left * sx).toInt().coerceIn(0, src.width - 1)
        val y0 = (top * sy).toInt().coerceIn(0, src.height - 1)
        val w = (keptW * sx).toInt().coerceIn(1, src.width - x0)
        val h = (keptH * sy).toInt().coerceIn(1, src.height - y0)
        return Bitmap.createBitmap(src, x0, y0, w, h)
    }

    private fun centerSquare(src: Bitmap): Bitmap {
        val side = minOf(src.width, src.height)
        return Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
    }
}
