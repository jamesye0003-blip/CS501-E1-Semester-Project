package com.example.lattice.domain.time

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 时间转换工具类 / Time conversion utilities
 *
 * 核心职责：
 * 1. 将用户输入的日期/时间/时区转换为UTC Instant（存储）
 * 2. 将UTC Instant转换为用户设备时区的日期/时间（展示）
 * 
 * Core responsibilities:
 * 1. Convert user-input date/time/timezone into a UTC Instant (for storage).
 * 2. Convert a UTC Instant into date/time in the user's device timezone (for display).
 * 
 * 遵循设计指南 / Follow the design guildlines: 
 * - 数据库存储UTC绝对时刻 / store UTC in DB
 * - 应用层负责时区转换 / app layer does conversions
 * - 全天任务使用当日00:00作为基准点 / all-day uses 00:00 baseline
 */
object TimeConverter {
    
    /**
     * Convert user date/time/zone into UTC instant.
     *
     * @param date required when time present
     * @param time optional time component
     * @param zoneId user-chosen zone
     * @return UTC Instant, or null if date missing
     */
    fun toUtcInstant(
        date: LocalDate?,
        time: LocalTime?,
        zoneId: ZoneId
    ): Instant? {
        if (date == null) return null
        
        // 如果time为null，使用当日00:00作为全天任务的基准点
        val localTime = time ?: LocalTime.MIDNIGHT
        
        // 在指定时区构造ZonedDateTime，然后转换为UTC
        val zonedDateTime = ZonedDateTime.of(date, localTime, zoneId)
        return zonedDateTime.toInstant()
    }
    
    /**
     * 将UTC Instant转换为指定时区的LocalDate。
     * 
     * @param instant UTC时刻
     * @param targetZoneId 目标时区（通常是设备当前时区）
     * @return 目标时区的日期
     */
    fun toLocalDate(instant: Instant, targetZoneId: ZoneId): LocalDate {
        return instant.atZone(targetZoneId).toLocalDate()
    }
    
    /**
     * 将UTC Instant转换为指定时区的LocalTime。
     * 
     * @param instant UTC时刻
     * @param targetZoneId 目标时区（通常是设备当前时区）
     * @return 目标时区的时间
     */
    fun toLocalTime(instant: Instant, targetZoneId: ZoneId): LocalTime {
        return instant.atZone(targetZoneId).toLocalTime()
    }
    
    /**
     * 将UTC Instant转换为指定时区的ZonedDateTime。
     * 
     * @param instant UTC时刻
     * @param targetZoneId 目标时区（通常是设备当前时区）
     * @return 目标时区的ZonedDateTime
     */
    fun toZonedDateTime(instant: Instant, targetZoneId: ZoneId): ZonedDateTime {
        return instant.atZone(targetZoneId)
    }
}




