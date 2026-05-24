package com.nuvio.app.features.player.desktop.mpv

import java.io.File

internal fun runtimeLibraryName(): String =
    if (isOsWindows()) "mediampv.dll" else "libmediampv.so"

internal fun isOsWindows(): Boolean =
    System.getProperty("os.name")?.contains("Windows", ignoreCase = true) == true

internal fun isOsLinux(): Boolean =
    System.getProperty("os.name")?.lowercase()?.contains("nux") == true

internal data class MpvRuntimeResolution(
    val directory: File?,
    val checkedDirectories: List<String>,
    val diagnostics: String,
) {
    val available: Boolean get() = directory?.resolve(runtimeLibraryName())?.isFile == true
}

internal object MpvRuntimeLocator {
    fun resolve(): MpvRuntimeResolution {
        val candidates = linkedMapOf<String, File>()
        fun add(label: String, file: File?) {
            if (file != null) candidates.putIfAbsent(label, file)
        }

        val resourcesDir = System.getProperty("compose.application.resources.dir")
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)
        val appDir = resourcesDir?.parentFile
        add("appDir/native", appDir?.resolve("native"))
        add("resourcesDir/native", resourcesDir?.resolve("native"))

        add("env:NUVIO_MEDIAMP_RUNTIME_DIR", System.getenv("NUVIO_MEDIAMP_RUNTIME_DIR")?.toFileOrNull())
        System.getenv("NUVIO_MPV_DIR")?.toFileOrNull()?.let { dir ->
            add("env:NUVIO_MPV_DIR", dir)
            add("env:NUVIO_MPV_DIR/bin", dir.resolve("bin"))
        }
        add("property:nuvio.mediamp.runtime.dir", System.getProperty("nuvio.mediamp.runtime.dir")?.toFileOrNull())
        System.getProperty("nuvio.mpv.dir")?.toFileOrNull()?.let { dir ->
            add("property:nuvio.mpv.dir", dir)
            add("property:nuvio.mpv.dir/bin", dir.resolve("bin"))
        }

        if (isOsLinux()) {
            add("linux:/usr/lib/x86_64-linux-gnu/mediamp", File("/usr/lib/x86_64-linux-gnu/mediamp"))
            add("linux:/usr/local/lib/mediamp", File("/usr/local/lib/mediamp"))
            add("linux:/usr/lib/mediamp", File("/usr/lib/mediamp"))
            add("env:LD_LIBRARY_PATH", System.getenv("LD_LIBRARY_PATH")?.toFileOrNull())
        }

        javaLibraryPathEntries().forEach { entry ->
            val dir = File(entry)
            add("java.library.path:${dir.safePath()}", dir)
            add("java.library.path/native:${dir.safePath()}", dir.resolve("native"))
        }

        pathEntries().forEach { entry ->
            add("PATH:${entry.safePath()}", entry)
        }

        if (devLookupEnabled()) {
            System.getProperty("user.dir")?.takeIf { it.isNotBlank() }?.let { userDir ->
                val base = File(userDir)
                add("dev:app/native", base.resolve("app/native"))
                add("dev:native", base.resolve("native"))
                add("dev:mediamp/build-ci", base.resolve("mediamp/mediamp-mpv/build-ci"))
                if (isOsWindows()) {
                    add("dev:mediamp/build-ci/Release", base.resolve("mediamp/mediamp-mpv/build-ci/Release"))
                    add("dev:mediamp/libmpv", base.resolve("mediamp/mediamp-mpv/libmpv/lib/windows/x86_64"))
                }
                if (isOsLinux()) {
                    add("dev:mediamp/build-ci", base.resolve("mediamp/mediamp-mpv/build-ci"))
                }
            }
        }

        val libName = runtimeLibraryName()
        val checked = candidates.map { (label, dir) ->
            "$label=${dir.safePath()} exists=${dir.isDirectory} $libName=${dir.resolve(libName).isFile}"
        }
        val selected = candidates.values.firstOrNull { it.resolve(libName).isFile }
        return MpvRuntimeResolution(
            directory = selected,
            checkedDirectories = checked,
            diagnostics = "selected=${selected?.safePath() ?: "none"} checked=${checked.joinToString(" | ")}",
        )
    }

    private fun devLookupEnabled(): Boolean =
        System.getenv("NUVIO_DEV_PLAYER_LOOKUP").equals("true", ignoreCase = true) ||
            System.getProperty("nuvio.dev.player.lookup").equals("true", ignoreCase = true)

    private fun javaLibraryPathEntries(): List<String> =
        System.getProperty("java.library.path")
            ?.split(File.pathSeparatorChar)
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            .orEmpty()

    private fun pathEntries(): List<File> =
        System.getenv("PATH")
            ?.split(File.pathSeparatorChar)
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            ?.map(::File)
            .orEmpty()

    private fun String.toFileOrNull(): File? =
        takeIf { it.isNotBlank() }?.let(::File)
}

internal fun File.safePath(): String = absolutePath.replace("\\", "/")
