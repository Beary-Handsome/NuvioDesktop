package com.nuvio.app.features.player

import java.io.File

/**
 * Launches the standalone Tauri player as a child process.
 *
 * Uses [NuvioPlayerLauncher] with crash detection and session gating.
 * Falls back to JSON-config-based launch if CLI-arg launch is unavailable.
 *
 * Usage:
 *   val exitCode = TauriPlayerProcess.launch(
 *       url = "https://...",
 *       title = "Movie Title",
 *   )
 *   when (exitCode) {
 *       0 -> // normal end
 *       1 -> // error
 *       2 -> // user closed
 *   }
 */
object TauriPlayerProcess {

    /**
     * Launch the Tauri player (primary: via CLI args; fallback: JSON config).
     *
     * @return exit code: 0=normal, 1=error, 2=user_closed
     */
    fun launch(
        url: String,
        title: String = "",
        userAgent: String = "libmpv/nuvio",
        referer: String = "",
        startTime: Double = 0.0,
        hwdec: String = "auto",
        volume: Int = 100,
        subFile: String? = null,
    ): Int {
        val config = NuvioPlayerConfig(
            url = url,
            title = title,
            userAgent = userAgent,
            referer = referer,
            startTime = startTime,
            hwdec = hwdec,
            volume = volume,
            subFile = subFile,
        )

        // Primary: use NuvioPlayerLauncher with fallback safety.
        var fallbackReason: String? = null
        val launched = NuvioPlayerLauncher.launchWithFallback(config) { reason ->
            fallbackReason = reason
        }

        if (launched) {
            return 0
        }

        // Fallback: existing JSON-config approach (backward compatibility).
        return legacyLaunch(config)
    }

    /**
     * Non-blocking launch that returns the process handle.
     */
    fun launchAsync(
        url: String,
        title: String = "",
        userAgent: String = "libmpv/nuvio",
        referer: String = "",
        startTime: Double = 0.0,
        hwdec: String = "auto",
        volume: Int = 100,
        subFile: String? = null,
        onExit: (exitCode: Int) -> Unit = {},
    ): Process {
        val config = NuvioPlayerConfig(
            url = url,
            title = title,
            userAgent = userAgent,
            referer = referer,
            startTime = startTime,
            hwdec = hwdec,
            volume = volume,
            subFile = subFile,
        )

        if (NuvioPlayerLauncher.isAvailable()) {
            val process = NuvioPlayerLauncher.launch(config)
            Thread.ofVirtual().start {
                val exitCode = process.waitFor()
                onExit(exitCode)
            }
            return process
        }

        return legacyLaunchAsync(config, onExit)
    }

    // -----------------------------------------------------------------------
    // Legacy JSON-config-based launch (kept for backward compatibility)
    // -----------------------------------------------------------------------

    @kotlinx.serialization.Serializable
    data class LegacyPlayerConfig(
        val url: String,
        val headers: Map<String, String> = emptyMap(),
        @kotlinx.serialization.SerialName("start_position")
        val startPosition: Double? = null,
        val title: String? = null,
        val hwdec: String = "auto",
        val volume: Long = 100,
    )

    private fun resolvePlayerBinary(): String {
        val isWindows = System.getProperty("os.name").lowercase().contains("win")
        val devPaths = listOf(
            "../nuvio-player/src-tauri/target/release/nuvio-player",
            "../nuvio-player/src-tauri/target/debug/nuvio-player",
        )
        for (p in devPaths) {
            val devBinary = p + if (isWindows) ".exe" else ""
            val file = File(devBinary)
            if (file.exists()) return file.absolutePath
        }
        val appDir = System.getProperty("compose.application.resources.dir")
            ?: File(TauriPlayerProcess::class.java.protectionDomain.codeSource.location.toURI())
                .parentFile?.absolutePath
        if (appDir != null) {
            val bundled = File(appDir, if (isWindows) "nuvio-player.exe" else "nuvio-player")
            if (bundled.exists()) return bundled.absolutePath
        }
        error("nuvio-player binary not found")
    }

    private fun legacyLaunch(config: NuvioPlayerConfig): Int {
        val binary = resolvePlayerBinary()
        val configFile = createConfigFile(config)
        return try {
            val process = ProcessBuilder(binary, "--config", configFile.toAbsolutePath().toString())
                .inheritIO()
                .start()
            process.waitFor()
        } finally {
            configFile.toFile().delete()
        }
    }

    private fun legacyLaunchAsync(
        config: NuvioPlayerConfig,
        onExit: (Int) -> Unit,
    ): Process {
        val binary = resolvePlayerBinary()
        val configFile = createConfigFile(config)
        val process = ProcessBuilder(binary, "--config", configFile.toAbsolutePath().toString())
            .inheritIO()
            .start()
        Thread.ofVirtual().start {
            try {
                val exitCode = process.waitFor()
                onExit(exitCode)
            } finally {
                configFile.toFile().delete()
            }
        }
        return process
    }

    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    private fun createConfigFile(config: NuvioPlayerConfig): java.nio.file.Path {
        val headers = mutableMapOf<String, String>()
        headers["User-Agent"] = config.userAgent
        if (config.referer.isNotEmpty()) headers["Referer"] = config.referer

        val legacy = LegacyPlayerConfig(
            url = config.url,
            headers = headers,
            startPosition = if (config.startTime > 0.0) config.startTime else null,
            title = config.title.ifEmpty { null },
            hwdec = config.hwdec,
            volume = config.volume.toLong(),
        )
        val tmpDir = File(System.getProperty("java.io.tmpdir"))
        val file = java.nio.file.Files.createTempFile(tmpDir.toPath(), "nuvio-player-legacy-", ".json")
        java.nio.file.Files.writeString(file, json.encodeToString(legacy))
        return file
    }
}
