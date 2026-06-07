package com.nuvio.app.features.player.desktop.nuvio

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.features.player.AudioTrack
import com.nuvio.app.features.player.PlayerAudioLevel
import com.nuvio.app.features.player.PlayerEngineController
import com.nuvio.app.features.player.PlayerLayoutMetrics
import com.nuvio.app.features.player.SubtitleTrack
import com.nuvio.app.features.player.desktop.DesktopPlayerPhase
import com.nuvio.app.features.player.desktop.DesktopPlayerState
import com.nuvio.app.features.player.formatPlaybackSpeedLabel
import com.nuvio.app.features.player.formatPlaybackTime
import com.nuvio.app.openMpvConfigFile
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun NuvioDesktopPlayerOverlay(
    state: DesktopPlayerState,
    controller: PlayerEngineController,
    modifier: Modifier = Modifier,
    videoSurface: @Composable () -> Unit,
    title: String = "",
    subtitleTracks: List<SubtitleTrack> = emptyList(),
    audioTracks: List<AudioTrack> = emptyList(),
    onFullscreenToggle: (() -> Unit)? = null,
    onSubtitleClick: (() -> Unit)? = null,
    onAudioClick: (() -> Unit)? = null,
    onVideoSettingsClick: (() -> Unit)? = null,
    onSourcesClick: (() -> Unit)? = null,
    onEpisodesClick: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    onResizeModeClick: (() -> Unit)? = null,
    onSpeedClick: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    var controlsVisible by remember { mutableStateOf(true) }
    var showVolume by remember { mutableStateOf(false) }
    var volume by remember { mutableStateOf(1f) }
    var isMuted by remember { mutableStateOf(false) }
    var hideJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(Unit) {
        controller.currentVolume()?.let { v ->
            volume = v.fraction
            isMuted = v.isMuted
        }
    }

    fun onActivity() {
        controlsVisible = true
        hideJob?.cancel()
        hideJob = scope.launch {
            delay(8000)
            controlsVisible = false
        }
    }

    val isPlaying = state.phase == DesktopPlayerPhase.Playing
    val isBuffering = state.phase == DesktopPlayerPhase.Buffering
    val durationMs = state.durationMs.coerceAtLeast(1L)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPointerEvent(PointerEventType.Move) { onActivity() }
            .onPointerEvent(PointerEventType.Scroll) { onActivity() }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp) handleKeyboardShortcut(event, controller, state)
                else false
            },
    ) {
        videoSurface()

        when (state.phase) {
            DesktopPlayerPhase.Idle -> IdleOverlay()
            DesktopPlayerPhase.Buffering -> BufferingOverlay()
            else -> {}
        }

        AnimatedVisibility(
            visible = controlsVisible || state.phase == DesktopPlayerPhase.Idle,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(300)),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent),
                            ),
                        ),
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                            ),
                        ),
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (onBack != null) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.35f))
                                    .clickable(onClick = onBack),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ArrowBack,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title.ifEmpty { "Nuvio Player" },
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (onVideoSettingsClick != null) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.35f))
                                        .clickable(onClick = onVideoSettingsClick),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Build,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }

                            if (onFullscreenToggle != null) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.35f))
                                        .clickable(onClick = onFullscreenToggle),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Fullscreen,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.weight(1f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { controller.seekBy(-10_000) }
                                .padding(14.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Replay10,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(38.dp),
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable {
                                    if (isPlaying) controller.pause() else controller.play()
                                }
                                .padding(18.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isBuffering) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.size(44.dp),
                                )
                            } else {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(44.dp),
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { controller.seekBy(10_000) }
                                .padding(14.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Forward10,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(38.dp),
                            )
                        }
                    }

                    Spacer(Modifier.weight(1f))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        val sliderColors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.White.copy(alpha = 0.28f),
                        )

                        Slider(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(24.dp)
                                .graphicsLayer(scaleY = 0.72f),
                            value = state.positionMs.coerceIn(0L, durationMs).toFloat(),
                            onValueChange = { controller.seekTo(it.toLong()) },
                            valueRange = 0f..durationMs.toFloat(),
                            colors = sliderColors,
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp)
                                .padding(top = 4.dp, bottom = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TimePill(text = formatPlaybackTime(state.positionMs))
                            TimePill(text = formatPlaybackTime(durationMs))
                        }

                        Surface(
                            color = Color.Black.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(24.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                if (showVolume) {
                                    VolumeSliderRow(
                                        volume = volume,
                                        isMuted = isMuted,
                                        onVolumeChange = {
                                            volume = it
                                            isMuted = false
                                            controller.setVolume(it)
                                        },
                                        onMuteClick = {
                                            isMuted = !isMuted
                                            controller.setVolume(if (isMuted) 0f else volume)
                                        },
                                    )
                                }
                                Row(
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    ActionPillButton(
                                        icon = Icons.Rounded.AspectRatio,
                                        label = "Fit",
                                        onClick = { if (onResizeModeClick != null) onResizeModeClick() },
                                    )
                                    ActionPillButton(
                                        icon = Icons.Rounded.Speed,
                                        label = formatPlaybackSpeedLabel(state.playbackSpeed),
                                        onClick = { if (onSpeedClick != null) onSpeedClick() },
                                    )
                                    ActionPillButton(
                                        icon = Icons.Rounded.Description,
                                        label = "Config",
                                        onClick = { openMpvConfigFile() },
                                    )
                                    ActionPillButton(
                                        icon = if (isMuted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                                        label = "Vol",
                                        onClick = { showVolume = !showVolume },
                                    )
                                    ActionPillButton(
                                        icon = Icons.Rounded.Subtitles,
                                        label = "CC",
                                        onClick = { if (onSubtitleClick != null) onSubtitleClick() },
                                    )
                                    ActionPillButton(
                                        icon = Icons.Rounded.AudioFile,
                                        label = "AUD",
                                        onClick = { if (onAudioClick != null) onAudioClick() },
                                    )
                                    if (onSourcesClick != null) {
                                        ActionPillButton(
                                            icon = Icons.Rounded.SwapHoriz,
                                            label = "Src",
                                            onClick = onSourcesClick,
                                        )
                                    }
                                    if (onEpisodesClick != null) {
                                        ActionPillButton(
                                            icon = Icons.Rounded.VideoLibrary,
                                            label = "Ep",
                                            onClick = onEpisodesClick,
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun TimePill(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.5f))
            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun ActionPillButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color.White,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = label,
            color = Color.White,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
        )
    }
}

@Composable
private fun VolumeSliderRow(
    volume: Float,
    isMuted: Boolean,
    onVolumeChange: (Float) -> Unit,
    onMuteClick: () -> Unit,
) {
    val pct = ((if (isMuted) 0f else volume) * 100f).toInt().coerceIn(0, 100)
    Row(
        modifier = Modifier
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .widthIn(min = 220.dp, max = 280.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (isMuted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .clickable(onClick = onMuteClick),
        )
        Slider(
            modifier = Modifier.weight(1f),
            value = if (isMuted) 0f else volume,
            onValueChange = onVolumeChange,
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.28f),
            ),
        )
        Text(
            text = "$pct",
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.widthIn(min = 28.dp),
            maxLines = 1,
        )
    }
}

@Composable
private fun IdleOverlay() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Icon(
            imageVector = Icons.Rounded.PlayArrow,
            contentDescription = null,
            tint = Color(0xFFE5383B),
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text("Nuvio Player", color = Color(0xFF999999), fontSize = 18.sp)
    }
}

@Composable
private fun BufferingOverlay() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            color = Color(0xFFE5383B),
            strokeWidth = 3.dp,
            modifier = Modifier.size(40.dp),
        )
    }
}

private fun handleKeyboardShortcut(
    event: KeyEvent,
    controller: PlayerEngineController,
    state: DesktopPlayerState,
): Boolean = when (event.key) {
    Key.Spacebar -> {
        if (state.phase == DesktopPlayerPhase.Playing) controller.pause() else controller.play()
        true
    }
    Key.DirectionLeft -> {
        controller.seekBy(if (event.isShiftPressed) -60_000 else -5_000)
        true
    }
    Key.DirectionRight -> {
        controller.seekBy(if (event.isShiftPressed) 60_000 else 5_000)
        true
    }
    Key.DirectionUp -> {
        controller.currentVolume()?.let {
            controller.setVolume((it.fraction + 0.1f).coerceAtMost(1f))
        }
        true
    }
    Key.DirectionDown -> {
        controller.currentVolume()?.let {
            controller.setVolume((it.fraction - 0.1f).coerceAtLeast(0f))
        }
        true
    }
    else -> false
}
