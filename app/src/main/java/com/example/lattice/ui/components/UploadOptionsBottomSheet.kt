package com.example.lattice.ui.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadOptionsBottomSheet(
    onDismiss: () -> Unit,
    onCameraClick: () -> Unit,
    onAlbumClick: () -> Unit,
    onFileClick: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (!sheetState.isVisible) sheetState.show()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Upload Attachment", style = MaterialTheme.typography.titleLarge)

            HorizontalDivider()

            // Camera option
            ListItem(
                headlineContent = { Text("Camera") },
                leadingContent = { Icon(Icons.Filled.CameraAlt, contentDescription = null) },
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = LocalIndication.current,
                        onClick = {
                            scope.launch {
                                sheetState.hide()
                                onDismiss()
                            }
                            onCameraClick()
                        }
                    ),
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )

            // Album option
            ListItem(
                headlineContent = { Text("Album") },
                leadingContent = { Icon(Icons.Filled.Image, contentDescription = null) },
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = LocalIndication.current,
                        onClick = {
                            scope.launch {
                                sheetState.hide()
                                onDismiss()
                            }
                            onAlbumClick()
                        }
                    ),
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )

            // File option
            ListItem(
                headlineContent = { Text("File") },
                leadingContent = { Icon(Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = null) },
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = LocalIndication.current,
                        onClick = {
                            scope.launch {
                                sheetState.hide()
                                onDismiss()
                            }
                            onFileClick()
                        }
                    ),
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
        }
    }
}
