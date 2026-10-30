package com.ajudi46.tempo.mobile

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

class CommandReceiver : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        if (event.path == "/command") {
            MediaBridge.handle(applicationContext, String(event.data))
        }
    }
}
