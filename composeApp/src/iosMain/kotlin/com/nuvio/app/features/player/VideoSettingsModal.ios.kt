package com.nuvio.app.features.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal actual fun VideoSettingsModal(
    visible: Boolean,
    settings: PlayerSettingsUiState,
    onSettingsChanged: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier,
) {
    IosVideoSettingsModal(
        visible = visible,
        settings = settings,
        onSettingsChanged = onSettingsChanged,
        onDismiss = onDismiss,
        modifier = modifier,
    )
}
