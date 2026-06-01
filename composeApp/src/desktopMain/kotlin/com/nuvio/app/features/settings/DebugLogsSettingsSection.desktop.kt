package com.nuvio.app.features.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nuvio.app.desktop.DesktopRuntimeLog

@Composable
internal actual fun DebugLogsSettingsSection(isTablet: Boolean) {
    Column {
        SettingsGroupDivider(isTablet = isTablet)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Debug logs enabled: ${DesktopRuntimeLog.debugEnabled}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
