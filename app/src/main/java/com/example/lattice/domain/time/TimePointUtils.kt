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
 * 统一管理 Task / TimePoint 相关的“纯时间逻辑”：
 *
 * - 不依赖 UI（Compose、Material3 等）
 * - 只依赖 domain model（Task、TimePoint）
 * - 提供：日期换算、列表过滤、展示用的格式化字符串等
 */

// 统一的时间格式（列表里展示类似 "3:05 PM" 那种）
private val LOCAL_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

/**
 * 将一个 TimePoint 转换为“系统默认时区”的 LocalDate。
 *
 * - 如果包含具体时间，则先构造 ZonedDateTime 再换算时区；
 * - 如果只有日期，则从该时区的当日 00:00 开始换算；
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
 * 便捷扩展：从 Task 上直接拿“在系统时区下的日期”（若没有时间则返回 null）。
 */
fun Task.timeAsSystemLocalDate(systemZone: ZoneId = ZoneId.systemDefault()): LocalDate? =
    time?.toSystemLocalDate(systemZone)

/**
 * 用于 UI 展示列表时的统一时间字符串，比如右侧的小时间（不含日期）。
 *
 * 若没有具体时间（只有日期），则返回 null，由 UI 自己决定显示什么占位文案。
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
 * 任务列表的日期筛选类型。
 *
 * 放在 domain 下的原因：
 * - 这是业务概念，而不是 UI 专用状态；
 * - UI 可以直接复用这个 enum，不再自己声明一份。
 */
enum class TaskFilter {
    Today,
    Tomorrow,
    Next7Days,
    ThisMonth,
    All
}

/**
 * 核心过滤逻辑：根据 TaskFilter 过滤 Task 列表。
 *
 * @param now         当前时间（带时区），方便测试时注入固定值。
 * @param systemZone  视图展示/业务判断所使用的“系统时区”，通常就是 now.zone。
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
 * 小工具：避免在 when 分支里重复写 filter 代码。
 */
private inline fun List<Task>.filterTasksByPredicate(
    crossinline predicate: (Task) -> Boolean
): List<Task> = filter { predicate(it) }

/**
 * 兼容“函数式调用”的旧用法。
 */
fun filterTasksByDate(
    tasks: List<Task>,
    filter: TaskFilter,
    now: ZonedDateTime = ZonedDateTime.now(ZoneId.systemDefault())
): List<Task> = tasks.filterByDate(filter, now)

/**
 * 给 Profile / Today tab 使用的便捷函数。
 */
fun filterTodayTasks(
    tasks: List<Task>,
    now: ZonedDateTime = ZonedDateTime.now(ZoneId.systemDefault())
): List<Task> = tasks.filterByDate(TaskFilter.Today, now)
