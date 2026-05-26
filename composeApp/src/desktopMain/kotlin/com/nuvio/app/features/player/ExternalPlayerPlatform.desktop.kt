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
        val execLower = executable.lowercase()
        when {
            execLower.contains("vlc") -> {
                args.add(request.sourceUrl)
                args.add("--play-and-exit")
            }
            execLower.contains("mpv") || execLower.contains("celluloid") -> {
                args.add(request.sourceUrl)
            }
            execLower.contains("iina") -> {
                args.add("--no-stdin")
                args.add("--keep-running")
                args.add(request.sourceUrl)
            }
            execLower.contains("kodi") -> {
                args.add("--play")
                args.add(request.sourceUrl)
            }
            else -> {
                args.add(request.sourceUrl)
            }
        }

        return try {
            val pb = ProcessBuilder(args)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
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
        val candidates = mutableListOf<Pair<String, String>>()
        fun add(path: String?, displayName: String) {
            if (path != null) candidates.add(path to displayName)
        }
        add(resolveInPath("vlc"), "VLC")
        add(resolveInPath("mpv"), "MPV")
        add(resolveInPath("celluloid"), "Celluloid")
        add(resolveInPath("kodi"), "Kodi")
        // Flatpak exports use reverse-domain names (e.g. tv.kodi.Kodi)
        add(resolveLinuxFlatpakPath("tv.kodi.Kodi"), "Kodi")
        add(resolveLinuxFlatpakPath("org.videolan.VLC"), "VLC")
        add(resolveLinuxFlatpakPath("io.mpv.Mpv"), "MPV")
        add(resolveLinuxFlatpakPath("io.github.celluloid_player.Celluloid"), "Celluloid")
        add(resolveLinuxSnapPath("kodi"), "Kodi")
        add(resolveLinuxSnapPath("vlc"), "VLC")
        add(resolveLinuxSnapPath("mpv"), "MPV")
        return candidates.distinctBy { it.first }.map { (path, name) ->
            ExternalPlayerApp(path, name)
        }
    }

    private fun resolveLinuxFlatpakPath(name: String): String? {
        val home = System.getProperty("user.home") ?: return null
        val flatpakDirs = listOf(
            "/var/lib/flatpak/exports/bin",
            "$home/.local/share/flatpak/exports/bin",
        )
        return flatpakDirs.firstOrNull { dir -> File(dir, name).canExecute() }
            ?.let { dir -> File(dir, name).absolutePath }
    }

    private fun resolveLinuxSnapPath(name: String): String? {
        val snapDir = "/snap/bin"
        return if (File(snapDir, name).canExecute()) File(snapDir, name).absolutePath else null
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
