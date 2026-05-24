package com.nuvio.app.features.player

import java.io.File

internal actual object ExternalPlayerPlatform {
    actual fun defaultPlayerId(): String? = linuxPlayers().firstOrNull()?.id

    actual fun availablePlayers(): List<ExternalPlayerApp> = linuxPlayers()

    actual fun open(
        request: ExternalPlayerPlaybackRequest,
        playerId: String?,
    ): ExternalPlayerOpenResult {
        val players = availablePlayers()
        if (players.isEmpty()) return ExternalPlayerOpenResult.NoPlayerAvailable

        val player = players.find { it.id == playerId } ?: players.first()
        val executable = player.id

        val args = mutableListOf(executable)
        when {
            executable.contains("vlc") -> {
                args.add(request.sourceUrl)
                args.add("--play-and-exit")
            }
            executable.contains("mpv") || executable.contains("celluloid") -> {
                args.add(request.sourceUrl)
            }
            else -> {
                args.add(request.sourceUrl)
            }
        }

        return try {
            val pb = ProcessBuilder(args)
                .redirectErrorStream(true)
            pb.start()
            ExternalPlayerOpenResult.Opened
        } catch (e: Exception) {
            ExternalPlayerOpenResult.Failed
        }
    }

    private fun isLinux(): Boolean =
        System.getProperty("os.name")?.lowercase()?.contains("nux") == true

    private fun linuxPlayers(): List<ExternalPlayerApp> {
        if (!isLinux()) return emptyList()
        val players = mutableListOf<ExternalPlayerApp>()
        if (isExecutableInPath("vlc")) players.add(ExternalPlayerApp("vlc", "VLC"))
        if (isExecutableInPath("mpv")) players.add(ExternalPlayerApp("mpv", "MPV"))
        return players
    }

    private fun isExecutableInPath(name: String): Boolean {
        val path = System.getenv("PATH") ?: return false
        return path.split(File.pathSeparatorChar)
            .any { dir -> File(dir, name).canExecute() }
    }
}
