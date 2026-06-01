package com.nuvio.app.desktop

import androidx.compose.ui.focus.FocusDirection
import java.util.concurrent.atomic.AtomicBoolean

object GamepadController {
    val playerActive = AtomicBoolean(false)
}

sealed interface GamepadAction {
    data object Activate : GamepadAction
    data object Back : GamepadAction
    data class Move(val direction: FocusDirection) : GamepadAction
}
