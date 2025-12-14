package com.example.lattice.ui.screens

import android.Manifest
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
import androidx.compose.material3.Divider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import com.example.lattice.domain.time.TimeZoneOption
import com.example.lattice.viewModel.EditorViewModel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    onBack: () -> Unit,
    onSave: (String, String, Priority, TimePoint?) -> Unit,
    initialTitle: String = "",
    initialDescription: String = "",
    initialPriority: Priority = Priority.None,
    initialTime: TimePoint? = null,
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

    val isEditing = initialTitle.isNotBlank()
    val topBarTitle = when {
        isEditing -> "Edit Task"
        parentId != null -> "New Subtask"
        else -> "New Task"
    }

    val context = LocalContext.current

    // ---- 语音相关：使用 EditorViewModel 管理状态 ----
    val editorViewModel: EditorViewModel = viewModel()
    val sttUiState by editorViewModel.uiState.collectAsState()

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
                            onSave(title, description, selectedPriority, timePoint)
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
                            onSave(title, description, selectedPriority, timePoint)
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
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                
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
