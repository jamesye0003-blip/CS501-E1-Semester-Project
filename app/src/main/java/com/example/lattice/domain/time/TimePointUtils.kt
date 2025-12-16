package com.example.lattice.domain.time

import com.example.lattice.domain.model.Task
import com.example.lattice.domain.model.TimePoint
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 统一管理 Task / TimePoint 相关的"纯时间逻辑"
 * - 不依赖 UI（Compose、Material3 等）
 * - 只依赖 domain model（Task、TimePoint）
 * - 提供：日期换算、列表过滤、展示用的格式化字符串等
 * 
 * Unified management of "pure time logic" related to Task / TimePoint
 * - No dependency on UI (Compose, Material3, etc.)
 * - Only depends on domain model (Task, TimePoint)
 * - Provides: date conversion, list filtering, formatted strings for display, etc.
 */

// 统一的时间格式（列表里展示类似 "3:05 PM" 那种）
private val LOCAL_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

/**
 * 将一个 TimePoint 转换为"系统默认时区"的 LocalDate
 * - 如果包含具体时间，则先构造 ZonedDateTime 再换算时区
 * - 如果只有日期，则从该时区的当日 00:00 开始换算
 * 
 * Convert a TimePoint to LocalDate in "system default timezone"
 * - If it contains specific time, construct ZonedDateTime first then convert timezone
 * - If it only has date, convert from 00:00 of that day in that timezone
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
 * 便捷扩展：从 Task 上直接拿"在系统时区下的日期"（若没有时间则返回 null）
 * 使用新的dueAt字段，转换为系统时区的日期。
 * 
 * Convenient extension: get "date in system timezone" directly from Task (returns null if no time)
 * Uses the new dueAt field, converts to system timezone date.
 */
fun Task.timeAsSystemLocalDate(systemZone: ZoneId = ZoneId.systemDefault()): LocalDate? =
    dueAt?.let { TimeConverter.toLocalDate(it, systemZone) }

/**
 * 用于 UI 展示列表时的统一时间字符串，比如右侧的小时间（不含日期）
 * 若没有具体时间（只有日期），则返回 null，由 UI 自己决定显示什么占位文案。
 * 
 * Unified time string for UI list display, such as the small time on the right (without date)
 * If there is no specific time (only date), returns null, and UI decides what placeholder text to display.
 */
fun TimePoint?.formatTimeForList(
    systemZone: ZoneId = ZoneId.systemDefault(),
    formatter: DateTimeFormatter = LOCAL_TIME_FORMATTER
): String? {
    val tp = this ?: return null
    val localTime: LocalTime = if (tp.time != null) {
        ZonedDateTime.of(tp.date, tp.time, tp.zoneId)
            .withZoneSameInstant(systemZone)
            .toLocalTime()
    } else {
        // 只有日期时一般不显示时间；你也可以改成 00:00 等特殊策略
        return null
    }
    return formatter.format(localTime)
}

/**
 * 从Task的dueAt字段格式化时间字符串（用于UI展示）
 * 
 * Format time string from Task's dueAt field (for UI display)
 * 
 * @param systemZone 系统当前时区 / System current timezone
 * @param formatter 时间格式化器 / Time formatter
 * @return 格式化的时间字符串，如果没有具体时间则返回null / Formatted time string, returns null if no specific time
 */
fun Task.formatTimeForList(
    systemZone: ZoneId = ZoneId.systemDefault(),
    formatter: DateTimeFormatter = LOCAL_TIME_FORMATTER
): String? {
    val instant = dueAt ?: return null
    if (!hasSpecificTime) return null
    
    val localTime = TimeConverter.toLocalTime(instant, systemZone)
    return formatter.format(localTime)
}

/**
 * 任务列表的日期筛选类型
 * 
 * 放在 domain 下的原因：
 * - 这是业务概念，而不是 UI 专用状态
 * - UI 可以直接复用这个 enum，不再自己声明一份
 * 
 * Date filter type for task list
 * 
 * Reasons for placing it in domain:
 * - This is a business concept, not UI-specific state
 * - UI can directly reuse this enum without declaring its own
 */
enum class TaskFilter {
    Today,
    Tomorrow,
    Next7Days,
    ThisMonth,
    All
}

/**
 * 获取 TaskFilter 枚举的用户友好显示名称
 * 
 * Get user-friendly display name for TaskFilter enum
 * 
 * @return 过滤器的本地化显示文本 / Localized display text for the filter
 */
