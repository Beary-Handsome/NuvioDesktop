/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * Use of this source code is governed by the Apache License version 2 license, which can be found at the following link.
 *
 * https://github.com/open-ani/mediamp/blob/main/LICENSE
 */
@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package org.openani.mediamp.mpv.utils

import org.jetbrains.skia.DirectContext
import org.jetbrains.skiko.SkiaLayer
import org.jetbrains.skiko.context.ContextHandler
import org.jetbrains.skiko.context.OpenGLContextHandler

class OpenGLComponentProvider(private val skiaLayer: SkiaLayer) {
    private val redrawer = skiaLayer.redrawer ?: error("SkiaLayer redrawer is null")
    private val redrawerClass = redrawer::class.java

    private val isLinux: Boolean =
        System.getProperty("os.name")?.lowercase()?.contains("nux") == true

    // On Windows: WindowsOpenGLRedrawer has both "device" (HDC) and "context" (HGLRC)
    // On Linux: LinuxOpenGLRedrawer has "context" (EGLContext) but no "device" field
    private val deviceHandleField = if (!isLinux) {
        redrawerClass.getDeclaredField("device").also { it.isAccessible = true }
    } else null

    private val glContextHandleField = redrawerClass
        .getDeclaredField("context")
        .also { it.isAccessible = true }

    private val contextHandlerHandleField = redrawerClass
        .getDeclaredField("contextHandler")
        .also { it.isAccessible = true }
    private val directContextHandler = ContextHandler::class.java
        .getDeclaredField("context")
        .also { it.isAccessible = true }

    val glDevice: Long get() = deviceHandleField?.getLong(redrawer) ?: 0L
    val glContext: Long get() = glContextHandleField.getLong(redrawer)
    val contextSignature: String get() = "$glDevice:$glContext"
    val contentScale: Float get() = skiaLayer.contentScale
    val currentDpi: Int get() = skiaLayer.currentDPI

    val directContext: DirectContext
        get() = (contextHandlerHandleField.get(redrawer) as OpenGLContextHandler)
            .let { directContextHandler.get(it) as DirectContext }
}
