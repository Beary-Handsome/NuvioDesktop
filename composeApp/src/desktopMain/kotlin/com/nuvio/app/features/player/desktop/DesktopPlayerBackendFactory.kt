package com.nuvio.app.features.player.desktop

import com.nuvio.app.desktop.DesktopRuntimeLog
import com.nuvio.app.features.player.PlayerBackendOption
import com.nuvio.app.features.player.PlayerSettingsRepository
import com.nuvio.app.features.player.desktop.mpv.MpvDesktopPlayerBackend
import com.nuvio.app.features.player.desktop.mpv.MpvRuntimeBootstrap
import com.nuvio.app.features.player.desktop.mpv.MpvRuntimeLocator
import com.nuvio.app.features.player.desktop.nativebridge.NativeBridgeDesktopPlayerBackend
import com.nuvio.app.features.player.desktop.nativebridge.NativeBridgeRuntimeLocator
import com.nuvio.app.features.player.desktop.vlc.VlcDesktopPlayerBackend

internal object DesktopPlayerBackendFactory {
    private const val BACKEND_PROPERTY = "nuvio.player.backend"
    private const val BACKEND_ENV = "NUVIO_PLAYER_BACKEND"
    private const val BACKEND_PROPERTY_LEGACY = "nuvio.windows.player.backend"
    private const val BACKEND_ENV_LEGACY = "NUVIO_WINDOWS_PLAYER_BACKEND"

    fun createDesktopBackend(): DesktopPlayerBackend {
        // First, try to use user preference from settings
        val userPreference = try {
            PlayerSettingsRepository.getPlayerBackend()
        } catch (e: Exception) {
            DesktopRuntimeLog.error("Failed to get player backend preference from settings", e)
            PlayerBackendOption.AUTO
        }
        
        // Convert user preference to selection
        val userSelection = when (userPreference) {
            PlayerBackendOption.MPV -> DesktopPlayerBackendSelection(DesktopPlayerBackendKind.Mpv, "mpv", "user-preference")
            PlayerBackendOption.VLC -> DesktopPlayerBackendSelection(DesktopPlayerBackendKind.Vlc, "vlc", "user-preference")
            PlayerBackendOption.AUTO -> {
                // Fall back to system properties/env vars if AUTO is selected
                DesktopPlayerBackendSelection.resolve()
            }
        }
        
        DesktopRuntimeLog.info("Selected player backend request=${userSelection.value} source=${userSelection.source}")
        return when (userSelection.backend) {
            DesktopPlayerBackendKind.None -> unavailable(
                backendName = "none",
                technicalMessage = "Player backend disabled by configuration.",
                selection = userSelection,
            )
            DesktopPlayerBackendKind.Mpv -> createMpvOrUnavailable(userSelection)
            DesktopPlayerBackendKind.Vlc -> createVlcOrUnavailable(userSelection)
            DesktopPlayerBackendKind.Auto -> createAutoWithFallback(userSelection)
            DesktopPlayerBackendKind.Native -> createNativeWithMpvFallback(userSelection)
        }
    }

    private fun createMpvOrUnavailable(selection: DesktopPlayerBackendSelection): DesktopPlayerBackend =
        createMpvOrNull(selection) ?: unavailable(
            backendName = "mediamp-mpv",
            technicalMessage = "MPV backend is unavailable.",
            selection = selection,
        )
        
    private fun createVlcOrUnavailable(selection: DesktopPlayerBackendSelection): DesktopPlayerBackend =
        createVlcOrNull() ?: unavailable(
            backendName = "mediamp-vlc",
            technicalMessage = "VLC backend is unavailable.",
            selection = selection,
        )

    private fun createVlcOrNull(): DesktopPlayerBackend? =
        VlcDesktopPlayerBackend.create()
            .onSuccess {
                DesktopRuntimeLog.info("Selected player backend=${it.backendName}")
            }
            .getOrNull()

    private fun createAutoWithFallback(selection: DesktopPlayerBackendSelection): DesktopPlayerBackend =
        createVlcOrNull() ?: createMpvOrNull(selection) ?: unavailable(
            backendName = "auto",
            technicalMessage = "VLC and MPV backends are unavailable.",
            selection = selection,
        )

