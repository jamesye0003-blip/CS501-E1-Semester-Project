package com.example.lattice.util

import com.example.lattice.domain.model.Task
import com.example.lattice.domain.model.TimePoint
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

// 统一的时间格式（列表里展示“h:mm a”那种）
private val LOCAL_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

/**
 * 将存储在 TimePoint 中的时间（携带自己的时区）统一转换为
 * 当前系统时区下的 LocalDate，便于比较“今天/明天/区间”等。
 */
fun TimePoint.toSystemLocalDate(systemZone: ZoneId = ZoneId.systemDefault()): LocalDate {
    return if (time != null) {
        ZonedDateTime.of(date, time, zoneId)
            .withZoneSameInstant(systemZone)
            .toLocalDate()
    } else {
        date.atStartOfDay(zoneId)
            .withZoneSameInstant(systemZone)
            .toLocalDate()
    }
}

/**
 * 便捷函数：拿到 Task 的时间对应的系统时区 LocalDate（没有时间则返回 null）。
 */
fun Task.timeAsSystemLocalDate(
    systemZone: ZoneId = ZoneId.systemDefault()
): LocalDate? = time?.toSystemLocalDate(systemZone)

/**
 * 列表中展示 TimePoint 的统一格式逻辑（原来的 formatTimePointForList）。
 */
fun TimePoint.formatForList(
    systemZone: ZoneId = ZoneId.systemDefault()
): String {
    val zoneLabel = zoneId.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    val zoneSuffix = if (zoneId == systemZone) null else zoneLabel

    if (time == null) {
        val nowInStored = ZonedDateTime.now(zoneId)
        val label = when (date) {
            nowInStored.toLocalDate() -> "Today"
            nowInStored.plusDays(1).toLocalDate() -> "Tomorrow"
            else -> date.toString()
        }
        return listOfNotNull(label, zoneSuffix).joinToString(" ")
    }

    val eventInStoredZone = ZonedDateTime.of(date, time, zoneId)
    val eventInLocalZone = eventInStoredZone.withZoneSameInstant(systemZone)

    val nowLocal = ZonedDateTime.now(systemZone)
    val label = when (eventInLocalZone.toLocalDate()) {
        nowLocal.toLocalDate() -> "Today"
        nowLocal.plusDays(1).toLocalDate() -> "Tomorrow"
        else -> eventInLocalZone.toLocalDate().toString()
    }

    val timePart = eventInLocalZone.toLocalTime().format(LOCAL_TIME_FORMATTER)
    return listOfNotNull(label, timePart, zoneSuffix).joinToString(" ")
}

/**
 * UI 层所有“今天 / 明天 / 7 天内 / 当月 / 全部”过滤统一用这个枚举。
 */
enum class TaskFilter {
    Today,
    Tomorrow,
    Next7Days,
    ThisMonth,
    All
}

/**
 * 统一的按 TaskFilter 做时间过滤逻辑。
 * 所有“按日期筛选任务”的地方都应该调用这个函数。
 */
fun filterTasksByDate(
    tasks: List<Task>,
    filter: TaskFilter,
    now: ZonedDateTime = ZonedDateTime.now(ZoneId.systemDefault())
): List<Task> {
    val today = now.toLocalDate()
    val systemZone = now.zone

    return when (filter) {
        TaskFilter.Today -> {
            tasks.filter { task ->
                task.timeAsSystemLocalDate(systemZone) == today
            }
        }
        TaskFilter.Tomorrow -> {
            val tomorrow = today.plusDays(1)
            tasks.filter { task ->
                task.timeAsSystemLocalDate(systemZone) == tomorrow
            }
        }
        TaskFilter.Next7Days -> {
            val endDate = today.plusDays(7)
            tasks.filter { task ->
                val date = task.timeAsSystemLocalDate(systemZone)
                date != null && date >= today && date <= endDate
            }
        }
        TaskFilter.ThisMonth -> {
            val firstDayOfMonth = today.withDayOfMonth(1)
            val lastDayOfMonth = today.withDayOfMonth(today.lengthOfMonth())
            tasks.filter { task ->
                val date = task.timeAsSystemLocalDate(systemZone)
                date != null && date >= firstDayOfMonth && date <= lastDayOfMonth
            }
        }
        TaskFilter.All -> tasks
    }
}

/**
 * Profile 页面里“今天任务”的逻辑也走统一的过滤函数。
 */
fun filterTodayTasks(
    tasks: List<Task>,
    now: ZonedDateTime = ZonedDateTime.now(ZoneId.systemDefault())
): List<Task> = filterTasksByDate(tasks, TaskFilter.Today, now)
