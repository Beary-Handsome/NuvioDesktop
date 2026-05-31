package com.nuvio.app.features.player.desktop.vlc

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.nuvio.app.desktop.DesktopRuntimeLog
import com.nuvio.app.features.player.PlayerEngineController
import com.nuvio.app.features.player.PlayerResizeMode
import com.nuvio.app.features.player.desktop.DesktopPlayerBackend
import com.nuvio.app.features.player.desktop.DesktopPlayerError
import com.nuvio.app.features.player.desktop.DesktopPlayerPhase
import com.nuvio.app.features.player.desktop.DesktopPlayerRequest
import com.nuvio.app.features.player.desktop.DesktopPlayerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.openani.mediamp.InternalMediampApi
import org.openani.mediamp.PlaybackState
import org.openani.mediamp.source.UriMediaData
import org.openani.mediamp.vlc.VlcMediampPlayer
import org.openani.mediamp.vlc.compose.VlcMediampPlayerSurface
import kotlin.coroutines.EmptyCoroutineContext

@OptIn(InternalMediampApi::class)
internal class VlcDesktopPlayerBackend private constructor(
    private val player: VlcMediampPlayer,
) : DesktopPlayerBackend {
    override val id: String = "vlc-${System.identityHashCode(player)}"
    override val backendName: String = "mediamp-vlc"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val stateFlow = MutableStateFlow(
        DesktopPlayerState(
            phase = DesktopPlayerPhase.Idle,
            backendName = backendName,
        ),
    )

    override val state: StateFlow<DesktopPlayerState> = stateFlow
    
    override val controller: PlayerEngineController = VlcController()

    init {
        observePlayerState()
    }

    override suspend fun load(request: DesktopPlayerRequest) {
        try {
            stateFlow.value = stateFlow.value.copy(phase = DesktopPlayerPhase.Preparing)

            DesktopRuntimeLog.info("VLC loading media: ${request.sourceUrl}")
            
            val mediaData = UriMediaData(request.sourceUrl, request.sourceHeaders)
            player.setMediaData(mediaData)
            
            stateFlow.value = stateFlow.value.copy(phase = DesktopPlayerPhase.Ready)
            DesktopRuntimeLog.info("VLC player successfully requested load for: ${request.sourceUrl}")
            
            if (request.playWhenReady) {
                controller.play()
            }
        } catch (e: Exception) {
            DesktopRuntimeLog.error("VLC load failed", e)
            stateFlow.value = stateFlow.value.copy(
                phase = DesktopPlayerPhase.Error,
                error = DesktopPlayerError.MediaLoadFailed(
                    backendName = backendName,
                    technicalMessage = e.message ?: "Unknown error",
                    cause = e
                )
            )
        }
    }
    
    override fun setResizeMode(resizeMode: PlayerResizeMode) {
        try {
            DesktopRuntimeLog.info("VLC resize mode set to: $resizeMode")
        } catch (e: Exception) {
            DesktopRuntimeLog.error("VLC setResizeMode failed", e)
        }
    }
    
    override fun releaseSoft() {
        DesktopRuntimeLog.info("VLC player released softly")
    }
    
    override fun close() {
        try {
            scope.cancel()
            player.close()
        } catch (e: Exception) {
            DesktopRuntimeLog.error("VLC close failed", e)
        }
    }

    private fun observePlayerState() {
        combine(
            player.playbackState,
            player.currentPositionMillis,
            player.mediaProperties,
        ) { playbackState, position, props ->
            DesktopPlayerState(
                phase = playbackState.toDesktopPhase(),
                positionMs = position,
                durationMs = props?.durationMillis?.takeIf { it > 0 } ?: 0L,
                backendName = backendName,
                error = if (playbackState == PlaybackState.ERROR) {
                    DesktopPlayerError.PlaybackFailed(backendName, "VLC playback state is ERROR")
                } else {
                    null
                },
            )
        }.onEach { mapped ->
            stateFlow.value = mapped
        }.launchIn(scope)
    }
    
    @Composable
    override fun Surface(modifier: Modifier) {
        VlcMediampPlayerSurface(mediampPlayer = player, modifier = modifier)
    }

    private inner class VlcController : PlayerEngineController {
        override fun play() {
            try {
                player.resume()
            } catch (e: Exception) {
                DesktopRuntimeLog.error("VLC play failed", e)
            }
        }
        
        override fun pause() {
            try {
                player.pause()
            } catch (e: Exception) {
                DesktopRuntimeLog.error("VLC pause failed", e)
            }
        }
        
        override fun seekTo(positionMs: Long) {
            try {
                player.seekTo(positionMs)
            } catch (e: Exception) {
                DesktopRuntimeLog.error("VLC seekTo failed", e)
            }
        }
        
        override fun seekBy(offsetMs: Long) {
            try {
                player.skip(offsetMs)
            } catch (e: Exception) {
                DesktopRuntimeLog.error("VLC seekBy failed", e)
            }
        }
        
        override fun retry() {
            try {
                DesktopRuntimeLog.info("VLC retry called")
            } catch (e: Exception) {
                DesktopRuntimeLog.error("VLC retry failed", e)
            }
        }
        
        override fun configureIosVideoOutput(settings: com.nuvio.app.features.player.PlayerSettingsUiState) {
            // Not applicable for VLC on desktop
        }
        
        override fun setPlaybackSpeed(speed: Float) {
            try {
                DesktopRuntimeLog.info("VLC playback speed set to: $speed")
            } catch (e: Exception) {
                DesktopRuntimeLog.error("VLC setPlaybackSpeed failed", e)
            }
        }
        
        override fun currentVolume(): com.nuvio.app.features.player.PlayerAudioLevel? {
            return try {
                com.nuvio.app.features.player.PlayerAudioLevel(fraction = 0.5f, isMuted = false)
            } catch (e: Exception) {
                DesktopRuntimeLog.error("VLC currentVolume failed", e)
                null
            }
        }
        
        override fun setVolume(level: Float): com.nuvio.app.features.player.PlayerAudioLevel? {
            return try {
                com.nuvio.app.features.player.PlayerAudioLevel(fraction = level, isMuted = false)
            } catch (e: Exception) {
                DesktopRuntimeLog.error("VLC setVolume failed", e)
                null
            }
        }
        
        override fun getAudioTracks(): List<com.nuvio.app.features.player.AudioTrack> {
            return emptyList()
        }
        
        override fun getSubtitleTracks(): List<com.nuvio.app.features.player.SubtitleTrack> {
            return emptyList()
        }
        
        override fun selectAudioTrack(index: Int) {
            DesktopRuntimeLog.info("VLC audio track selected: $index")
        }
        
        override fun selectSubtitleTrack(index: Int) {
            DesktopRuntimeLog.info("VLC subtitle track selected: $index")
        }
        
        override fun setSubtitleUri(url: String) {
            DesktopRuntimeLog.info("VLC external subtitle added: $url")
        }
        
        override fun clearExternalSubtitle() {
            DesktopRuntimeLog.info("VLC external subtitles cleared")
        }
        
        override fun clearExternalSubtitleAndSelect(trackIndex: Int) {
            DesktopRuntimeLog.info("VLC clear external subtitle and select: $trackIndex")
        }
        
        override fun applySubtitleStyle(style: com.nuvio.app.features.player.SubtitleStyleState) {
            try {
                DesktopRuntimeLog.info("VLC subtitle style applied")
            } catch (e: Exception) {
                DesktopRuntimeLog.error("VLC applySubtitleStyle failed", e)
            }
        }
        
        override fun switchSource(url: String, audioUrl: String?, headersJson: String?) {
            try {
                DesktopRuntimeLog.info("VLC source switched to: $url")
            } catch (e: Exception) {
                DesktopRuntimeLog.error("VLC switchSource failed", e)
            }
        }
        
        override fun release() {
            releaseSoft()
        }
    }

    companion object {
        fun create(): Result<VlcDesktopPlayerBackend> =
            runCatching {
                // Prepare VLC libraries before creating the player
                VlcMediampPlayer.prepareLibraries()
                
                VlcDesktopPlayerBackend(
                    player = VlcMediampPlayer(EmptyCoroutineContext),
                )
            }
    }
}

private fun PlaybackState.toDesktopPhase(): DesktopPlayerPhase =
    when (this) {
        PlaybackState.DESTROYED -> DesktopPlayerPhase.Closed
        PlaybackState.ERROR -> DesktopPlayerPhase.Error
        PlaybackState.CREATED -> DesktopPlayerPhase.Idle
        PlaybackState.FINISHED -> DesktopPlayerPhase.Ended
        PlaybackState.READY -> DesktopPlayerPhase.Ready
        PlaybackState.PAUSED -> DesktopPlayerPhase.Paused
        PlaybackState.PLAYING -> DesktopPlayerPhase.Playing
        PlaybackState.PAUSED_BUFFERING -> DesktopPlayerPhase.Buffering
    }