    private fun createNativeWithMpvFallback(selection: DesktopPlayerBackendSelection): DesktopPlayerBackend {
        val nativeRuntime = NativeBridgeRuntimeLocator.resolve()
        if (!nativeRuntime.available) {
            DesktopRuntimeLog.warn(
                "Windows native bridge unavailable before playback; trying MPV fallback diagnostics=${nativeRuntime.diagnostics}",
            )
            return createMpvOrUnavailable(selection)
        }
        return NativeBridgeDesktopPlayerBackend.create()
            .onSuccess {
                DesktopRuntimeLog.info("Selected player backend=${it.backendName} (source=${selection.source} request=${selection.value})")
            }
            .getOrElse { throwable ->
                DesktopRuntimeLog.error("Windows native bridge init failed; trying MPV fallback", throwable)
                createMpvOrUnavailable(selection)
            }
    }

    private fun createMpvOrNull(selection: DesktopPlayerBackendSelection): DesktopPlayerBackend? {
        val runtime = MpvRuntimeLocator.resolve()
        val bootstrap = MpvRuntimeBootstrap.apply(runtime)
        if (!bootstrap.success) {
            DesktopRuntimeLog.error("MPV runtime bootstrap failed diagnostics=${bootstrap.diagnostics}", bootstrap.error)
            return null
        }
        return MpvDesktopPlayerBackend.create(runtime)
            .onSuccess {
                DesktopRuntimeLog.info("Selected player backend=${it.backendName} (source=${selection.source} request=${selection.value})")
            }
            .onFailure { DesktopRuntimeLog.error("MPV backend init failed", it) }
            .getOrNull()
    }

    private fun unavailable(
        backendName: String,
        technicalMessage: String,
        selection: DesktopPlayerBackendSelection,
    ): DesktopPlayerBackend {
        DesktopRuntimeLog.warn("Selected player backend=$backendName (source=${selection.source} request=${selection.value})")
        return UnavailableDesktopPlayerBackend(
            backendName = backendName,
            error = DesktopPlayerError.RuntimeUnavailable(
                backendName = backendName,
                technicalMessage = technicalMessage,
                suggestedAction = "Check the backend runtime files and restart the app.",
            ),
        )
    }

    private enum class DesktopPlayerBackendKind {
        Auto,
        Mpv,
        Vlc,
        Native,
        None,
    }

    private data class DesktopPlayerBackendSelection(
        val backend: DesktopPlayerBackendKind,
        val value: String,
        val source: String,
    ) {
        companion object {
            fun resolve(): DesktopPlayerBackendSelection {
                val property = System.getProperty(BACKEND_PROPERTY)?.trim()?.lowercase()
                if (!property.isNullOrBlank()) return fromValue(property, "system-property:$BACKEND_PROPERTY")
                val env = System.getenv(BACKEND_ENV)?.trim()?.lowercase()
                if (!env.isNullOrBlank()) return fromValue(env, "env:$BACKEND_ENV")
                val propertyLegacy = System.getProperty(BACKEND_PROPERTY_LEGACY)?.trim()?.lowercase()
                if (!propertyLegacy.isNullOrBlank()) return fromValue(propertyLegacy, "system-property:$BACKEND_PROPERTY_LEGACY")
                val envLegacy = System.getenv(BACKEND_ENV_LEGACY)?.trim()?.lowercase()
                if (!envLegacy.isNullOrBlank()) return fromValue(envLegacy, "env:$BACKEND_ENV_LEGACY")
                return DesktopPlayerBackendSelection(DesktopPlayerBackendKind.Auto, "auto", "default")
            }

            private fun fromValue(value: String, source: String): DesktopPlayerBackendSelection =
                DesktopPlayerBackendSelection(
                    backend = when (value) {
                        "mpv" -> DesktopPlayerBackendKind.Mpv
                        "vlc" -> DesktopPlayerBackendKind.Vlc
                        "native" -> DesktopPlayerBackendKind.Native
                        "none" -> DesktopPlayerBackendKind.None
                        else -> DesktopPlayerBackendKind.Auto
                    },
                    value = value,
                    source = source,
                )
        }
    }
}
