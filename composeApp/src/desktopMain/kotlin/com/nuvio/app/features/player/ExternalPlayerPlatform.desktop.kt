package com.nuvio.app.features.player

import java.io.File

internal actual object ExternalPlayerPlatform {
    actual fun defaultPlayerId(): String? = desktopPlayers().firstOrNull()?.id

    actual fun availablePlayers(): List<ExternalPlayerApp> = desktopPlayers()

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
            executable.contains("iina") -> {
                args.add("--no-stdin")
                args.add("--keep-running")
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

    private fun osName(): String =
        System.getProperty("os.name")?.lowercase().orEmpty()

    private fun desktopPlayers(): List<ExternalPlayerApp> {
        val os = osName()
        return when {
            os.contains("nux") -> linuxPlayers()
            os.contains("win") -> windowsPlayers()
            os.contains("mac") -> macPlayers()
            else -> emptyList()
        }
    }

    private fun linuxPlayers(): List<ExternalPlayerApp> {
        val players = mutableListOf<ExternalPlayerApp>()
        if (isExecutableInPath("vlc")) players.add(ExternalPlayerApp("vlc", "VLC"))
        if (isExecutableInPath("mpv")) players.add(ExternalPlayerApp("mpv", "MPV"))
        return players
    }

    private fun windowsPlayers(): List<ExternalPlayerApp> {
        val players = mutableListOf<ExternalPlayerApp>()
        if (isExecutableInPath("vlc.exe")) players.add(ExternalPlayerApp("vlc.exe", "VLC"))
        if (isExecutableInPath("mpv.exe")) players.add(ExternalPlayerApp("mpv.exe", "MPV"))
        if (players.isEmpty()) {
            if (isExecutableInPath("vlc")) players.add(ExternalPlayerApp("vlc", "VLC"))
            if (isExecutableInPath("mpv")) players.add(ExternalPlayerApp("mpv", "MPV"))
        }
        return players
    }

    private fun macPlayers(): List<ExternalPlayerApp> {
        val players = mutableListOf<ExternalPlayerApp>()
        if (isExecutableInPath("vlc")) players.add(ExternalPlayerApp("vlc", "VLC"))
        if (isExecutableInPath("iina")) players.add(ExternalPlayerApp("iina", "IINA"))
        if (isExecutableInPath("mpv")) players.add(ExternalPlayerApp("mpv", "MPV"))
        return players
    }

    private fun isExecutableInPath(name: String): Boolean {
        val path = System.getenv("PATH") ?: return false
        return path.split(File.pathSeparatorChar)
            .any { dir -> File(dir, name).canExecute() }
    }
}
