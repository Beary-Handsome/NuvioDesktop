package com.nuvio.app.core.ui

private const val DesktopQualityDecodeMultiplier = 2.0f
private const val DesktopQualityDecodeBucketPx = 64
private const val DesktopQualityDecodeMaxDimensionPx = 2560

/**
 * Windows Desktop runs on the Skiko OpenGL backend (libmpv shares its GL
 * context). Its resampler aliases visibly on draw-time downscales, so on
 * Windows the decode dimension must equal the measured draw size and never
 * over-decode. macOS/Linux Desktop keep the existing 2x oversample path.
 */
private val isWindowsDesktop: Boolean by lazy {
    System.getProperty("os.name")?.contains("Windows", ignoreCase = true) == true
}

internal actual val NuvioImageFilterQuality: FilterQuality = FilterQuality.High

internal actual fun nuvioQualityDecodeDimensionPx(displayDimensionPx: Int): Int {
    val displayPx = displayDimensionPx.coerceAtLeast(1)
    if (isWindowsDesktop && WindowsImageRenderingPreference.nativeWicEnabled) return displayPx
    return displayPx
        .scaleQualityDimension(
            multiplier = DesktopQualityDecodeMultiplier,
            maxPx = DesktopQualityDecodeMaxDimensionPx,
        )
        .roundUpToQualityBucket(DesktopQualityDecodeBucketPx)
}

internal actual fun String.upgradeTmdbImageQuality(): String {
    if (!contains("image.tmdb.org/t/p/", ignoreCase = true)) return this
    return replace(TmdbImageSizeSegment, "/original/")
}
