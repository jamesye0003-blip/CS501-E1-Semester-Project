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

data class Task(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String = "",
    val time: TimePoint? = null,
    val priority: Priority = Priority.None,
    val done: Boolean = false,
    val parentId: String? = null
)
