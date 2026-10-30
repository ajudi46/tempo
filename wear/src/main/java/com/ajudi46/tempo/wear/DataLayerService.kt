package com.ajudi46.tempo.wear

import androidx.wear.tiles.TileService
import com.ajudi46.tempo.wear.tile.MediaTileService
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Wakes up whenever the phone publishes new playback state, even if the app isn't open. */
class DataLayerService : WearableListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDataChanged(events: DataEventBuffer) {
        if (events.none { it.dataItem.uri.path == PATH_NOW_PLAYING }) return
        scope.launch {
            NowPlayingRepo.refresh(applicationContext)
            TileService.getUpdater(applicationContext).requestUpdate(MediaTileService::class.java)
        }
    }
}
