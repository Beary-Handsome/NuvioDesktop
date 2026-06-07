package com.nuvio.app.features.player

import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

data class NuvioPlayerConfig(
    val url: String,
    val title: String = "",
    val userAgent: String = "libmpv/nuvio",
    val referer: String = "",
    val startTime: Double = 0.0,
    val hwdec: String = "auto",
    val volume: Int = 100,
    val subFile: String? = null,
)

/**
 * Launches the standalone nuvio-player binary as a child process.
 *
 * Features:
 * - Three-tier binary detection (bundled → dev → PATH)
 * - Startup timeout detection (5s) — falls back if crash-on-launch
 * - Session crash gating — disables after exit code 1
 * - Persistent logging to ~/.config/nuvio/player-launcher.log
 */
object NuvioPlayerLauncher {

    private const val BINARY_NAME_WIN = "nuvio-player.exe"
    private const val BINARY_NAME_UNIX = "nuvio-player"
    private const val LAUNCH_TIMEOUT_SECONDS = 5L
    private val TIMESTAMP_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    private var cachedBinaryPath: String? = null
    private var sessionDisabled = false
    private val logFile: File by lazy {
        File(System.getProperty("user.home"), ".config/nuvio/player-launcher.log").also {
            it.parentFile?.mkdirs()
        }
    }

    // -------------------------------------------------------------------
    // Logging
    // -------------------------------------------------------------------

    private fun log(msg: String) {
        val entry = "[${LocalDateTime.now().format(TIMESTAMP_FMT)}] $msg"
        // Always print to stderr for dev visibility
        System.err.println(entry)
        try {
            logFile.appendText("$entry\n")
        } catch (_: Exception) {
            // Best-effort logging — don't crash if log write fails
        }
    }

    // -------------------------------------------------------------------
    // Availability
    // -------------------------------------------------------------------

    fun isAvailable(): Boolean {
        if (sessionDisabled) return false
        return try {
            getBinaryPath()
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Check if the launcher has been disabled for this session due to a
     * previous crash. The UI can check this to skip showing the player.
     */
    fun isSessionDisabled(): Boolean = sessionDisabled

    fun getBinaryPath(): String {
        cachedBinaryPath?.let { return it }

        val isWindows = System.getProperty("os.name").lowercase().contains("win")
        val binaryName = if (isWindows) BINARY_NAME_WIN else BINARY_NAME_UNIX

        // 1. Same directory as app JAR (bundled via appResourcesRootDir)
        val appDir = System.getProperty("compose.application.resources.dir")
            ?: File(NuvioPlayerLauncher::class.java.protectionDomain.codeSource.location.toURI())
                .parentFile?.absolutePath
        if (appDir != null) {
            val bundled = File(appDir, binaryName)
            if (bundled.exists()) {
                bundled.absolutePath.also { cachedBinaryPath = it }; return cachedBinaryPath!!
            }
        }

        // 2. Dev mode: project-relative build output
        val devPaths = listOf(
            "../nuvio-player/src-tauri/target/release",
            "../nuvio-player/src-tauri/target/debug",
        )
        for (dir in devPaths) {
            val devBinary = File(dir, binaryName)
            if (devBinary.exists()) {
                devBinary.absolutePath.also { cachedBinaryPath = it }; return cachedBinaryPath!!
            }
        }

        // 3. PATH lookup
        val pathEnv = System.getenv("PATH") ?: ""
        for (pathDir in pathEnv.split(File.pathSeparator)) {
            val candidate = File(pathDir, binaryName)
            if (candidate.exists()) {
                candidate.absolutePath.also { cachedBinaryPath = it }; return cachedBinaryPath!!
            }
        }

        error("nuvio-player binary not found. Build it from nuvio-player/ and place next to the app.")
    }

    // -------------------------------------------------------------------
    // Launch (raw)
    // -------------------------------------------------------------------

    /**
     * Start the player process. Does NOT wait for it — returns the [Process]
     * handle immediately.
     *
     * Prefer [launchWithFallback] which adds timeout detection and crash
     * gating.
     */
    fun launch(config: NuvioPlayerConfig): Process {
        val binary = getBinaryPath()
        val args = mutableListOf(
            binary,
            "--url", config.url,
            "--title", config.title,
            "--user-agent", config.userAgent,
            "--referer", config.referer,
            "--start-time", config.startTime.toString(),
            "--hwdec", config.hwdec,
            "--volume", config.volume.toString(),
        )
        if (config.subFile != null) {
            args.add("--sub-file")
            args.add(config.subFile)
        }

        return ProcessBuilder(args)
            .inheritIO()
            .start()
    }

    // -------------------------------------------------------------------
    // Launch with fallback (recommended)
    // -------------------------------------------------------------------

    /**
     * Launch the player with crash detection and fallback.
     *
     * - If the binary is unavailable or session-disabled → calls [onFallback]
     *   and returns `false`.
     * - If the process exits within [LAUNCH_TIMEOUT_SECONDS] with code `1` →
     *   session-disables, calls [onFallback], returns `false`.
     * - If the process stays alive past the timeout → returns `true` (success).
     *
     * @param onFallback invoked with a human-readable reason when fallback
     *                   to mediamp is needed. The UI can show a toast.
     * @return `true` if the player launched successfully, `false` if fallback
     *         should be used.
     */
    fun launchWithFallback(
        config: NuvioPlayerConfig,
        onFallback: (reason: String) -> Unit,
    ): Boolean {
        if (sessionDisabled) {
            log("SESSION BLOCKED: launch skipped (disabled from previous crash)")
            onFallback("External player disabled for this session (previous crash). Using built-in player.")
            return false
        }

        log("Launch attempt: url=${config.url} title=${config.title}")

        try {
            val binary = getBinaryPath()
            log("Binary path: $binary")

            val process = launch(config)
            log("Process started: PID=${process.pid()}")

            // Wait up to N seconds — if the process exits during this window
            // it means it crashed or finished before playback could start.
            val exited = process.waitFor(LAUNCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)

            if (exited) {
                val code = process.exitValue()
                log("Process exited within ${LAUNCH_TIMEOUT_SECONDS}s with code $code")

                if (code == 1) {
                    sessionDisabled = true
                    log("SESSION DISABLED: player crashed on startup (exit code 1)")
                    onFallback("External player crashed on startup. Using built-in player for this session.")
                    return false
                }

                // Code 0 or 2: player finished or user closed quickly.
                // Not a crash, but playback didn't happen. Still count as "launched".
                log("Process exited quickly (code=$code) — not a crash")
                return true
            }

            // Process is still alive — launch succeeded.
            log("Player started successfully (PID=${process.pid()})")

            // Monitor exit in background for logging.
            Thread.ofVirtual().start {
                try {
                    val exitCode = process.waitFor()
                    log("Player exited with code $exitCode")
                } catch (_: InterruptedException) {
                    log("Player monitor interrupted")
                }
            }

            return true

        } catch (e: Exception) {
            log("Launch failed: ${e.message}")
            onFallback("External player error: ${e.message}. Using built-in player.")
            return false
        }
    }
}