fun TaskFilter.getDisplayName(): String = when (this) {
    TaskFilter.Today -> "Today"
    TaskFilter.Tomorrow -> "Tomorrow"
    TaskFilter.Next7Days -> "Next 7 Days"
    TaskFilter.ThisMonth -> "This Month"
    TaskFilter.All -> "All tasks"
}

/**
 * 核心过滤逻辑：根据 TaskFilter 过滤 Task 列表
 * 
 * Core filtering logic: filter Task list according to TaskFilter
 * 
 * @param now 当前时间（带时区），方便测试时注入固定值 / Current time (with timezone), convenient for injecting fixed values during testing
 * @param systemZone 视图展示/业务判断所使用的"系统时区"，通常就是 now.zone / "System timezone" used for view display/business judgment, usually now.zone
 */
fun List<Task>.filterByDate(
    filter: TaskFilter,
    now: ZonedDateTime = ZonedDateTime.now(ZoneId.systemDefault()),
    systemZone: ZoneId = now.zone
): List<Task> {
    if (isEmpty()) return emptyList()

    val today: LocalDate = now.withZoneSameInstant(systemZone).toLocalDate()

    return when (filter) {
        TaskFilter.Today -> {
            filterTasksByPredicate { task ->
                task.timeAsSystemLocalDate(systemZone) == today
            }
        }

        TaskFilter.Tomorrow -> {
            val tomorrow = today.plusDays(1)
            filterTasksByPredicate { task ->
                task.timeAsSystemLocalDate(systemZone) == tomorrow
            }
        }

        TaskFilter.Next7Days -> {
            val start = today
            val endInclusive = today.plusDays(7)
            filterTasksByPredicate { task ->
                val date = task.timeAsSystemLocalDate(systemZone)
                date != null && !date.isBefore(start) && !date.isAfter(endInclusive)
            }
        }

        TaskFilter.ThisMonth -> {
            val firstDayOfMonth = LocalDate.of(today.year, today.month, 1)
            val lastDayOfMonth = firstDayOfMonth.plusMonths(1).minusDays(1)
            filterTasksByPredicate { task ->
                val date = task.timeAsSystemLocalDate(systemZone)
                date != null && !date.isBefore(firstDayOfMonth) && !date.isAfter(lastDayOfMonth)
            }
        }

        TaskFilter.All -> this
    }
}

/**
 * 小工具：避免在 when 分支里重复写 filter 代码
 * 
 * Utility: avoid repeating filter code in when branches
 */
private inline fun List<Task>.filterTasksByPredicate(
    crossinline predicate: (Task) -> Boolean
): List<Task> = filter { predicate(it) }

/**
 * 兼容"函数式调用"的旧用法
 * 
 * Compatible with old "functional call" usage
 */
fun filterTasksByDate(
    tasks: List<Task>,
    filter: TaskFilter,
    now: ZonedDateTime = ZonedDateTime.now(ZoneId.systemDefault())
): List<Task> = tasks.filterByDate(filter, now)

/**
 * 给 Profile / Today tab 使用的便捷函数
 * 
 * Convenient function for Profile / Today tab
 */
fun filterTodayTasks(
    tasks: List<Task>,
    now: ZonedDateTime = ZonedDateTime.now(ZoneId.systemDefault())
): List<Task> = tasks.filterByDate(TaskFilter.Today, now)

/**
 * 从字符串构建 TimePoint 对象 / Build TimePoint object from strings
 * 
 * 根据日期字符串、时间字符串、时区字符串和时间格式化器构建 TimePoint。
 * 如果日期字符串为空则返回 null；时间字符串为空时创建只有日期的 TimePoint。
 * 
 * Builds TimePoint from date string, time string, timezone string and time formatter.
 * Returns null if date string is blank; creates TimePoint with date only if time string is blank.
 * 
 * @param dateText 日期字符串（格式：yyyy-MM-dd）/ Date string (format: yyyy-MM-dd)
 * @param timeText 时间字符串（格式由 timeFormatter 决定）/ Time string (format determined by timeFormatter)
 * @param zoneIdText 时区 ID 字符串（如 "Asia/Shanghai"）/ Timezone ID string (e.g., "Asia/Shanghai")
 * @param timeFormatter 时间格式化器，用于解析 timeText / Time formatter for parsing timeText
 * @return TimePoint 对象，如果日期无效则返回 null / TimePoint object, returns null if date is invalid
 */
fun buildTimePoint(
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
