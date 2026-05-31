package com.nuvio.app.core.ui

internal actual fun resolvedPosterWidthDp(preset: PosterCardWidthPreset): Int =
    when (preset) {
        PosterCardWidthPreset.Compact -> 160
        PosterCardWidthPreset.Dense -> 180
        PosterCardWidthPreset.Standard -> 200
        PosterCardWidthPreset.Balanced -> 220
        PosterCardWidthPreset.Comfort -> 240
        PosterCardWidthPreset.Large -> 260
    }
