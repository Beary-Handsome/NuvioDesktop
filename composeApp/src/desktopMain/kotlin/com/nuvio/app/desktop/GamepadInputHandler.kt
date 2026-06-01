package com.nuvio.app.desktop

import com.studiohartman.jamepad.ControllerManager
import com.studiohartman.jamepad.ControllerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

fun CoroutineScope.launchGamepadPlayerHandler(
    manager: ControllerManager,
    onActivate: () -> Unit,
    onBack: () -> Unit,
): Job {
    GamepadController.playerActive.set(true)
    return launch(Dispatchers.IO) {
        try {
            var prev: ControllerState? = null
            while (isActive) {
                try {
                    val state = manager.getState(0)
                    if (!state.isConnected) {
                        prev = null
                        delay(500L)
                        continue
                    }
                    val previous = prev
                    prev = state
                    if (previous != null) {
                        if (!previous.a && state.a) onActivate()
                        if (!previous.b && state.b) onBack()
                    }
                } catch (_: Exception) {}
                delay(100L)
            }
        } finally {
            GamepadController.playerActive.set(false)
        }
    }
}
