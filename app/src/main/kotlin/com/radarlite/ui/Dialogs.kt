package com.radarlite.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.radarlite.ui.theme.Coral

@Composable
fun DisclaimerDialog(onAccept: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Legal notice") },
        text = {
            Text(
                "Road-alert apps are illegal or restricted in some countries. You are responsible for checking the laws in your region before use."
            )
        },
        confirmButton = { TextButton(onClick = onAccept) { Text("I understand", color = Coral) } },
    )
}

@Composable
fun BackgroundLocationDialog(onOpenSettings: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Background location needed") },
        text = {
            Text(
                "RadarLite accesses location in the background to detect nearby road alerts and show warnings even when the app is closed or not in use. Your location stays on your device and is not shared. Select Location and Allow all the time."
            )
        },
        confirmButton = { TextButton(onClick = onOpenSettings) { Text("Open Settings", color = Coral) } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

@Composable
fun DatabaseMissingDialog(onDownload: () -> Unit, onLater: () -> Unit) {
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text("Download alert database?") },
        text = { Text("RadarLite needs an alert database before it can warn you. Download it now?") },
        confirmButton = { TextButton(onClick = onDownload) { Text("Download", color = Coral) } },
        dismissButton = { TextButton(onClick = onLater) { Text("Not now") } },
    )
}

@Composable
fun DatabaseStaleDialog(onUpdate: () -> Unit, onSkip: () -> Unit) {
    AlertDialog(
        onDismissRequest = onSkip,
        title = { Text("Update alert database?") },
        text = { Text("The alert database has not been checked for 7 days or more. Update it now?") },
        confirmButton = { TextButton(onClick = onUpdate) { Text("Update", color = Coral) } },
        dismissButton = { TextButton(onClick = onSkip) { Text("Skip") } },
    )
}
