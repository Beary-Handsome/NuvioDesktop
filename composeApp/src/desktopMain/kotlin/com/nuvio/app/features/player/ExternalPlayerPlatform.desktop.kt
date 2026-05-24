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
            executable.contains("kodi") -> {
                args.add("--play")
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
        resolveInPath("vlc")?.let { players.add(ExternalPlayerApp(it, "VLC")) }
        resolveInPath("mpv")?.let { players.add(ExternalPlayerApp(it, "MPV")) }
        resolveInPath("kodi")?.let { players.add(ExternalPlayerApp(it, "Kodi")) }
        return players
    }

    private fun windowsPlayers(): List<ExternalPlayerApp> {
        val players = mutableListOf<ExternalPlayerApp>()

        fun addIfFound(vararg names: String, displayName: String) {
            for (name in names) {
                val resolved = resolveInPath(name) ?: resolveAtCommonPath(name)
                if (resolved != null) {
                    players.add(ExternalPlayerApp(resolved, displayName))
                    return
                }
            }
        }

        addIfFound("vlc.exe", "vlc", displayName = "VLC")
        addIfFound("mpv.exe", "mpv.com", "mpv", displayName = "MPV")
        addIfFound("kodi.exe", "kodi", displayName = "Kodi")
        return players
    }

    private fun resolveAtCommonPath(name: String): String? {
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
            "$programFiles\\Kodi",
            "$programFilesX86\\Kodi",
            "$localAppData\\Programs\\Kodi",
        )

        return commonDirs.firstOrNull { dir -> File(dir, name).canExecute() }
            ?.let { dir -> File(dir, name).absolutePath }
    }

    private fun macPlayers(): List<ExternalPlayerApp> {
        val players = mutableListOf<ExternalPlayerApp>()
        resolveInPath("vlc")?.let { players.add(ExternalPlayerApp(it, "VLC")) }
        resolveInPath("iina")?.let { players.add(ExternalPlayerApp(it, "IINA")) }
        resolveInPath("mpv")?.let { players.add(ExternalPlayerApp(it, "MPV")) }
        resolveInPath("kodi")?.let { players.add(ExternalPlayerApp(it, "Kodi")) }
        return players
    }

    private fun resolveInPath(name: String): String? {
        val path = System.getenv("PATH") ?: return null
        return path.split(File.pathSeparatorChar)
            .firstOrNull { dir -> File(dir, name).canExecute() }
            ?.let { dir -> File(dir, name).absolutePath }
    }
}
