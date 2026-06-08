package com.nuvio.app.features.player.desktop.mpv

import com.nuvio.app.desktop.DesktopRuntimeLog
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.win32.StdCallLibrary
import java.io.File

internal data class MpvRuntimeBootstrapResult(
    val success: Boolean,
    val diagnostics: String,
    val error: Throwable? = null,
)

internal object MpvRuntimeBootstrap {
    private const val LOAD_LIBRARY_SEARCH_DEFAULT_DIRS = 0x00001000
    private const val LOAD_LIBRARY_SEARCH_USER_DIRS = 0x00000400

    @Volatile private var bootstrappedDirectory: String? = null

    @Synchronized
    fun apply(runtime: MpvRuntimeResolution): MpvRuntimeBootstrapResult {
        val directory = runtime.directory
        if (directory == null || !runtime.available) {
            return MpvRuntimeBootstrapResult(
                success = false,
                diagnostics = "MPV runtime directory unresolved. ${runtime.diagnostics}",
            )
        }
        val normalized = directory.absoluteFile.safePath()
        if (bootstrappedDirectory == normalized) {
            return MpvRuntimeBootstrapResult(success = true, diagnostics = "already bootstrapped dir=$normalized")
        }

        prependJavaLibraryPath(directory)
        if (isOsWindows()) {
            val kernel32 = runCatching { Native.load("kernel32", Kernel32::class.java) }
                .onFailure { DesktopRuntimeLog.error("MPV runtime bootstrap cannot load kernel32", it) }
                .getOrNull()
            if (kernel32 != null) {
                val flags = LOAD_LIBRARY_SEARCH_DEFAULT_DIRS or LOAD_LIBRARY_SEARCH_USER_DIRS
                runCatching { kernel32.SetDefaultDllDirectories(flags) }
                    .onFailure { DesktopRuntimeLog.error("MPV runtime bootstrap SetDefaultDllDirectories failed", it) }
                runCatching { kernel32.AddDllDirectory(WString(directory.absolutePath)) }
                    .onFailure { DesktopRuntimeLog.error("MPV runtime bootstrap AddDllDirectory failed dir=$normalized", it) }
                runCatching { kernel32.SetDllDirectoryW(WString(directory.absolutePath)) }
                    .onFailure { DesktopRuntimeLog.error("MPV runtime bootstrap SetDllDirectoryW failed dir=$normalized", it) }
            }
        }

        // Pre-load transitive native deps so the OS linker can resolve them
        // when System.load() loads libmediampv.so. Without this, the JVM's
        // native loader won't find sibling .so files even with java.library.path set.
        if (isOsLinux()) {
            preloadTransitiveDeps(directory)
        }

        val libraryFile = directory.resolve(runtimeLibraryName())
        return runCatching {
            System.load(libraryFile.absolutePath)
        }.fold(
            onSuccess = {
                bootstrappedDirectory = normalized
                DesktopRuntimeLog.info("MPV runtime bootstrap loaded=${libraryFile.safePath()}")
                MpvRuntimeBootstrapResult(success = true, diagnostics = "loaded=${libraryFile.safePath()}")
            },
            onFailure = { throwable ->
                if (throwable.message?.contains("already loaded", ignoreCase = true) == true) {
                    bootstrappedDirectory = normalized
                    MpvRuntimeBootstrapResult(success = true, diagnostics = "already loaded=${libraryFile.safePath()}")
                } else {
                    MpvRuntimeBootstrapResult(
                        success = false,
                        diagnostics = "System.load failed=${libraryFile.safePath()} runtime=${runtime.diagnostics}",
                        error = throwable,
                    )
                }
            },
        )
    }

    /**
     * Pre-load the shared libraries that libmediampv.so depends on.
     * Order matters: load leaf deps first (avutil), then libs that depend on them.
     */
    private fun preloadTransitiveDeps(directory: File) {
        // Load order: leaves first, then dependents
        val depNames = listOf(
            "libavutil.so",
            "libswresample.so",
            "libavcodec.so",
            "libswscale.so",
            "libavformat.so",
            "libavfilter.so",
            "libplacebo.so",
            "libmpv.so",
        )
        for (name in depNames) {
            val lib = directory.resolve(name)
            if (lib.isFile) {
                runCatching { System.load(lib.absolutePath) }
                    .onSuccess { DesktopRuntimeLog.info("MPV preload OK: ${lib.name}") }
                    .onFailure { e ->
                        // Not fatal — the RPATH or LD_LIBRARY_PATH may still resolve it
                        DesktopRuntimeLog.warn("MPV preload skip: ${lib.name} (${e.message?.take(120)})")
                    }
            }
        }
    }

    private fun prependJavaLibraryPath(directory: File) {
        val current = System.getProperty("java.library.path").orEmpty()
        val path = directory.absolutePath
        val entries = current.split(File.pathSeparatorChar).filter { it.isNotBlank() }
        if (entries.any { File(it).absolutePath.equals(path, ignoreCase = true) }) return
        System.setProperty("java.library.path", (listOf(path) + entries).joinToString(File.pathSeparator))
    }

    private interface Kernel32 : StdCallLibrary, Library {
        fun SetDefaultDllDirectories(directoryFlags: Int): Boolean
        fun AddDllDirectory(newDirectory: WString): Pointer?
        fun SetDllDirectoryW(pathName: WString): Boolean
    }
}
