package com.example.lattice.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.lattice.domain.time.TimeZoneData
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleBottomSheet(
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
                val minutes = abs(offset.totalSeconds % 3600) / 60
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
