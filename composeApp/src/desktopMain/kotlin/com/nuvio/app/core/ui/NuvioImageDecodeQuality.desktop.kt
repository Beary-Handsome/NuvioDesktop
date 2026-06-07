package com.nuvio.app.core.ui

import androidx.compose.ui.graphics.FilterQuality

internal actual val NuvioImageFilterQuality: FilterQuality = FilterQuality.Low

internal actual fun nuvioQualityDecodeDimensionPx(displayDimensionPx: Int): Int =
    displayDimensionPx.scaleQualityDimension(multiplier = 1.5f, maxPx = 3840)
