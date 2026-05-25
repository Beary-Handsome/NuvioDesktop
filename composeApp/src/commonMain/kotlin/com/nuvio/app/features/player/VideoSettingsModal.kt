package com.nuvio.app.features.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal expect fun VideoSettingsModal(
    visible: Boolean,
    settings: PlayerSettingsUiState,
    onSettingsChanged: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
)
