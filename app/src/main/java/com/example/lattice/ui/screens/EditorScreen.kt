package com.example.lattice.ui.screens

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItemDefaults
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Divider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.lattice.domain.model.Priority
import com.example.lattice.domain.model.TimePoint
import com.example.lattice.domain.time.TimeZoneData
import com.example.lattice.viewModel.EditorViewModel
import com.example.lattice.ui.theme.PriorityHigh
import com.example.lattice.ui.theme.PriorityMedium
import com.example.lattice.ui.theme.PriorityLow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale as JavaLocale
import kotlin.math.abs
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.ListItem
import androidx.compose.ui.text.style.TextOverflow
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
    var title by rememberSaveable(initialTitle) { mutableStateOf(initialTitle) }
    var description by rememberSaveable(initialDescription) { mutableStateOf(initialDescription) }
    var selectedPriority by rememberSaveable(initialPriority.name) { mutableStateOf(initialPriority) }

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

    val isEditing = initialTitle.isNotBlank()
    val topBarTitle = when {
        isEditing -> "Edit Task"
        parentId != null -> "New Subtask"
        else -> "New Task"
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ---- 语音相关：使用 EditorViewModel 管理状态 ----
    val editorViewModel: EditorViewModel = viewModel()
    val sttUiState by editorViewModel.uiState.collectAsState()
    
    // ---- 附件相关：文件处理和存储 ----
    // Helper function to copy file to app's internal storage
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
    
    // Helper function to get file info from URI
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
    
    // Camera photo file
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
    
    // Camera launcher (must be defined before permission launcher)
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            val fileName = cameraPhotoFile.name
            val fileSize = cameraPhotoFile.length()
            val attachment = com.example.lattice.domain.model.Attachment(
                filePath = cameraPhotoFile.absolutePath,
                fileName = fileName,
                fileType = com.example.lattice.domain.model.AttachmentType.IMAGE,
                fileSize = fileSize
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
    
    // Camera permission launcher
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
    
    // Function to launch camera with permission check
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
    
    // Image picker launcher (Album)
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            val fileInfo = getFileInfoFromUri(it)
            val fileName = fileInfo?.first ?: "image_${System.currentTimeMillis()}.jpg"
            val fileSize = fileInfo?.second
            val copiedFile = copyFileToInternalStorage(it, fileName)
            copiedFile?.let { file ->
                val attachment = com.example.lattice.domain.model.Attachment(
                    filePath = file.absolutePath,
                    fileName = fileName,
                    fileType = com.example.lattice.domain.model.AttachmentType.IMAGE,
                    fileSize = fileSize
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
    
    // File picker launcher (for PDF/DOC)
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val fileInfo = getFileInfoFromUri(it)
            val originalFileName = fileInfo?.first ?: "file_${System.currentTimeMillis()}"
            val fileSize = fileInfo?.second
            val extension = originalFileName.substringAfterLast('.', "").lowercase()
            val fileType = when (extension) {
                "pdf" -> com.example.lattice.domain.model.AttachmentType.PDF
                "doc", "docx" -> com.example.lattice.domain.model.AttachmentType.DOC
                else -> com.example.lattice.domain.model.AttachmentType.OTHER
            }
            val copiedFile = copyFileToInternalStorage(it, originalFileName)
            copiedFile?.let { file ->
                val attachment = com.example.lattice.domain.model.Attachment(
                    filePath = file.absolutePath,
                    fileName = originalFileName,
                    fileType = fileType,
                    fileSize = fileSize
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
    
    val isRecording = sttUiState.isRecording
    val isTranscribing = sttUiState.isTranscribing
    val speechError = sttUiState.error

    // 请求 RECORD_AUDIO 权限 - 用于 Title
    val titlePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            editorViewModel.clearError()
            editorViewModel.startRecording(
                onResult = { text ->
                    // Title 是单行，直接替换或追加（如果已有内容则追加空格）
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

    // 请求 RECORD_AUDIO 权限 - 用于 Description
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Title + 语音按钮
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
                                        // Title 是单行，直接替换或追加（如果已有内容则追加空格）
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

            // Description + 语音按钮
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
                            // ✅ 只触发保存事件，由外部决定是否导航返回
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

            if (isRecording) {
                Text(
                    "Listening…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            } else if (isTranscribing) {
                Text(
                    "Transcribing…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (speechError != null) {
                Text(
                    text = speechError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // Priority
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Priority", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Priority.values().forEach { priority ->
                        val isSelected = selectedPriority == priority
                        val priorityColorValue = priorityColor(priority)
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

            // Scheduled time
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

            // Attachments
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

            // Bottom buttons
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
                            // ✅ 同样只负责发出保存事件
                            onSave(title, description, selectedPriority, timePoint, attachments)
                        }
                    },
                    enabled = title.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(primaryLabel, maxLines = 1, softWrap = false)
                }
            }

            Text(
                "Tip: you can add subtasks under any task from the Tasks screen.",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )
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

// ----------------- 以下辅助组件与之前版本一致，只是略微清理 import -----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleBottomSheet(
    initialDate: LocalDate?,
    initialTime: LocalTime?,
    initialZoneId: ZoneId,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, LocalTime?, ZoneId) -> Unit
) {
    val systemZone = ZoneId.systemDefault()
    val initialMillis = remember(initialDate) {
        initialDate?.atStartOfDay(systemZone)?.toInstant()?.toEpochMilli()
    }
    val datePickerState =
        rememberDatePickerState(initialSelectedDateMillis = initialMillis)

    var includeTime by rememberSaveable { mutableStateOf(initialTime != null) }
    val timeState = rememberTimePickerState(
        initialHour = initialTime?.hour ?: LocalTime.now().hour,
        initialMinute = initialTime?.minute ?: LocalTime.now().minute,
        is24Hour = false
    )

    var selectedZoneId by rememberSaveable(initialZoneId.id) {
        mutableStateOf(initialZoneId.id)
    }

    val contentScroll = rememberScrollState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    
    // Time zone data
    val allTimeZones = remember { TimeZoneData.getAllTimeZones() }
    val selectedZone = remember(selectedZoneId) {
        runCatching { ZoneId.of(selectedZoneId) }.getOrElse { systemZone }
    }

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
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .verticalScroll(contentScroll),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Pick Schedule", style = MaterialTheme.typography.titleLarge)

            DatePicker(
                state = datePickerState,
                showModeToggle = false,
                colors = DatePickerDefaults.colors()
            )

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Include time", style = MaterialTheme.typography.titleMedium)
                Switch(
                    checked = includeTime,
                    onCheckedChange = { includeTime = it }
                )
            }

            if (includeTime) {
                // Time Picker Section
                Text(
                    "Time",
                    style = MaterialTheme.typography.titleMedium
                )
                TimePicker(state = timeState)
                
                // Divider
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                
                // Time Zone Selection Section
                Text(
                    "Time Zone",
                    style = MaterialTheme.typography.titleMedium
                )
                
                // Find city name for display
                val cityName = TimeZoneData.findCityByZoneId(selectedZone) ?: selectedZone.id
                val now = ZonedDateTime.now(selectedZone)
                val offset = now.offset
                val hours = offset.totalSeconds / 3600
                val minutes = kotlin.math.abs(offset.totalSeconds % 3600) / 60
                val offsetStr = when {
                    minutes == 0 -> "UTC${if (hours >= 0) "+" else ""}$hours"
                    else -> "UTC${if (hours >= 0) "+" else ""}$hours:${minutes.toString().padStart(2, '0')}"
                }
                
                Text(
                    text = "Selected: $cityName, $offsetStr",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                // Time Zone List
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    allTimeZones.forEach { timeZoneOption ->
                        val isSelected = timeZoneOption.zoneId == selectedZone
                        
                        TextButton(
                            onClick = {
                                selectedZoneId = timeZoneOption.zoneId.id
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = timeZoneOption.displayText,
                                style = if (isSelected) {
                                    MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    MaterialTheme.typography.bodyMedium
                                }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.heightIn(min = 8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            sheetState.hide()
                            onDismiss()
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        val selectedMillis =
                            datePickerState.selectedDateMillis ?: return@Button
                        val date = Instant.ofEpochMilli(selectedMillis)
                            .atZone(systemZone)
                            .toLocalDate()
                        val zone =
                            runCatching { ZoneId.of(selectedZoneId) }
                                .getOrElse { systemZone }
                        val time = if (includeTime) {
                            LocalTime.of(timeState.hour, timeState.minute)
                        } else null
                        scope.launch {
                            sheetState.hide()
                            onConfirm(date, time, zone)
                        }
                    },
                    enabled = datePickerState.selectedDateMillis != null,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("OK")
                }
            }
        }
    }

}


private fun buildTimePoint(
    dateText: String,
    timeText: String,
    zoneIdText: String,
    timeFormatter: DateTimeFormatter
): TimePoint? {
    if (dateText.isBlank()) return null
    val date = runCatching { LocalDate.parse(dateText) }.getOrNull()
        ?: return null
    val time = timeText.takeIf { it.isNotBlank() }?.let {
        LocalTime.parse(it, timeFormatter)
    }
    val zone =
        runCatching { ZoneId.of(zoneIdText) }.getOrElse { ZoneId.systemDefault() }
    return TimePoint(date = date, time = time, zoneId = zone)
}

@Composable
private fun priorityColor(priority: Priority): Color = when (priority) {
    Priority.High -> PriorityHigh
    Priority.Medium -> PriorityMedium
    Priority.Low -> PriorityLow
    Priority.None -> MaterialTheme.colorScheme.outline
}


// ----------------- Upload Options Bottom Sheet -----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UploadOptionsBottomSheet(
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
                leadingContent = { Icon(Icons.Default.CameraAlt, contentDescription = null) },
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = rememberRipple(),
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
                leadingContent = { Icon(Icons.Default.Image, contentDescription = null) },
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = rememberRipple(),
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
                        indication = rememberRipple(),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttachmentBottomSheet(
    attachments: List<com.example.lattice.domain.model.Attachment>,
    onDismiss: () -> Unit,
    onDelete: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val contentScroll = rememberScrollState()
    
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
                .heightIn(max = 600.dp)
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .verticalScroll(contentScroll),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Attachments", style = MaterialTheme.typography.titleLarge)
            
            HorizontalDivider()
            
            if (attachments.isEmpty()) {
                Text(
                    "No attachments",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                attachments.forEach { attachment ->
                    AttachmentPreviewItem(
                        attachment = attachment,
                        onDelete = { onDelete(attachment.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AttachmentPreviewItem(
    attachment: com.example.lattice.domain.model.Attachment,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = attachment.fileName,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
                
                // Preview based on file type
                when (attachment.fileType) {
                    com.example.lattice.domain.model.AttachmentType.IMAGE -> {
                        val file = File(attachment.filePath)
                        if (file.exists()) {
                            Image(
                                painter = rememberAsyncImagePainter(
                                    ImageRequest.Builder(context)
                                        .data(file)
                                        .build()
                                ),
                                contentDescription = attachment.fileName,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Text(
                                "File not found",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    com.example.lattice.domain.model.AttachmentType.PDF,
                    com.example.lattice.domain.model.AttachmentType.DOC -> {
                        // For PDF/DOC, show first page preview (simplified - just show file info)
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = attachment.fileName,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                if (attachment.fileSize != null) {
                                    Text(
                                        text = formatFileSize(attachment.fileSize),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    else -> {
                        Text(
                            "Preview not available",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${bytes / (1024 * 1024)} MB"
    }
}
