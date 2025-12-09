package com.example.lattice.domain.model

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

enum class Priority {
    High,
    Medium,
    Low,
    None
}

data class TimePoint(
    val date: LocalDate,
    val time: LocalTime? = null,
    val zoneId: ZoneId = ZoneId.systemDefault()
) {
    fun formattedTime(): String? = time?.format(TIME_FORMATTER)

    fun toPayload(): String {
        val timeText = formattedTime() ?: ""
        return listOf(date.toString(), timeText, zoneId.id).joinToString("|")
    }

    companion object {
        private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        fun fromPayload(payload: String): TimePoint? {
            if (payload.isBlank()) return null
            val parts = payload.split("|")
            if (parts.isEmpty() || parts[0].isBlank()) return null
            return runCatching {
                val date = LocalDate.parse(parts[0])
                val time = parts.getOrNull(1)?.takeIf { it.isNotBlank() }?.let {
                    LocalTime.parse(it, TIME_FORMATTER)
                }
                val zone = parts.getOrNull(2)?.takeIf { it.isNotBlank() }?.let { ZoneId.of(it) }
                    ?: ZoneId.systemDefault()
                TimePoint(date = date, time = time, zoneId = zone)
            }.getOrNull()
        }
    }
}

/**
 * Domain层的Task模型。
 * Domain model for Task.
 *
 * 时间属性设计（遵循设计指南）：
 * Time fields (per guideline):
 * - dueAt: 任务截止的绝对时刻（UTC），可为null
 *          absolute UTC instant, nullable
 * - hasSpecificTime:   标记是否包含具体时间（时分秒）
 *                      whether time-of-day is present
 * - sourceTimeZoneId:  记录创建时的源时区ID
 *                      source timezone ID when created
 *
 * 注意：TimePoint保留作为UI层的输入辅助类，通过扩展函数转换为Task的新字段。
 * Note: TimePoint is kept for UI input; convert via extensions to Task fields.
 */
data class Task(
    val id: String = UUID.randomUUID().toString(),
    val userId: String? = null,  // Owning user id for local multi-account isolation.
    val title: String,
    val description: String = "",
    val dueAt: java.time.Instant? = null,  // Absolute deadline in UTC. Nullable. DB column: due_at.
    val hasSpecificTime: Boolean = false,  // Whether time-of-day is present (true = has hh:mm:ss; false = all-day).
    val sourceTimeZoneId: String? = null,  // Source time zone at creation (e.g., "Asia/Shanghai"). DB column: source_time_zone.
    val priority: Priority = Priority.None,
    val done: Boolean = false,
    val parentId: String? = null
) {
    /**
     * Compatibility: derived time property rebuilt from dueAt/hasSpecificTime/sourceTimeZoneId.
     * For backward compatibility only; prefer dueAt/hasSpecificTime/sourceTimeZoneId.
     */
    @Deprecated("Use dueAt/hasSpecificTime/sourceTimeZoneId instead", ReplaceWith("dueAt"))
    val time: TimePoint?
        get() = if (dueAt == null) null else {
            val zoneId = sourceTimeZoneId?.let { ZoneId.of(it) } ?: ZoneId.systemDefault()
            val zonedDateTime = dueAt.atZone(zoneId)
            TimePoint(
                date = zonedDateTime.toLocalDate(),
                time = if (hasSpecificTime) zonedDateTime.toLocalTime() else null,
                zoneId = zoneId
            )
        }
}
