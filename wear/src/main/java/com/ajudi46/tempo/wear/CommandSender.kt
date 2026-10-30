package com.ajudi46.tempo.wear

import android.content.Context
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

object CommandSender {
    /** Commands: toggle, next, prev, vol_up, vol_down, vol_set:N */
    suspend fun send(ctx: Context, command: String) {
        val nodes = Wearable.getNodeClient(ctx).connectedNodes.await()
        val messages = Wearable.getMessageClient(ctx)
        nodes.forEach { messages.sendMessage(it.id, PATH_COMMAND, command.toByteArray()).await() }
    }
}
