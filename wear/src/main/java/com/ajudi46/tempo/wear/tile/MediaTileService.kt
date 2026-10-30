package com.ajudi46.tempo.wear.tile

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.Box
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.LayoutElementBuilders.Row
import androidx.wear.protolayout.LayoutElementBuilders.Spacer
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.protolayout.material3.ButtonDefaults.filledButtonColors
import androidx.wear.protolayout.material3.ButtonDefaults.filledTonalButtonColors
import androidx.wear.protolayout.material3.IconButtonStyle.Companion.largeIconButtonStyle
import androidx.wear.protolayout.material3.Typography
import androidx.wear.protolayout.material3.backgroundImage
import androidx.wear.protolayout.material3.circularProgressIndicator
import androidx.wear.protolayout.material3.icon
import androidx.wear.protolayout.material3.iconButton
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.modifiers.clickable
import androidx.wear.protolayout.types.argb
import androidx.wear.protolayout.types.layoutString
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.tiles.TileService
import com.ajudi46.tempo.wear.CommandSender
import com.ajudi46.tempo.wear.MainActivity
import com.ajudi46.tempo.wear.NowPlaying
import com.ajudi46.tempo.wear.NowPlayingRepo
import com.ajudi46.tempo.wear.R
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.guava.future

private const val ART = "art"

/**
 * The home-screen widget, built with the Material 3 Expressive tile components.
 * Wear OS doesn't deliver crown input to tiles, so volume lives in the app: tap the title to open it.
 */
class MediaTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<Tile> =
        scope.future {
            val command = when (requestParams.currentState.lastClickableId) {
                "toggle" -> "toggle"
                "next" -> "next"
                "prev" -> "prev"
                else -> null
            }
            command?.let { CommandSender.send(this@MediaTileService, it) }
            NowPlayingRepo.refresh(this@MediaTileService)

            var s = NowPlayingRepo.state.value
            // Show the new play/pause icon right away; the phone's confirmation arrives a moment later
            if (command == "toggle") s = s.copy(playing = !s.playing)

            Tile.Builder()
                .setResourcesVersion(version(s))
                .setTileTimeline(Timeline.fromLayoutElement(layout(s, requestParams.deviceConfiguration)))
                // While playing, ask for a refresh so the progress ring keeps up
                .setFreshnessIntervalMillis(if (s.playing) 15_000L else 0L)
                .build()
        }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest
    ): ListenableFuture<ResourceBuilders.Resources> = scope.future {
        val s = NowPlayingRepo.state.value
        val res = ResourceBuilders.Resources.Builder().setVersion(version(s))
        listOf(
            "ic_play" to R.drawable.ic_play, "ic_pause" to R.drawable.ic_pause,
            "ic_next" to R.drawable.ic_next, "ic_prev" to R.drawable.ic_prev,
        ).forEach { (id, drawable) ->
            res.addIdToImageMapping(
                id,
                ResourceBuilders.ImageResource.Builder().setAndroidResourceByResId(
                    ResourceBuilders.AndroidImageResourceByResId.Builder().setResourceId(drawable).build()
                ).build()
            )
        }
        val bmp = s.backdrop
        if (s.backdropBytes != null && bmp != null) {
            res.addIdToImageMapping(
                ART,
                ResourceBuilders.ImageResource.Builder().setInlineResource(
                    ResourceBuilders.InlineImageResource.Builder()
                        .setData(s.backdropBytes)
                        .setFormat(ResourceBuilders.IMAGE_FORMAT_UNDEFINED)
                        .setWidthPx(bmp.width).setHeightPx(bmp.height)
                        .build()
                ).build()
            )
        }
        res.build()
    }

    private fun version(s: NowPlaying) = "v${s.backdropBytes?.contentHashCode() ?: 0}"

    // ---- layout (Material 3 Expressive) ----

    private fun layout(s: NowPlaying, device: DeviceParameters): LayoutElement {
        val open = clickable(
            action = ActionBuilders.LaunchAction.Builder().setAndroidActivity(
                ActionBuilders.AndroidActivity.Builder()
                    .setPackageName(packageName)
                    .setClassName(MainActivity::class.java.name)
                    .build()
            ).build(),
            id = "open",
        )
        val hasArt = s.backdropBytes != null

        return materialScope(context = this, deviceConfiguration = device) {
            val content = if (!s.hasSession) {
                primaryLayout(
                    titleSlot = { text("Tempo".layoutString, typography = Typography.LABEL_MEDIUM, color = colorScheme.primary) },
                    mainSlot = {
                        text(
                            "Nothing playing".layoutString,
                            typography = Typography.BODY_MEDIUM,
                            color = colorScheme.onSurfaceVariant,
                        )
                    },
                    onClick = open,
                )
            } else {
                primaryLayout(
                    titleSlot = {
                        text(
                            s.appName.layoutString,
                            typography = Typography.LABEL_MEDIUM,
                            color = colorScheme.primary,
                            maxLines = 1,
                        )
                    },
                    mainSlot = {
                        Column.Builder()
                            .setWidth(expand())
                            .setHeight(expand())
                            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                            .addContent(text(s.title.layoutString, typography = Typography.TITLE_MEDIUM, maxLines = 1))
                            .addContent(
                                text(
                                    s.artist.layoutString,
                                    typography = Typography.BODY_SMALL,
                                    color = colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            )
                            // Pushes the controls to the bottom of the tile
                            .addContent(Spacer.Builder().setHeight(expand()).build())
                            .addContent(
                                Row.Builder()
                                    .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                                    .addContent(
                                        iconButton(
                                            onClick = clickable(id = "prev"),
                                            iconContent = { icon("ic_prev") },
                                            width = dp(38f),
                                            height = dp(38f),
                                            colors = filledTonalButtonColors(),
                                        )
                                    )
                                    .addContent(Spacer.Builder().setWidth(dp(4f)).build())
                                    // Largest control: play/pause inside the progress ring
                                    .addContent(
                                        Box.Builder()
                                            .setWidth(dp(70f))
                                            .setHeight(dp(70f))
                                            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                                            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                                            .addContent(
                                                circularProgressIndicator(
                                                    staticProgress = s.progressNow() ?: 0f,
                                                    strokeWidth = 5f,
                                                    size = dp(70f),
                                                )
                                            )
                                            .addContent(
                                                iconButton(
                                                    onClick = clickable(id = "toggle"),
                                                    iconContent = { icon(if (s.playing) "ic_pause" else "ic_play") },
                                                    width = dp(54f),
                                                    height = dp(54f),
                                                    colors = filledButtonColors(),
                                                    style = largeIconButtonStyle(),
                                                )
                                            )
                                            .build()
                                    )
                                    .addContent(Spacer.Builder().setWidth(dp(4f)).build())
                                    .addContent(
                                        iconButton(
                                            onClick = clickable(id = "next"),
                                            iconContent = { icon("ic_next") },
                                            width = dp(38f),
                                            height = dp(38f),
                                            colors = filledTonalButtonColors(),
                                        )
                                    )
                                    .build()
                            )
                            .build()
                    },
                    onClick = open,
                )
            }

            // Softened artwork fills the tile behind everything
            if (hasArt && s.hasSession) {
                Box.Builder()
                    .setWidth(expand())
                    .setHeight(expand())
                    .addContent(
                        backgroundImage(
                            ART,
                            contentScaleMode = LayoutElementBuilders.CONTENT_SCALE_MODE_CROP,
                            overlayColor = 0xCC0C0A12.toInt().argb, // dark scrim so text stays readable
                        )
                    )
                    .addContent(content)
                    .build()
            } else {
                content
            }
        }
    }
}
