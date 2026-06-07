package com.nuvio.app

import java.nio.file.Path

private class DesktopPlatform : Platform {
    override val name: String = "Desktop"
}

actual fun getPlatform(): Platform = DesktopPlatform()

internal actual val isIos: Boolean = false
internal actual val isDesktop: Boolean = true

internal actual fun openMpvConfigFile() {
    val os = System.getProperty("os.name").lowercase()
    val configDir = when {
        os.contains("win") -> Path.of(System.getenv("APPDATA"), "mpv")
        os.contains("nix") || os.contains("nux") || os.contains("mac") -> Path.of(System.getProperty("user.home"), ".config", "mpv")
        else -> return
    }
    val configFile = configDir.resolve("mpv.conf")
    if (!configFile.toFile().exists()) {
        configFile.toFile().parentFile.mkdirs()
        configFile.toFile().writeText(
            "# MPV Configuration for Nuvio Player\n" +
            "# Add your mpv options here (one per line), e.g.:\n" +
            "# hwdec=auto\n" +
            "# profile=gpu-hq\n" +
            "\n",
        )
    }
    when {
        os.contains("win") -> {
            val notepad = Path.of(System.getenv("windir"), "System32", "notepad.exe").toString()
            ProcessBuilder(notepad, configFile.toString())
                .inheritIO()
                .start()
        }
        os.contains("mac") -> Runtime.getRuntime().exec(arrayOf("open", configFile.toString()))
        else -> Runtime.getRuntime().exec(arrayOf("xdg-open", configFile.toString()))
    }
}
