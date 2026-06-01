package com.nuvio.app.core.ui

import androidx.compose.ui.graphics.FilterQuality
import java.util.regex.Pattern

private const val DesktopQualityDecodeMultiplier = 2.0f
private const val DesktopQualityDecodeBucketPx = 64
private const val DesktopQualityDecodeMaxDimensionPx = 2560

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
