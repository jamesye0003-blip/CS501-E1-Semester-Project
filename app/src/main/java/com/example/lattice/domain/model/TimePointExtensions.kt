package com.example.lattice.domain.model

import com.example.lattice.domain.time.TimeConverter
import java.time.ZoneId

/**
 * TimePoint扩展函数，用于在UI层和Domain层之间转换。
 *
 * TimePoint作为UI输入辅助类，用于收集用户输入的日期、时间和时区。
 * 然后通过扩展函数转换为Task的dueAt、hasSpecificTime和sourceTimeZoneId字段。
 *
 * TimePoint extensions bridging UI input and domain fields.
 *
 * TimePoint serves as an input assistance class for the UI, used to collect the date, time and time zone entered by the user.
 * Then, the fields of dueAt, hasSpecificTime and sourceTimeZoneId of the Task are converted through the extension function.
 */

/**
 * Convert TimePoint to Task time fields.
 *
 * @return Triple<dueAt: Instant?, hasSpecificTime: Boolean, sourceTimeZoneId: String?>
 */
fun TimePoint?.toTaskTimeFields(): Triple<java.time.Instant?, Boolean, String?> {
    if (this == null) {
        return Triple(null, false, null)
    }
    
    val instant = TimeConverter.toUtcInstant(
        date = date,
        time = time,
        zoneId = zoneId
    )
    
    val hasSpecificTime = time != null
    val sourceTimeZoneId = zoneId.id
    
    return Triple(instant, hasSpecificTime, sourceTimeZoneId)
}

/**
 * Rebuild TimePoint from Task fields for UI display.
 *
 * @param systemZoneId system time zone for display
 * @return TimePoint or null
 */
fun Task.toTimePoint(systemZoneId: ZoneId = ZoneId.systemDefault()): TimePoint? {
    val instant = dueAt ?: return null
    
    val zonedDateTime = TimeConverter.toZonedDateTime(instant, systemZoneId)
    val date = zonedDateTime.toLocalDate()
    val time = if (hasSpecificTime) zonedDateTime.toLocalTime() else null
    
    // use sourceTimeZoneId as original zone, fallback to system zone
    val originalZoneId = sourceTimeZoneId?.let { ZoneId.of(it) } ?: systemZoneId
    
    return TimePoint(
        date = date,
        time = time,
        zoneId = originalZoneId
    )
}




