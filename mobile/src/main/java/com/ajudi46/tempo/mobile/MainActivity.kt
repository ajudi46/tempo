package com.ajudi46.tempo.mobile

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await

/** Hosted privacy policy, also required in the Play Console listing. The link is hidden while this is empty. */
private const val PRIVACY_POLICY_URL = "https://ajudi46.github.io/tempo/privacy.html"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { TempoApp() }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TempoApp() {
    val ctx = LocalContext.current
    val dark = isSystemInDarkTheme()
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialExpressiveTheme(colorScheme = scheme, motionScheme = MotionScheme.expressive()) {
        Home()
    }
}

private class NowPlayingInfo(val title: String, val artist: String, val app: String)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Home() {
    val ctx = LocalContext.current
    var access by remember { mutableStateOf(false) }
    var watch by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableStateOf<NowPlayingInfo?>(null) }

    // Poll lightly so the cards react as soon as the user comes back from Settings or the watch connects
    LaunchedEffect(Unit) {
        while (true) {
            access = NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
            watch = runCatching {
                Wearable.getNodeClient(ctx).connectedNodes.await().firstOrNull()?.displayName
            }.getOrNull()
            now = MediaBridge.activeController(ctx)?.let { c ->
                val m = c.metadata
                NowPlayingInfo(
                    m?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE).orEmpty(),
                    m?.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST).orEmpty(),
                    runCatching {
                        ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(c.packageName, 0)).toString()
                    }.getOrDefault(c.packageName),
                )
            }
            delay(2000)
        }
    }

    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Tempo") },
                subtitle = { Text("Your phone's media, on your wrist") },
                scrollBehavior = scroll,
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Hero(ready = access && watch != null)

            StatusCard(
                icon = Icons.Filled.Notifications,
                title = "Media access",
                body = if (access) "Tempo can see and control what's playing."
                else "Allow notification access so Tempo can control other apps' media.",
                ok = access,
            ) {
                if (!access) {
                    Button(
                        onClick = { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.MediumContainerHeight),
                    ) { Text("Grant access") }
                }
            }

            StatusCard(
                icon = Icons.Filled.PlayArrow,
                title = "Watch",
                body = watch?.let { "Connected to $it" } ?: "No watch connected. Check Bluetooth and the Tempo watch app.",
                ok = watch != null,
            )

            val n = now
            Card(
                Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Now playing", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    if (n == null) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            LoadingIndicator(Modifier.size(40.dp))
                            Text(
                                "Waiting for something to play",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        Text(n.title.ifBlank { "Unknown title" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(n.artist, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(n.app, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (PRIVACY_POLICY_URL.isNotBlank()) {
                TextButton(
                    onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL))) },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) { Text("Privacy policy") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Hero(ready: Boolean) {
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Surface(
            shape = MaterialShapes.Cookie9Sided.toShape(),
            color = if (ready) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.size(140.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = if (ready) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatusCard(
    icon: ImageVector,
    title: String,
    body: String,
    ok: Boolean,
    action: (@Composable () -> Unit)? = null,
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = if (ok) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape)
                        .background(if (ok) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        icon, contentDescription = null,
                        tint = if (ok) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onError,
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(body, style = MaterialTheme.typography.bodyMedium)
                }
                Icon(
                    if (ok) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                    contentDescription = if (ok) "OK" else "Needs attention",
                )
            }
            action?.invoke()
        }
    }
}
