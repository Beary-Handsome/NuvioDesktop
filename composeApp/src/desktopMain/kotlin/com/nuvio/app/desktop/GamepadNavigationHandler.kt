package com.nuvio.app.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import com.studiohartman.jamepad.ControllerManager
import com.studiohartman.jamepad.ControllerState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.CONFLATED
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.awt.EventQueue
import java.awt.event.KeyEvent

@Composable
fun GamepadNavigationHandler(
    window: ComposeWindow?,
    gamepadClickAction: MutableState<(() -> Unit)?>,
) {
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val actions = remember { Channel<GamepadAction>(CONFLATED) }
    val manager = remember { ControllerManager() }

    // Consumer on Main thread — safe for Compose API calls
    LaunchedEffect(Unit) {
        for (action in actions) {
            when (action) {
                is GamepadAction.Move -> {
                    if (!focusManager.moveFocus(action.direction)) {
                        focusManager.moveFocus(FocusDirection.Next)
                    }
                }
                GamepadAction.Activate -> {
                    gamepadClickAction.value?.invoke()
                }
                GamepadAction.Back -> {
                    val w = window ?: continue
                    EventQueue.invokeLater {
                        try {
                            w.dispatchEvent(
                                KeyEvent(w, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED)
                            )
                            w.dispatchEvent(
                                KeyEvent(w, KeyEvent.KEY_RELEASED, System.currentTimeMillis(), 0, KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED)
                            )
                        } catch (_: Exception) {}
                    }
                }
            }
        }
    }

    // Producer on IO thread — polls hardware
    DisposableEffect(Unit) {
        var initialized = false
        try {
            manager.initSDLGamepad()
            initialized = true
        } catch (_: Exception) {}

        val job = if (initialized) {
            scope.launch(Dispatchers.IO) {
                var prev: ControllerState? = null
                var lastDpadDir: Long = 0L
                var lastAxisDir: Long = 0L
                val deadZone = 0.4f
                val repeatInterval = 200L

                while (isActive) {
                    if (GamepadController.playerActive.get()) {
                        delay(100L)
                        continue
                    }
                    if (window != null && !window.isFocused) {
                        delay(200L)
                        continue
                    }

                    try {
                        val state = manager.getState(0)
                        if (!state.isConnected) {
                            prev = null
                            delay(200L)
                            continue
                        }

                        val previous = prev
                        prev = state

                        if (previous != null) {
                            if (!previous.a && state.a)          actions.trySend(GamepadAction.Activate)
                            if (!previous.start && state.start)  actions.trySend(GamepadAction.Activate)
                            if (!previous.b && state.b)          actions.trySend(GamepadAction.Back)
                        }

                        val now = System.currentTimeMillis()
                        val dpadDir = resolveDpadDirection(state)
                        if (dpadDir != null && now - lastDpadDir >= repeatInterval) {
                            lastDpadDir = now
                            actions.trySend(GamepadAction.Move(dpadDir))
                        }

                        val axisDir = resolveAxisDirection(state, deadZone)
                        if (axisDir != null && now - lastAxisDir >= repeatInterval) {
                            lastAxisDir = now
                            actions.trySend(GamepadAction.Move(axisDir))
                        }
                    } catch (_: Exception) {}
                    delay(50L)
                }
            }
        } else null

        onDispose {
            job?.cancel()
            if (initialized) {
                try { manager.quitSDLGamepad() } catch (_: Exception) {}
            }
        }
    }
}

private fun resolveDpadDirection(state: ControllerState): FocusDirection? = when {
    state.dpadRight -> FocusDirection.Right
    state.dpadDown  -> FocusDirection.Down
    state.dpadLeft  -> FocusDirection.Left
    state.dpadUp    -> FocusDirection.Up
    else -> null
}

private fun resolveAxisDirection(state: ControllerState, deadZone: Float): FocusDirection? = when {
    state.leftStickX > deadZone  -> FocusDirection.Right
    state.leftStickX < -deadZone -> FocusDirection.Left
    state.leftStickY < -deadZone -> FocusDirection.Up
    state.leftStickY > deadZone  -> FocusDirection.Down
    else -> null
}
