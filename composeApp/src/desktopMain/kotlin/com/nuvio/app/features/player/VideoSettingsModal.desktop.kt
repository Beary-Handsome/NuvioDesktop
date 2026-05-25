package com.nuvio.app.features.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.nuvio.app.features.player.desktop.mpv.DesktopVideoSettingsModal

@Composable
internal actual fun VideoSettingsModal(
    visible: Boolean,
    settings: PlayerSettingsUiState,
    onSettingsChanged: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier,
) {
    DesktopVideoSettingsModal(
        visible = visible,
        settings = settings,
        onSettingsChanged = onSettingsChanged,
        onDismiss = onDismiss,
        modifier = modifier,
    )
}
