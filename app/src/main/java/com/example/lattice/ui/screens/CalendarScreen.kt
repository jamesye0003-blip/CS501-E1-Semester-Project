package com.example.lattice.ui.screens

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.lattice.domain.model.Task
import com.example.lattice.domain.model.toTimePoint
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private data class CalendarCell(
    val date: LocalDate,
    val inMonth: Boolean
)

/**
 * IMPORTANT:
 * This project uses a LocalIndication that may still be a legacy Indication (PlatformRipple).
 * New default Modifier.clickable() requires IndicationNodeFactory and will crash.
 * Therefore, all clickables in this file use the overload that explicitly passes LocalIndication.current.
 */
@Composable
private fun Modifier.safeClickable(
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier {
    val indication = LocalIndication.current
    val interactionSource = remember { MutableInteractionSource() }
    return this.clickable(
        enabled = enabled,
        interactionSource = interactionSource,
        indication = indication,
        onClick = onClick
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    tasks: List<Task>,
    onEdit: (String) -> Unit,
    onAddTask: () -> Unit
) {
    val today = remember { LocalDate.now() }
    var month by rememberSaveable { mutableStateOf(YearMonth.now()) }

    // Default: do not show completed tasks (configurable)
    var showCompleted by rememberSaveable { mutableStateOf(false) }

    // Select a day -> show a dialog with that day's tasks
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }

    val tasksByDate = remember(tasks, showCompleted) {
        tasks
            .asSequence()
            .filter { showCompleted || !it.done }
            .mapNotNull { t ->
                val tp = t.toTimePoint() ?: return@mapNotNull null
                tp.date to t
            }
            .groupBy({ it.first }, { it.second })
    }

    val monthTitleFormatter = remember {
        DateTimeFormatter.ofPattern("yyyy MMM", Locale.getDefault())
    }

    val weekdayLabels = remember {
        // Sunday-first
        listOf(
            DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY
        )
    }

    val cells = remember(month) {
        buildMonthCells(month)
    }

    LaunchedEffect(month) {
        selectedDate = null
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(month.atDay(1).format(monthTitleFormatter)) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(),
                navigationIcon = {
                    IconButton(onClick = { month = month.minusMonths(1) }) {
                        Icon(Icons.Filled.ChevronLeft, contentDescription = "Prev Month")
                    }
                },
                actions = {
                    IconButton(onClick = { month = month.plusMonths(1) }) {
                        Icon(Icons.Filled.ChevronRight, contentDescription = "Next Month")
                    }
                    IconButton(onClick = { month = YearMonth.now() }) {
                        Icon(Icons.Filled.Today, contentDescription = "Today")
                    }
                    TextButton(onClick = { showCompleted = !showCompleted }) {
                        Text(if (showCompleted) "Done: ON" else "Done: OFF")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // Weekday header row
            Row(modifier = Modifier.fillMaxWidth()) {
                weekdayLabels.forEach { dow ->
                    Text(
                        text = dow.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(cells) { cell ->
                    val dayTasks = tasksByDate[cell.date].orEmpty()
                    CalendarDayCell(
                        cell = cell,
                        isToday = cell.date == today,
                        tasks = dayTasks,
                        onClick = { selectedDate = cell.date }
                    )
                }
            }
        }

        // Day detail dialog
        val d = selectedDate
        if (d != null) {
            val dayTasks = tasksByDate[d].orEmpty()
            AlertDialog(
                onDismissRequest = { selectedDate = null },
                title = {
                    Text(
                        text = d.toString(),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                text = {
                    if (dayTasks.isEmpty()) {
                        Text("No tasks.")
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            dayTasks.forEach { t ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .safeClickable { onEdit(t.id) }
                                        .padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = t.title.ifBlank { "(Untitled)" },
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (t.done) "Done" else "",
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.alpha(if (t.done) 0.7f else 0f)
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        selectedDate = null
                        onAddTask()
                    }) {
                        Text("New")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { selectedDate = null }) {
                        Text("Close")
                    }
                }
            )
        }
    }
}

@Composable
private fun CalendarDayCell(
    cell: CalendarCell,
    isToday: Boolean,
    tasks: List<Task>,
    onClick: () -> Unit
) {
    // Show at most 2 tasks; display the rest as '+N'
    val preview = tasks.take(2)
    val remaining = tasks.size - preview.size

    val baseAlpha = if (cell.inMonth) 1f else 0.35f
    val container = if (isToday) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }

    Surface(
        tonalElevation = if (isToday) 2.dp else 0.dp,
        shadowElevation = 0.dp,
        color = container,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp)
            .alpha(baseAlpha)
            .safeClickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = cell.date.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelLarge
            )

            preview.forEach { t ->
                Text(
                    text = "• ${t.title.ifBlank { "(Untitled)" }}",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (remaining > 0) {
                Text(
                    text = "+$remaining",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

private fun buildMonthCells(month: YearMonth): List<CalendarCell> {
    val first = month.atDay(1)

    // Sunday-first grid:
    // DayOfWeek.value: Monday=1..Sunday=7
    // Using (value % 7) gives Sunday=0, Monday=1,...Saturday=6
    val offset = first.dayOfWeek.value % 7

    val start = first.minusDays(offset.toLong())
    val totalCells = 42 // 6 weeks * 7 columns

    return (0 until totalCells).map { i ->
        val d = start.plusDays(i.toLong())
        CalendarCell(
            date = d,
            inMonth = d.month == month.month && d.year == month.year
        )
    }
}
