package com.nuvio.app.features.player

import com.nuvio.app.desktop.DesktopExternalPlaybackWindowController
import com.nuvio.app.desktop.DesktopRuntimeLog
import java.io.File
import java.lang.ProcessBuilder.Redirect
import java.util.concurrent.CompletableFuture

internal actual object ExternalPlayerPlatform {
    private val isWindows: Boolean by lazy {
        System.getProperty("os.name")?.contains("Windows", ignoreCase = true) == true
    }

    private val allDefinitions: List<DesktopPlayerDefinition> by lazy {
        if (isWindows) windowsDesktopPlayerDefinitions else linuxExternalPlayerDefinitions
    }

    private val detectedPlayers: List<DesktopPlayerInstall> by lazy {
        val players = if (isWindows) {
            detectWindowsExternalPlayers().map { it.toDesktopPlayerInstall() }
        } else {
            detectLinuxExternalPlayers()
        }
        DesktopRuntimeLog.info(
            "externalPlayer detection complete count=${players.size} ids=${players.joinToString { it.definition.id }}",
        )
        players
    }

    actual fun defaultPlayerId(): String? =
        detectedPlayers.firstOrNull()?.definition?.id

    actual fun availablePlayers(): List<ExternalPlayerApp> =
        detectedPlayers.map { install ->
            ExternalPlayerApp(
                id = install.definition.id,
                name = install.definition.name,
            )
        }

    actual fun open(
        request: ExternalPlayerPlaybackRequest,
        playerId: String?,
        onProgressUpdate: ((positionMs: Long) -> Unit)?,
        onExit: ((lastPositionMs: Long) -> Unit)?,
    ): ExternalPlayerOpenResult {
        DesktopRuntimeLog.info(
            "externalPlayer open requested configuredId=${playerId ?: "none"} " +
                "sourceKind=${request.sourceUrl.safeSourceKind()} sourceKey=${request.sourceUrl.safeSourceKey()} " +
                "headers=${request.sourceHeaders.keys.sorted()} audio=${!request.sourceAudioUrl.isNullOrBlank()} " +
                "initialPositionMs=${request.initialPositionMs.coerceAtLeast(0L)} " +
                "durationMs=${request.videoDurationMs}",
        )
        if (playerId.isNullOrBlank()) {
            DesktopRuntimeLog.warn("externalPlayer open rejected: no configured player")
            return ExternalPlayerOpenResult.NotConfigured
        }
        val knownDefinition = allDefinitions.firstOrNull { it.id == playerId }
            ?: run {
                DesktopRuntimeLog.warn("externalPlayer open rejected: unknown configured id=$playerId")
                return ExternalPlayerOpenResult.NotConfigured
            }
        val install = detectedPlayers.firstOrNull { it.definition.id == playerId }
            ?: run {
                DesktopRuntimeLog.warn("External player unavailable id=${knownDefinition.id}")
                return ExternalPlayerOpenResult.NoPlayerAvailable
            }
        val commandResult = buildDesktopPlayerCommand(install, request)
        val command = commandResult.command
            ?: run {
                DesktopRuntimeLog.warn(
                    "External player launch rejected id=${install.definition.id} reason=${commandResult.failureReason}",
                )
                return ExternalPlayerOpenResult.Failed
            }
        return runCatching {
            val diagnostics = desktopPlayerLaunchDiagnostics(install, request, command)
            DesktopRuntimeLog.info(
                "externalPlayer command prepared id=${diagnostics.playerId} kind=${diagnostics.kind} " +
                    "sourceKind=${diagnostics.sourceKind} sourceKey=${diagnostics.sourceKey} " +
                    "sourceExt=${diagnostics.sourceExtension ?: "none"} headers=${diagnostics.headerNames} " +
                    "audio=${diagnostics.hasSeparateAudio} initialPositionMs=${diagnostics.initialPositionMs} " +
                    "seekNote=${diagnostics.seekSupportNote} command=${diagnostics.commandPreview}",
            )
            val startMs = System.currentTimeMillis()
            val process = ProcessBuilder(command)
                .redirectOutput(Redirect.DISCARD)
                .redirectError(Redirect.DISCARD)
                .start()
            val processPid = runCatching { process.pid() }.getOrNull()
            DesktopRuntimeLog.info(
                "externalPlayer launched id=${install.definition.id} pid=${processPid ?: "unknown"} " +
                    "elapsedLaunchMs=${System.currentTimeMillis() - startMs} executable=${install.executablePath}",
            )
            DesktopExternalPlaybackWindowController.minimizeToTray(install.definition.id, processPid)
            monitorExternalPlayerProcess(
                process = process,
                playerId = install.definition.id,
                startedAtMs = startMs,
                initialPositionMs = request.initialPositionMs,
                durationMs = request.videoDurationMs,
                logFilePath = commandResult.logFilePath,
                onProgressUpdate = onProgressUpdate,
                onExit = onExit,
            )
            ExternalPlayerOpenResult.Opened
        }.getOrElse { throwable ->
            DesktopRuntimeLog.error("External player launch failed id=${install.definition.id}", throwable)
            ExternalPlayerOpenResult.Failed
        }
    }

    private fun monitorExternalPlayerProcess(
        process: Process,
        playerId: String,
        startedAtMs: Long,
        initialPositionMs: Long,
        durationMs: Long,
        logFilePath: String?,
        onProgressUpdate: ((positionMs: Long) -> Unit)?,
        onExit: ((lastPositionMs: Long) -> Unit)?,
    ) {
        CompletableFuture.runAsync {
            val pid = runCatching { process.pid() }.getOrNull()
            DesktopRuntimeLog.info("externalPlayer monitor start id=$playerId pid=${pid ?: "unknown"}")
            var lastKnownPositionMs = initialPositionMs
            var lastPollTimeMs = startedAtMs

            while (process.isAlive()) {
                Thread.sleep(5000)

                val newPosition = if (logFilePath != null) {
                    parsePositionFromMpvLog(File(logFilePath)) ?: estimateWallClockPosition(
                        startedAtMs, initialPositionMs, lastKnownPositionMs, lastPollTimeMs, durationMs,
                    )
                } else {
                    estimateWallClockPosition(
                        startedAtMs, initialPositionMs, lastKnownPositionMs, lastPollTimeMs, durationMs,
                    )
                }

                if (newPosition > lastKnownPositionMs) {
                    lastKnownPositionMs = newPosition
                    lastPollTimeMs = System.currentTimeMillis()
                    onProgressUpdate?.invoke(lastKnownPositionMs)
                }
            }

            val exitCode = runCatching { process.exitValue() }.getOrNull()
            DesktopRuntimeLog.info(
                "externalPlayer exited id=$playerId pid=${pid ?: "unknown"} exitCode=${exitCode ?: "unknown"} " +
                    "elapsedMs=${System.currentTimeMillis() - startedAtMs} " +
                    "lastKnownPositionMs=$lastKnownPositionMs",
            )
            onExit?.invoke(lastKnownPositionMs)
            DesktopExternalPlaybackWindowController.restoreFromTray("external-player-exit:$playerId")
        }
    }

    private fun parsePositionFromMpvLog(logFile: File): Long? {
        if (!logFile.exists() || !logFile.canRead()) return null
        return try {
            val lines = logFile.readLines()
            for (i in lines.indices.reversed()) {
                val line = lines[i]
                val avMatch = Regex("AV: (\\d+):(\\d+):(\\d+) /").find(line)
                if (avMatch != null) {
                    val hours = avMatch.groupValues[1].toLong()
                    val minutes = avMatch.groupValues[2].toLong()
                    val seconds = avMatch.groupValues[3].toLong()
                    return (hours * 3600000L + minutes * 60000L + seconds * 1000L)
                }
            }
            null
        } catch (e: Exception) {
            DesktopRuntimeLog.warn("externalPlayer mpv log parse error: ${e.message}")
            null
        }
    }

    private fun estimateWallClockPosition(
        startedAtMs: Long,
        initialPositionMs: Long,
        lastKnownPositionMs: Long,
        lastPollTimeMs: Long,
        durationMs: Long,
    ): Long {
        val elapsedSinceStart = System.currentTimeMillis() - startedAtMs
        val estimatedFromStart = initialPositionMs + elapsedSinceStart
        if (durationMs > 0L && estimatedFromStart >= durationMs) {
            return durationMs
        }
        if (estimatedFromStart > lastKnownPositionMs) {
            return estimatedFromStart
        }
        return lastKnownPositionMs
    }
}

internal fun String.safeSourceKind(): String = when {
    startsWith("file:", ignoreCase = true) -> "file-uri"
    startsWith("http://", ignoreCase = true) -> "http"
    startsWith("https://", ignoreCase = true) -> "https"
    else -> "other"
}

internal fun String.safeSourceKey(): String =
    hashCode().toUInt().toString(16)
