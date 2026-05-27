package com.nuvio.app.core.ui

internal actual fun nuvioQualityDecodeDimensionPx(displayDimensionPx: Int): Int =
    displayDimensionPx.roundUpToQualityBucket(50).coerceAtMost(1440)
