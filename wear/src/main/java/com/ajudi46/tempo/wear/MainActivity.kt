package com.ajudi46.tempo.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.FilledTonalIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { RemoteScreen() } }
    }
}

private const val PIXELS_PER_VOLUME_STEP = 48f

@Composable
private fun RemoteScreen() {
    val ctx = LocalContext.current
    val s by NowPlayingRepo.state.collectAsState()
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    val haptic = LocalHapticFeedback.current
    var rotaryAcc by remember { mutableFloatStateOf(0f) }
    var progress by remember { mutableFloatStateOf(0f) }
    val colors = MaterialTheme.colorScheme

    LaunchedEffect(Unit) {
        NowPlayingRepo.refresh(ctx)
        focus.requestFocus()
    }

    // Move the ring between phone updates while playing
    LaunchedEffect(s) {
        while (true) {
            progress = s.progressNow() ?: 0f
            if (!s.playing) break
            delay(500)
        }
    }

    fun send(cmd: String) {
        scope.launch(Dispatchers.IO) { CommandSender.send(ctx, cmd) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            // Crown: clockwise = louder
            .onRotaryScrollEvent { e ->
                rotaryAcc += e.verticalScrollPixels
                val steps = (rotaryAcc / PIXELS_PER_VOLUME_STEP).toInt()
                if (steps != 0) {
                    rotaryAcc -= steps * PIXELS_PER_VOLUME_STEP
                    val v = NowPlayingRepo.bumpVolume(steps)
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    send("vol_set:$v")
                }
                true
            }
            .focusRequester(focus)
            .focusable()
    ) {
        // Softened artwork backdrop, then a scrim so text and buttons stay readable
        val backdrop = s.backdrop
        if (backdrop != null) {
            Image(
                bitmap = backdrop.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.Medium,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(Modifier.fillMaxSize().background(colors.surfaceContainer))
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0xB30C0A12), Color(0x990C0A12), Color(0xCC0C0A12)))
            )
        )

        VolumeArc(
            fraction = if (s.maxVolume > 0) s.volume.toFloat() / s.maxVolume else 0f,
            track = Color.White.copy(alpha = 0.22f),
            fill = colors.primary,
        )

        if (!s.hasSession) {
            Text(
                "Nothing playing on your phone",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
            )
        } else {
            // No side padding on the outer column: the button row needs the full width
            Column(
                Modifier.fillMaxSize().padding(top = 14.dp, bottom = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.clip(CircleShape)
                            .background(colors.surfaceContainer.copy(alpha = 0.62f))
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    ) {
                        Text(
                            s.appName, style = MaterialTheme.typography.labelSmall, color = colors.primary,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    val textPad = Modifier.padding(horizontal = 34.dp)
                    Text(
                        s.title, style = MaterialTheme.typography.titleMedium, color = colors.onBackground,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = textPad,
                    )
                    Text(
                        s.artist, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = textPad,
                    )
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledTonalIconButton(onClick = { send("prev") }, modifier = Modifier.size(38.dp)) {
                        Icon(painterResource(R.drawable.ic_prev), contentDescription = "Previous", modifier = Modifier.size(20.dp))
                    }
                    // Largest control, with the progress ring around it
                    Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                        ProgressRing(
                            progress = progress,
                            playing = s.playing,
                            track = Color.White.copy(alpha = 0.22f),
                            fill = colors.primary,
                        )
                        FilledIconButton(onClick = { send("toggle") }, modifier = Modifier.size(54.dp)) {
                            Icon(
                                painterResource(if (s.playing) R.drawable.ic_pause else R.drawable.ic_play),
                                contentDescription = if (s.playing) "Pause" else "Play",
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }
                    FilledTonalIconButton(onClick = { send("next") }, modifier = Modifier.size(38.dp)) {
                        Icon(painterResource(R.drawable.ic_next), contentDescription = "Next", modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

/** Wavy progress ring: it waves while playing and straightens when paused. */
@Composable
private fun ProgressRing(progress: Float, playing: Boolean, track: Color, fill: Color) {
    val amplitude by animateFloatAsState(if (playing) 1.6f else 0f, label = "ringWave")
    Canvas(Modifier.fillMaxSize()) {
        val stroke = 3.5.dp.toPx()
        val maxAmp = 1.6.dp.toPx()
        val amp = amplitude.dp.toPx()
        val r = size.minDimension / 2 - stroke / 2 - maxAmp
        drawCircle(track, radius = r, style = Stroke(stroke))

        val sweep = 360f * progress.coerceIn(0f, 1f)
        if (sweep <= 0f) return@Canvas
        val path = Path()
        var a = 0f
        while (true) {
            val theta = Math.toRadians((a - 90f).toDouble())
            val rr = r + amp * sin(Math.toRadians((a * 12f).toDouble())).toFloat()
            val x = center.x + rr * cos(theta).toFloat()
            val y = center.y + rr * sin(theta).toFloat()
            if (a == 0f) path.moveTo(x, y) else path.lineTo(x, y)
            if (a >= sweep) break
            a = minOf(a + 3f, sweep)
        }
        drawPath(path, fill, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Volume gauge hugging the right edge of the round display, filling from the bottom. */
@Composable
private fun VolumeArc(fraction: Float, track: Color, fill: Color) {
    Canvas(Modifier.fillMaxSize()) {
        val stroke = 6.dp.toPx()
        val inset = stroke / 2 + 2.dp.toPx()
        val topLeft = Offset(inset, inset)
        val arcSize = Size(size.width - 2 * inset, size.height - 2 * inset)
        val span = 80f
        drawArc(
            track, startAngle = -span / 2, sweepAngle = span, useCenter = false,
            topLeft = topLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round),
        )
        val sweep = span * fraction.coerceIn(0f, 1f)
        if (sweep > 0f) {
            drawArc(
                fill, startAngle = span / 2 - sweep, sweepAngle = sweep, useCenter = false,
                topLeft = topLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
    }
}
