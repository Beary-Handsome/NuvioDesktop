package com.nuvio.app.features.player.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import com.nuvio.app.desktop.DesktopPlayerRegistry
import com.nuvio.app.desktop.DesktopRuntimeLog
import com.nuvio.app.features.player.PlayerBackendOption
import com.nuvio.app.features.player.PlayerEngineController
import com.nuvio.app.features.player.PlayerPlaybackSnapshot
import com.nuvio.app.features.player.PlayerResizeMode
import com.nuvio.app.features.player.PlayerSettingsRepository
import com.nuvio.app.features.player.desktop.nuvio.NuvioDesktopPlayerOverlay
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import java.security.MessageDigest

@Composable
internal fun DesktopPlayerSurfaceHost(
    sourceUrl: String,
    sourceAudioUrl: String?,
    sourceHeaders: Map<String, String>,
    sourceResponseHeaders: Map<String, String>,
    modifier: Modifier,
    playWhenReady: Boolean,
    resizeMode: PlayerResizeMode,
    onControllerReady: (PlayerEngineController) -> Unit,
    onSnapshot: (PlayerPlaybackSnapshot) -> Unit,
    onError: (String?) -> Unit,
    onSubtitleClick: (() -> Unit)? = null,
    onAudioClick: (() -> Unit)? = null,
    onVideoSettingsClick: (() -> Unit)? = null,
    onSourcesClick: (() -> Unit)? = null,
    onEpisodesClick: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    onResizeModeClick: (() -> Unit)? = null,
    onSpeedClick: (() -> Unit)? = null,
) {
    val sessionKey = remember(sourceUrl, sourceAudioUrl, sourceHeaders, sourceResponseHeaders) {
        listOf(sourceUrl, sourceAudioUrl.orEmpty(), sourceHeaders.hashCode().toString(), sourceResponseHeaders.hashCode().toString())
            .joinToString("|")
            .sha256Prefix()
    }
    val latestOnControllerReady by rememberUpdatedState(onControllerReady)
    val latestOnSnapshot by rememberUpdatedState(onSnapshot)
    val latestOnError by rememberUpdatedState(onError)

    var activeSessionKey by remember { mutableStateOf<String?>(null) }
    var lastPositionMs by remember { mutableStateOf(0L) }
    val backend = remember {
        DesktopPlayerBackendFactory.createDesktopBackend()
    }

    DisposableEffect(backend) {
        val registryId = "desktop-player-${backend.id}"
        DesktopPlayerRegistry.register(
            id = registryId,
            stop = { backend.releaseSoft() },
            close = {
                backend.releaseSoft()
                backend.close()
            },
        )
        onDispose {
            DesktopRuntimeLog.info("DesktopPlayerSurfaceHost dispose backend=${backend.backendName}")
            DesktopPlayerRegistry.unregister(registryId)
            backend.releaseSoft()
            backend.close()
        }
    }

    LaunchedEffect(sessionKey, backend) {
        activeSessionKey = sessionKey
        DesktopRuntimeLog.info("DesktopPlayerSurfaceHost load session=$sessionKey backend=${backend.backendName}")
        latestOnControllerReady(backend.controller)
        val request = DesktopPlayerRequest(
            sessionKey = sessionKey,
            sourceUrl = sourceUrl,
            sourceAudioUrl = sourceAudioUrl,
            sourceHeaders = sourceHeaders,
            sourceResponseHeaders = sourceResponseHeaders,
            playWhenReady = playWhenReady,
            resizeMode = resizeMode,
            seekTargetMs = lastPositionMs,
        )
        backend.load(request)
    }

    LaunchedEffect(backend, sessionKey) {
        backend.state.collectLatest { state ->
            if (sessionKey != activeSessionKey) {
                DesktopRuntimeLog.info("DesktopPlayerSurfaceHost stale state ignored session=$sessionKey active=$activeSessionKey")
                return@collectLatest
            }
            latestOnSnapshot(state.toSnapshot())
            lastPositionMs = state.positionMs
            latestOnError(state.error?.uiMessage)
        }
    }

    // Only react to playWhenReady CHANGES after initial load (drop the first emission
    // to avoid racing with the load LaunchedEffect which already handles playWhenReady)
    LaunchedEffect(backend, sessionKey) {
        if (sessionKey != activeSessionKey) return@LaunchedEffect
        snapshotFlow { playWhenReady }
            .drop(1)
            .collect { shouldPlay ->
                if (shouldPlay) backend.controller.play() else backend.controller.pause()
            }
    }

    LaunchedEffect(resizeMode, backend, sessionKey) {
        if (sessionKey != activeSessionKey) return@LaunchedEffect
        backend.setResizeMode(resizeMode)
    }

    // Always use the Nuvio overlay on desktop — it provides controls, back button, and OSD
    val state by backend.state.collectAsState()
    NuvioDesktopPlayerOverlay(
        state = state,
        controller = backend.controller,
        videoSurface = { backend.Surface(modifier) },
        onSubtitleClick = onSubtitleClick,
        onAudioClick = onAudioClick,
        onVideoSettingsClick = onVideoSettingsClick,
        onSourcesClick = onSourcesClick,
        onEpisodesClick = onEpisodesClick,
        onBack = onBack,
        onResizeModeClick = onResizeModeClick,
        onSpeedClick = onSpeedClick,
    )
}

private fun String.sha256Prefix(): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray())
    return digest.take(8).joinToString(separator = "") { byte ->
        byte.toUByte().toString(16).padStart(2, '0')
    }
}
