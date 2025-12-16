package com.example.lattice.ui.screens

import android.Manifest
import android.content.ContentResolver
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.ui.platform.LocalContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.lattice.domain.model.Attachment
import com.example.lattice.domain.model.AttachmentType
import com.example.lattice.domain.model.Priority
import com.example.lattice.domain.model.TimePoint
import com.example.lattice.domain.time.TimeZoneData
import com.example.lattice.domain.time.buildTimePoint
import com.example.lattice.viewModel.EditorViewModel
import com.example.lattice.ui.theme.PriorityHigh
import com.example.lattice.ui.theme.PriorityMedium
import com.example.lattice.ui.theme.PriorityLow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale as JavaLocale
import java.util.UUID
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import com.example.lattice.ui.components.AttachmentBottomSheet
import com.example.lattice.ui.components.ScheduleBottomSheet
import com.example.lattice.ui.components.UploadOptionsBottomSheet
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    onBack: () -> Unit,
    onSave: (String, String, Priority, TimePoint?, List<com.example.lattice.domain.model.Attachment>) -> Unit,
    initialTitle: String = "",
    initialDescription: String = "",
    initialPriority: Priority = Priority.None,
    initialTime: TimePoint? = null,
    initialAttachments: List<com.example.lattice.domain.model.Attachment> = emptyList(),
    primaryLabel: String = "Save",
    parentId: String? = null,
    fromBottomNav: Boolean = false
) {
    // Basic UI state: task form fields
    var title by rememberSaveable(initialTitle) { mutableStateOf(initialTitle) }
    var description by rememberSaveable(initialDescription) { mutableStateOf(initialDescription) }
    var selectedPriority by rememberSaveable(initialPriority.name) { mutableStateOf(initialPriority) }

    // Schedule/time state
    var dateText by rememberSaveable(initialTime?.date?.toString()) {
        mutableStateOf(initialTime?.date?.toString() ?: "")
    }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    var timeText by rememberSaveable(initialTime?.formattedTime()) {
        mutableStateOf(initialTime?.formattedTime() ?: "")
    }
    var zoneIdText by rememberSaveable(
        initialTime?.zoneId?.id ?: ZoneId.systemDefault().id
    ) { mutableStateOf(initialTime?.zoneId?.id ?: ZoneId.systemDefault().id) }
    var schedulePickerOpen by remember { mutableStateOf(false) }
    
    // Attachment state
    var attachments by rememberSaveable(initialAttachments) { 
        mutableStateOf(initialAttachments.toMutableList()) 
    }
    var uploadOptionsOpen by remember { mutableStateOf(false) }
    var attachmentPreviewOpen by remember { mutableStateOf(false) }
    var uploadSuccessMessage by remember { mutableStateOf<String?>(null) }
    var showUploadSuccess by remember { mutableStateOf(false) }

    // UI configuration
    val isEditing = initialTitle.isNotBlank()
    val topBarTitle = when {
        isEditing -> "Edit Task"
        parentId != null -> "New Subtask"
        else -> "New Task"
    }

    // Context and coroutine scope
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Speech-to-text state
    val editorViewModel: EditorViewModel = viewModel()
    val sttUiState by editorViewModel.uiState.collectAsState()
    val isRecording = sttUiState.isRecording
    val isTranscribing = sttUiState.isTranscribing
    val speechError = sttUiState.error
    
    // Attachment helper functions
    fun copyFileToInternalStorage(uri: Uri, fileName: String): File? {
        return try {
            val contentResolver: ContentResolver = context.contentResolver
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            val appDir = File(context.filesDir, "attachments")
            if (!appDir.exists()) {
                appDir.mkdirs()
            }
            val outputFile = File(appDir, fileName)
            inputStream?.use { input ->
                FileOutputStream(outputFile).use { output ->
                    input.copyTo(output)
                }
            }
            outputFile
        } catch (e: Exception) {
            null
        }
    }
    
    fun getFileInfoFromUri(uri: Uri): Pair<String, Long>? {
        return try {
            val contentResolver: ContentResolver = context.contentResolver
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    val fileName = if (nameIndex >= 0) it.getString(nameIndex) else "unknown"
                    val fileSize = if (sizeIndex >= 0) it.getLong(sizeIndex) else 0L
                    Pair(fileName, fileSize)
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    // Camera and file picker launchers
    val cameraPhotoFile = remember {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", JavaLocale.getDefault()).format(Date())
        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        File.createTempFile(
            "IMG_${timeStamp}_",
            ".jpg",
            storageDir
        )
    }
    
    val cameraUri = remember {
        androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            cameraPhotoFile
        )
    }
    
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            val fileName = cameraPhotoFile.name
            val fileSize = cameraPhotoFile.length()
            val attachment = Attachment(
                UUID.randomUUID().toString(),
                cameraPhotoFile.absolutePath,
                fileName,
                AttachmentType.IMAGE,
                null,
                fileSize
            )
            attachments.add(attachment)
            uploadSuccessMessage = "Upload successful: $fileName"
            showUploadSuccess = true
            scope.launch {
                delay(3000)
                showUploadSuccess = false
            }
        }
    }
    
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permission granted, launch camera
            cameraLauncher.launch(cameraUri)
        } else {
            // Permission denied, show message or handle gracefully
            uploadSuccessMessage = "Camera permission is required to take photos"
            showUploadSuccess = true
            scope.launch {
                delay(3000)
                showUploadSuccess = false
            }
        }
    }
    
    fun launchCamera() {
        when {
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> {
                // Permission already granted, launch camera directly
                cameraLauncher.launch(cameraUri)
            }
            else -> {
                // Request permission
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }
    
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            val fileInfo = getFileInfoFromUri(it)
            val fileName = fileInfo?.first ?: "image_${System.currentTimeMillis()}.jpg"
            val fileSize = fileInfo?.second
            val copiedFile = copyFileToInternalStorage(it, fileName)
            copiedFile?.let { file ->
                val attachment = Attachment(
                    UUID.randomUUID().toString(),
                    file.absolutePath,
                    fileName,
                    AttachmentType.IMAGE,
                    null,
                    fileSize
                )
                attachments.add(attachment)
                uploadSuccessMessage = "Upload successful: $fileName"
                showUploadSuccess = true
                scope.launch {
                    kotlinx.coroutines.delay(3000)
                    showUploadSuccess = false
                }
            }
        }
    }
    
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val fileInfo = getFileInfoFromUri(it)
            val originalFileName = fileInfo?.first ?: "file_${System.currentTimeMillis()}"
            val fileSize = fileInfo?.second
            val extension = originalFileName.substringAfterLast('.', "").lowercase()
            val fileType = when (extension) {
                "pdf" -> AttachmentType.PDF
                "doc", "docx" -> AttachmentType.DOC
                else -> AttachmentType.OTHER
            }
            val copiedFile = copyFileToInternalStorage(it, originalFileName)
            copiedFile?.let { file ->
                val attachment = Attachment(
                    UUID.randomUUID().toString(),
                    file.absolutePath,
                    originalFileName,
                    fileType,
                    null,
                    fileSize
                )
                attachments.add(attachment)
                uploadSuccessMessage = "Upload successful: $originalFileName"
                showUploadSuccess = true
                scope.launch {
                    kotlinx.coroutines.delay(3000)
                    showUploadSuccess = false
                }
            }
        }
    }
    
    // Speech-to-text permission launchers
    val titlePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            editorViewModel.clearError()
            editorViewModel.startRecording(
                onResult = { text ->
                    // The title is single-line: replace directly, or append with a space if content already exists.
                    title = if (title.isBlank()) {
                        text
                    } else {
                        "$title $text"
                    }
                }
            )
        } else {
            editorViewModel.setError("Microphone permission denied.")
        }
    }
    
    val descriptionPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            editorViewModel.clearError()
            editorViewModel.startRecording(
                onResult = { text ->
                    description = listOf(description, text)
                        .filter { it.isNotBlank() }
                        .joinToString("\n")
                }
            )
        } else {
            editorViewModel.setError("Microphone permission denied.")
        }
    }

    Scaffold(
        topBar = {
            if (fromBottomNav) {
                TopAppBar(
                    title = { Text(topBarTitle) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            } else {
                TopAppBar(
                    title = { Text(topBarTitle) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    ),
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Title + Speech-to-Text
            item {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    trailingIcon = {
                    IconButton(
                        enabled = !isRecording && !isTranscribing,
                        onClick = {
                            editorViewModel.clearError()
                            val granted =
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                            if (granted) {
                                editorViewModel.startRecording(
                                    onResult = { text ->
                                        // The title is single-line: replace directly, or append with a space if content already exists.
                                        title = if (title.isBlank()) {
                                            text
                                        } else {
                                            "$title $text"
                                        }
                                    }
                                )
                            } else {
                                titlePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Mic,
                            contentDescription = "Record voice for title"
                        )
                    }
                }
            )
            }

            // Description + Speech-to-Text
            item {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (title.isNotBlank()) {
                                val timePoint = buildTimePoint(
                                    dateText,
                                    timeText,
                                    zoneIdText,
                                    timeFormatter
                                )
                                // Only trigger save event.
                                onSave(title, description, selectedPriority, timePoint, attachments)
                            }
                        }
                    ),
                    trailingIcon = {
                        IconButton(
                            enabled = !isRecording && !isTranscribing,
                            onClick = {
                                editorViewModel.clearError()
                                val granted =
                                    ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                if (granted) {
                                    editorViewModel.startRecording(
                                        onResult = { text ->
                                            description = listOf(description, text)
                                                .filter { it.isNotBlank() }
                                                .joinToString("\n")
                                        }
                                    )
                                } else {
                                    descriptionPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Mic,
                                contentDescription = "Record voice"
                            )
                        }
                    }
                )
            }

            if (isRecording) {
                item {
                    Text(
                        "Listening…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else if (isTranscribing) {
                item {
                    Text(
                        "Transcribing…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (speechError != null) {
                item {
                    Text(
                        text = speechError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // Priority
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Priority", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Priority.values().forEach { priority ->
                        val isSelected = selectedPriority == priority
                        val priorityColorValue = when (priority) {
                            Priority.High -> PriorityHigh
                            Priority.Medium -> PriorityMedium
                            Priority.Low -> PriorityLow
                            Priority.None -> MaterialTheme.colorScheme.outline
                        }
                        val interactionSource = remember { MutableInteractionSource() }
                        val indication = LocalIndication.current
                        Surface(
                            modifier = Modifier
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = indication
                                ) { selectedPriority = priority }
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) priorityColorValue else MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(8.dp)
                                ),
                            shape = RoundedCornerShape(8.dp),
                            color = priorityColorValue.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = priority.name,
                                color = priorityColorValue,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
            }

            // Scheduled time
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Scheduled Time", style = MaterialTheme.typography.titleMedium)

                    val selectedZone = runCatching { ZoneId.of(zoneIdText) }
                        .getOrDefault(ZoneId.systemDefault())
                    
                    val cityName = TimeZoneData.findCityByZoneId(selectedZone) ?: selectedZone.id
                    val now = ZonedDateTime.now(selectedZone)
                    val offset = now.offset
                    val hours = offset.totalSeconds / 3600
                    val minutes = kotlin.math.abs(offset.totalSeconds % 3600) / 60
                    val offsetStr = when {
                        minutes == 0 -> "UTC${if (hours >= 0) "+" else ""}$hours"
                        else -> "UTC${if (hours >= 0) "+" else ""}$hours:${minutes.toString().padStart(2, '0')}"
                    }
                    val zoneDisplay = "$cityName, $offsetStr"

                    val combinedText = when {
                        dateText.isBlank() -> "Not set"
                        timeText.isBlank() -> "$dateText ($zoneDisplay)"
                        else -> "$dateText $timeText ($zoneDisplay)"
                    }

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(
                            text = combinedText,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { schedulePickerOpen = true }) {
                            Text("Pick Schedule")
                        }
                        TextButton(
                            onClick = {
                                dateText = ""
                                timeText = ""
                                zoneIdText = ZoneId.systemDefault().id
                            }
                        ) {
                            Text("Clear")
                        }
                    }
                }
            }

            // Attachments
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Attachments", style = MaterialTheme.typography.titleMedium)
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { uploadOptionsOpen = true }) {
                            Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Upload")
                        }
                        
                        if (attachments.isNotEmpty()) {
                            TextButton(onClick = { attachmentPreviewOpen = true }) {
                                Text("Preview Attachment")
                            }
                        }
                    }
                    
                    // Upload success message
                    if (showUploadSuccess && uploadSuccessMessage != null) {
                        Text(
                            text = uploadSuccessMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            // Bottom buttons
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel", maxLines = 1, softWrap = false)
                    }
                    Button(
                        onClick = {
                            if (title.isNotBlank()) {
                                val timePoint = buildTimePoint(
                                    dateText,
                                    timeText,
                                    zoneIdText,
                                    timeFormatter
                                )
                                // Only trigger save event.
                                onSave(title, description, selectedPriority, timePoint, attachments)
                            }
                        },
                        enabled = title.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(primaryLabel, maxLines = 1, softWrap = false)
                    }
                }
            }

            item {
                Text(
                    "Tip: you can add subtasks under any task from the Tasks screen.",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }

    if (schedulePickerOpen) {
        ScheduleBottomSheet(
            initialDate = dateText.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it) },
            initialTime = timeText.takeIf { it.isNotBlank() }
                ?.let { LocalTime.parse(it, timeFormatter) },
            initialZoneId = runCatching { ZoneId.of(zoneIdText) }
                .getOrElse { ZoneId.systemDefault() },
            onDismiss = { schedulePickerOpen = false },
            onConfirm = { date, time, zoneId ->
                dateText = date.toString()
                timeText = time?.format(timeFormatter) ?: ""
                zoneIdText = zoneId.id
                schedulePickerOpen = false
            }
        )
    }
    
    // Upload options bottom sheet
    if (uploadOptionsOpen) {
        UploadOptionsBottomSheet(
            onDismiss = { uploadOptionsOpen = false },
            onCameraClick = {
                uploadOptionsOpen = false
                launchCamera()
            },
            onAlbumClick = {
                uploadOptionsOpen = false
                imagePickerLauncher.launch(
                    PickVisualMediaRequest(PickVisualMedia.ImageOnly)
                )
            },
            onFileClick = {
                uploadOptionsOpen = false
                filePickerLauncher.launch("*/*")
            }
        )
    }
    
    // Attachment preview bottom sheet
    if (attachmentPreviewOpen) {
        AttachmentBottomSheet(
            attachments = attachments,
            onDismiss = { attachmentPreviewOpen = false },
            onDelete = { attachmentId ->
                attachments.removeAll { it.id == attachmentId }
            }
        )
    }
}


