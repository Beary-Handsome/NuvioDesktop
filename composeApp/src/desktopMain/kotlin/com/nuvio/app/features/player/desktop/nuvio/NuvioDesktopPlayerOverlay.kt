package com.nuvio.app.features.player.desktop.nuvio

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import com.nuvio.app.core.ui.NuvioAnimation
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.hoverable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
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
    var isDraggingSeek by remember { mutableStateOf(false) }
    var lastClickTime by remember { mutableStateOf(0L) }

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
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onPointerEvent(PointerEventType.Move) { onActivity() }
            .onPointerEvent(PointerEventType.Scroll) { onActivity() }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp) {
                    val handled = when (event.key) {
                        Key.Escape -> { onBack?.invoke(); true }
                        Key.F -> { onFullscreenToggle?.invoke(); true }
                        Key.M -> {
                            isMuted = !isMuted
                            controller.setVolume(if (isMuted) 0f else volume)
                            true
                        }
                        else -> handleKeyboardShortcut(event, controller, state)
                    }
                    if (handled) onActivity() // Show controls on any keyboard action
                    handled
                } else false
            },
    ) {
        // Video surface — no pointer interception here, let clicks pass through
        videoSurface()

        // Clickable overlay for play/pause (single click) and fullscreen (double click)
        // Uses pointerInput instead of clickable to avoid spacebar triggering click
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val up = waitForUpOrCancellation()
                        if (up != null) {
                            val now = System.currentTimeMillis()
                            if (now - lastClickTime in 50..400) {
                                onFullscreenToggle?.invoke()
                                lastClickTime = 0L
                            } else {
                                if (isPlaying) controller.pause() else controller.play()
                                lastClickTime = now
                            }
                            onActivity()
                        }
                    }
                },
        )

        when (state.phase) {
            DesktopPlayerPhase.Idle -> IdleOverlay()
            DesktopPlayerPhase.Buffering -> BufferingOverlay()
            else -> {}
        }

        AnimatedVisibility(
            visible = controlsVisible || state.phase == DesktopPlayerPhase.Idle,
            enter = fadeIn(animationSpec = tween(NuvioAnimation.STANDARD_MS)),
            exit = fadeOut(animationSpec = tween(NuvioAnimation.STANDARD_MS)),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(Modifier.fillMaxSize()) {
                // Top gradient
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
                // Bottom gradient
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
                    // Top bar: back, title, settings, fullscreen
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (onBack != null) {
                            HoverIconButton(
                                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back",
                                onClick = onBack,
                            )
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
                                HoverIconButton(
                                    icon = Icons.Rounded.Tune,
                                    contentDescription = "Video settings",
                                    onClick = onVideoSettingsClick,
                                )
                            }
                            if (onFullscreenToggle != null) {
                                HoverIconButton(
                                    icon = Icons.Rounded.Fullscreen,
                                    contentDescription = "Toggle fullscreen (F)",
                                    onClick = onFullscreenToggle,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.weight(1f))

                    // Center controls: seek back, play/pause, seek forward
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        HoverIconButton(
                            icon = Icons.Rounded.Replay10,
                            contentDescription = "Rewind 10 seconds",
                            onClick = { controller.seekBy(-10_000) },
                            size = 52.dp,
                            iconSize = 38.dp,
                        )

                        Spacer(Modifier.width(16.dp))

                        // Play/Pause with scale feedback
                        HoverIconButton(
                            icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlaying) "Pause (Space)" else "Play (Space)",
                            onClick = { if (isPlaying) controller.pause() else controller.play() },
                            size = 64.dp,
                            iconSize = 44.dp,
                            showSpinner = isBuffering,
                        )

                        Spacer(Modifier.width(16.dp))

                        HoverIconButton(
                            icon = Icons.Rounded.Forward10,
                            contentDescription = "Forward 10 seconds",
                            onClick = { controller.seekBy(10_000) },
                            size = 52.dp,
                            iconSize = 38.dp,
                        )
                    }

                    Spacer(Modifier.weight(1f))

                    // Bottom controls: seek bar + action buttons
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        val sliderColors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.White.copy(alpha = 0.28f),
                        )

                        // Seek slider with drag feedback
                        Slider(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(24.dp)
                                .graphicsLayer(scaleY = if (isDraggingSeek) 1.2f else 0.72f),
                            value = state.positionMs.coerceIn(0L, durationMs).toFloat(),
                            onValueChange = {
                                isDraggingSeek = true
                                controller.seekTo(it.toLong())
                            },
                            onValueChangeFinished = {
                                isDraggingSeek = false
                            },
                            valueRange = 0f..durationMs.toFloat(),
                            colors = sliderColors,
                        )

                        // Time pills
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

                        // Action pill bar
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
                                    ActionPillButton(icon = Icons.Rounded.AspectRatio, label = "Fit",
                                        onClick = { onResizeModeClick?.invoke() })
                                    ActionPillButton(icon = Icons.Rounded.Speed,
                                        label = formatPlaybackSpeedLabel(state.playbackSpeed),
                                        onClick = { onSpeedClick?.invoke() })
                                    ActionPillButton(
                                        icon = if (isMuted) Icons.AutoMirrored.Rounded.VolumeOff
                                               else Icons.AutoMirrored.Rounded.VolumeUp,
                                        label = "Vol", onClick = { showVolume = !showVolume })
                                    ActionPillButton(icon = Icons.Rounded.Subtitles, label = "Subs",
                                        onClick = { onSubtitleClick?.invoke() })
                                    ActionPillButton(icon = Icons.Rounded.AudioFile, label = "Audio",
                                        onClick = { onAudioClick?.invoke() })
                                    if (onSourcesClick != null) {
                                        ActionPillButton(icon = Icons.Rounded.SwapHoriz, label = "Sources",
                                            onClick = onSourcesClick)
                                    }
                                    if (onEpisodesClick != null) {
                                        ActionPillButton(icon = Icons.Rounded.VideoLibrary, label = "Episodes",
                                            onClick = onEpisodesClick)
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

/** Icon button with hover highlight and optional tooltip */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun HoverIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    iconSize: Dp = 20.dp,
    showSpinner: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                when {
                    isHovered -> Color.White.copy(alpha = 0.25f)
                    else -> Color.Black.copy(alpha = 0.35f)
                }
            )
            .hoverable(interactionSource)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (showSpinner) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 3.dp,
                modifier = Modifier.size(iconSize),
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(iconSize),
            )
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

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ActionPillButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(22.dp))
            .background(if (isHovered) Color.White.copy(alpha = 0.15f) else Color.Transparent)
            .hoverable(interactionSource)
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
            contentDescription = if (isMuted) "Unmute (M)" else "Mute (M)",
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
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = "Play",
                tint = Color(0xFFE5383B),
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text("Nuvio Player", color = Color(0xFF999999), fontSize = 18.sp)
        }
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
    // Spacebar handled by BindPlayerKeyboardShortcuts AWT dispatcher — don't duplicate
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
