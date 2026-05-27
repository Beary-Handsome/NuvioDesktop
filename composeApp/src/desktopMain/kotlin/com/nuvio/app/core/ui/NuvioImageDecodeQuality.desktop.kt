package com.nuvio.app.core.ui

internal actual fun nuvioQualityDecodeDimensionPx(displayDimensionPx: Int): Int =
    displayDimensionPx.scaleQualityDimension(multiplier = 1.5f, maxPx = 3840)
