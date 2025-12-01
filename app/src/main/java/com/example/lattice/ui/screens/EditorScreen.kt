package com.example.lattice.ui.screens

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.lattice.data.SpeechResult
import com.example.lattice.data.SpeechToTextRepository
import com.example.lattice.domain.model.Priority
import com.example.lattice.domain.model.TimePoint
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.CoroutineScope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    onBack: () -> Unit,
    onSave: (String, String, Priority, TimePoint?) -> Unit,
    initialTitle: String = "",
    initialDescription: String = "",
    initialPriority: Priority = Priority.None,
    initialTime: TimePoint? = null,
    primaryLabel: String = "Save"
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

    val isEditing = initialTitle.isNotBlank()
    val topBarTitle = if (isEditing) "Edit Task" else "New Task"

    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val speechRepo = remember { SpeechToTextRepository(context) }

    var isRecording by rememberSaveable { mutableStateOf(false) }
    var isTranscribing by rememberSaveable { mutableStateOf(false) }
    var speechError by rememberSaveable { mutableStateOf<String?>(null) }

    // 请求 RECORD_AUDIO 权限
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startRecording(
                scope = scope,
                speechRepo = speechRepo,
                onText = { text ->
                    description = listOf(description, text)
                        .filter { it.isNotBlank() }
                        .joinToString("\n")
                },
                onError = { msg -> speechError = msg },
                onStateChange = { rec, tr ->
                    isRecording = rec
                    isTranscribing = tr
                }
            )
        } else {
            speechError = "Microphone permission denied."
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(topBarTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Title
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
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
                            onSave(title, description, selectedPriority, timePoint)
                            onBack()
                        }
                    }
                ),
                trailingIcon = {
                    IconButton(
                        enabled = !isRecording && !isTranscribing,
                        onClick = {
                            speechError = null
                            val granted =
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                            if (granted) {
                                startRecording(
                                    scope = scope,
                                    speechRepo = speechRepo,
                                    onText = { text ->
                                        description = listOf(description, text)
                                            .filter { it.isNotBlank() }
                                            .joinToString("\n")
                                    },
                                    onError = { msg -> speechError = msg },
                                    onStateChange = { rec, tr ->
                                        isRecording = rec
                                        isTranscribing = tr
                                    }
                                )
                            } else {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
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
                    text = speechError!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // Priority
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Priority", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Priority.values().forEach { priority ->
                        FilterChip(
                            selected = selectedPriority == priority,
                            onClick = { selectedPriority = priority },
                            label = { Text(priority.name) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor =
                                    priorityColor(priority).copy(alpha = 0.2f),
                                selectedLabelColor = priorityColor(priority),
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }

            // Scheduled time（保持你原来的逻辑）
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Scheduled Time", style = MaterialTheme.typography.titleMedium)

                val zoneDisplay = runCatching { ZoneId.of(zoneIdText) }
                    .getOrDefault(ZoneId.systemDefault())
                    .getDisplayName(TextStyle.SHORT, Locale.getDefault())

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
                            onSave(title, description, selectedPriority, timePoint)
                            onBack()
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
}

// ----------------- 以下辅助组件与之前版本一致，只是放在同一文件 -----------------

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

    var zoneQuery by rememberSaveable { mutableStateOf("") }
    var selectedZoneId by rememberSaveable(initialZoneId.id) {
        mutableStateOf(initialZoneId.id)
    }

    val allZones = remember { ZoneId.getAvailableZoneIds().sorted() }
    val filteredZones = remember(zoneQuery, allZones) {
        val q = zoneQuery.trim()
        if (q.isBlank()) allZones else allZones.filter {
            it.contains(q, ignoreCase = true)
        }
    }

    val contentScroll = rememberScrollState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var showTimeSheet by remember { mutableStateOf(false) }

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
                    onCheckedChange = {
                        includeTime = it
                        if (!it) showTimeSheet = false
                    }
                )
            }

            if (includeTime) {
                val selectedZone = runCatching { ZoneId.of(selectedZoneId) }
                    .getOrElse { systemZone }
                val currentTime = LocalTime.of(timeState.hour, timeState.minute)
                val timeDisplay = currentTime.format(
                    DateTimeFormatter.ofPattern(
                        "h:mm a",
                        Locale.getDefault()
                    )
                )
                Text(
                    text = "Time: $timeDisplay (${selectedZone.getDisplayName(TextStyle.SHORT, Locale.getDefault())})",
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(
                    onClick = { showTimeSheet = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Set time & time zone")
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

    if (includeTime && showTimeSheet) {
        TimeZoneSheet(
            timeState = timeState,
            zoneQuery = zoneQuery,
            onZoneQueryChange = { zoneQuery = it },
            filteredZones = filteredZones,
            selectedZoneId = selectedZoneId,
            onZoneSelected = { zoneId ->
                selectedZoneId = zoneId
                zoneQuery = zoneId
            },
            onDismiss = { showTimeSheet = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeZoneSheet(
    timeState: androidx.compose.material3.TimePickerState,
    zoneQuery: String,
    onZoneQueryChange: (String) -> Unit,
    filteredZones: List<String>,
    selectedZoneId: String,
    onZoneSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val zoneListScroll = rememberScrollState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { sheetState.show() }

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
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Set Time & Time Zone", style = MaterialTheme.typography.titleLarge)

            TimePicker(state = timeState)

            OutlinedTextField(
                value = zoneQuery,
                onValueChange = onZoneQueryChange,
                label = { Text("Search time zone") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
                    .verticalScroll(zoneListScroll),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                filteredZones.forEach { zoneId ->
                    val display = runCatching { ZoneId.of(zoneId) }.getOrNull()
                    val shorthand =
                        display?.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                    val label =
                        if (shorthand.isNullOrBlank()) zoneId else "$zoneId ($shorthand)"

                    TextButton(
                        onClick = {
                            onZoneSelected(zoneId)
                            scope.launch {
                                sheetState.hide()
                                onDismiss()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = label,
                            style =
                                if (zoneId == selectedZoneId)
                                    MaterialTheme.typography.bodyLarge
                                else
                                    MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                if (filteredZones.isEmpty()) {
                    Text(
                        "No time zones found",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            OutlinedButton(
                onClick = {
                    scope.launch {
                        sheetState.hide()
                        onDismiss()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Done")
            }
        }
    }
}
private fun startRecording(
    scope: CoroutineScope,
    speechRepo: SpeechToTextRepository,
    onText: (String) -> Unit,
    onError: (String) -> Unit,
    onStateChange: (Boolean, Boolean) -> Unit
) {
    scope.launch {
        // 正在录音
        onStateChange(true, false)

        val result = speechRepo.recordAndTranscribe(
            seconds = 5,
            languageCode = "en-US"   // 需要中文可以改成 "zh-CN"
        )

        when (result) {
            is SpeechResult.Success -> {
                onStateChange(false, false)
                onText(result.text)
            }
            is SpeechResult.Error -> {
                onStateChange(false, false)
                onError(result.message)
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
    Priority.High -> Color(0xFFE53935)
    Priority.Medium -> Color(0xFFFFB300)
    Priority.Low -> Color(0xFF1E88E5)
    Priority.None -> MaterialTheme.colorScheme.outline
}

/**
 * 辅助：TimePoint 上的时间格式（如果你原来有这个扩展函数，可以删掉这里）
 */
private fun TimePoint.formattedTime(): String? =
    this.time?.format(DateTimeFormatter.ofPattern("HH:mm"))