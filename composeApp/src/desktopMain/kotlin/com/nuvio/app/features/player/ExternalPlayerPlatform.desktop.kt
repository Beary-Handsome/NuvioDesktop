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
        val checked = mutableSetOf<String>()

        fun addIfFound(id: String, name: String): Boolean {
            if (id in checked) return false
            checked.add(id)
            val found = isExecutableInPath(id) || isExecutableAtCommonPath(id)
            if (found) players.add(ExternalPlayerApp(id, name))
            return found
        }

        addIfFound("vlc.exe", "VLC")
        addIfFound("mpv.exe", "MPV")
        addIfFound("mpv.com", "MPV")
        if (!checked.any { it.startsWith("vlc") }) addIfFound("vlc", "VLC")
        if (!checked.any { it.startsWith("mpv") }) addIfFound("mpv", "MPV")
        return players
    }

    private fun isExecutableAtCommonPath(name: String): Boolean {
        val programFiles = System.getenv("ProgramFiles") ?: "C:\\Program Files"
        val programFilesX86 = System.getenv("ProgramFiles(x86)") ?: "C:\\Program Files (x86)"
        val localAppData = System.getenv("LOCALAPPDATA") ?: "${System.getProperty("user.home")}\\AppData\\Local"

        val commonDirs = listOf(
            "$programFiles\\VideoLAN\\VLC",
            "$programFilesX86\\VideoLAN\\VLC",
            "$localAppData\\Programs\\VLC",
            "$programFiles\\mpv",
            "$programFiles\\mpv.net",
            "$programFiles\\MPV",
            "$programFiles\\MPlayer",
            "$programFiles\\MPlayer for Windows",
            "$localAppData\\mpv",
            "$localAppData\\Programs\\mpv",
        )

        return commonDirs.any { dir -> File(dir, name).canExecute() }
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
