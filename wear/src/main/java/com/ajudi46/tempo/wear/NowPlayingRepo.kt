package com.ajudi46.tempo.wear

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.SystemClock
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream

const val PATH_NOW_PLAYING = "/now_playing"
const val PATH_COMMAND = "/command"

/** Artwork is shown as a backdrop, so it is stored softened (downscaled, then scaled back up on screen). */
private const val BACKDROP_WIDTH_PX = 48

data class NowPlaying(
    val hasSession: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val appName: String = "",
    val playing: Boolean = false,
    val volume: Int = 0,
    val maxVolume: Int = 15,
    val backdropBytes: ByteArray? = null,
    val backdrop: Bitmap? = null,
    val positionMs: Long = -1,
    val durationMs: Long = 0,
    val speed: Float = 1f,
    /** SystemClock.elapsedRealtime() when positionMs was received. */
    val receivedAt: Long = 0,
) {
    /** Playback progress 0..1 as of now, or null when the player doesn't report a position or length. */
    fun progressNow(now: Long = SystemClock.elapsedRealtime()): Float? {
        if (positionMs < 0 || durationMs <= 0) return null
        val advanced = if (playing) ((now - receivedAt) * speed).toLong() else 0L
        return ((positionMs + advanced).toFloat() / durationMs).coerceIn(0f, 1f)
    }
}

/** Latest playback state received from the phone, shared by the app and the tile. */
object NowPlayingRepo {
    private val _state = MutableStateFlow(NowPlaying())
    val state: StateFlow<NowPlaying> = _state.asStateFlow()

    @Volatile private var lastLocalVolumeAt = 0L

    suspend fun refresh(ctx: Context) {
        val client = Wearable.getDataClient(ctx)
        val buffer = client.getDataItems(Uri.parse("wear://*$PATH_NOW_PLAYING")).await()
        val map = try {
            buffer.firstOrNull()?.let { DataMapItem.fromDataItem(it).dataMap }
        } finally {
            buffer.release()
        } ?: return

        val source = map.getAsset("artwork")?.let { loadAsset(ctx, it) }
            ?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        val backdrop = source?.let { soften(it) }
        val old = _state.value
        // Don't let a stale phone update snap the volume bar back while the crown is turning
        val keepLocalVolume = System.currentTimeMillis() - lastLocalVolumeAt < 800
        _state.value = NowPlaying(
            hasSession = map.getBoolean("has_session"),
            title = map.getString("title", ""),
            artist = map.getString("artist", ""),
            appName = map.getString("app_name", ""),
            playing = map.getBoolean("playing"),
            volume = if (keepLocalVolume) old.volume else map.getInt("volume"),
            maxVolume = map.getInt("max_volume", 15),
            backdropBytes = backdrop?.let { toJpeg(it) },
            backdrop = backdrop,
            positionMs = map.getLong("position_ms", -1L),
            durationMs = map.getLong("duration_ms", 0L),
            speed = map.getFloat("speed", 1f),
            receivedAt = SystemClock.elapsedRealtime(),
        )
    }

    /** Applies a crown step immediately; returns the new volume to send to the phone. */
    fun bumpVolume(steps: Int): Int {
        val s = _state.value
        val v = (s.volume + steps).coerceIn(0, s.maxVolume)
        lastLocalVolumeAt = System.currentTimeMillis()
        _state.value = s.copy(volume = v)
        return v
    }

    private fun soften(src: Bitmap): Bitmap {
        val w = BACKDROP_WIDTH_PX
        val h = (w.toFloat() * src.height / src.width).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, w, h, true)
    }

    private fun toJpeg(bmp: Bitmap): ByteArray =
        ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()

    private suspend fun loadAsset(ctx: Context, asset: Asset): ByteArray? = try {
        Wearable.getDataClient(ctx).getFdForAsset(asset).await().inputStream.use { it.readBytes() }
    } catch (_: Exception) {
        null
    }
}
