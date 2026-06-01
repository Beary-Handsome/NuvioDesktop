package com.nuvio.app.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged

val LocalGamepadActivation = staticCompositionLocalOf { mutableStateOf<(() -> Unit)?>(null) }

@Composable
fun Modifier.gamepadActivate(onClick: () -> Unit): Modifier {
    val ref = LocalGamepadActivation.current
    return this.onFocusChanged { focusState ->
        ref.value = if (focusState.isFocused) onClick else null
    }
}
