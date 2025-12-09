package com.example.lattice.domain.time

import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 时区数据模型，包含城市名和对应的ZoneId。
 * Time zone data model with city name and ZoneId.
 */
data class TimeZoneOption(
    val cityName: String,
    val zoneId: ZoneId,
    val utcOffset: String
) {
    /**
     * 显示格式：城市名, UTC偏移。
     * Display format: City Name, UTC Offset.
     */
    val displayText: String
        get() = "$cityName, $utcOffset"
}

/**
 * 全球主要城市时区数据。
 * Major cities time zones data.
 */
object TimeZoneData {
    
    /**
     * 获取所有时区选项列表，按UTC偏移排序。
     * Get all time zone options sorted by UTC offset.
     */
    fun getAllTimeZones(): List<TimeZoneOption> {
        return TIME_ZONE_MAP.map { (city, zoneId) ->
            val now = ZonedDateTime.now(zoneId)
            val offset = now.offset
            val totalSeconds = offset.totalSeconds
            val hours = totalSeconds / 3600
            val minutes = kotlin.math.abs((totalSeconds % 3600) / 60)
            
            val offsetStr = when {
                minutes == 0 -> "UTC${if (hours >= 0) "+" else ""}$hours"
                else -> "UTC${if (hours >= 0) "+" else ""}$hours:${minutes.toString().padStart(2, '0')}"
            }
            
            TimeZoneOption(
                cityName = city,
                zoneId = zoneId,
                utcOffset = offsetStr
            )
        }.sortedWith(compareBy({ 
            val now = ZonedDateTime.now(it.zoneId)
            -now.offset.totalSeconds // Sort by UTC offset (negative for descending)
        }, { it.cityName }))
    }
    
    /**
     * 根据ZoneId查找对应的城市名。
     * Find city name by ZoneId.
     */
    fun findCityByZoneId(zoneId: ZoneId): String? {
        return TIME_ZONE_MAP.entries.firstOrNull { it.value == zoneId }?.key
    }
    
    /**
     * 根据城市名查找ZoneId。
     * Find ZoneId by city name.
     */
    fun findZoneIdByCity(city: String): ZoneId? {
        return TIME_ZONE_MAP[city]
    }
    
    /**
     * 主要城市与时区的映射表。
     * Mapping of major cities to their time zones.
     */
    private val TIME_ZONE_MAP = mapOf(
        // UTC-12 to UTC-1
        "Honolulu" to ZoneId.of("Pacific/Honolulu"),                    // UTC-10
        "Anchorage" to ZoneId.of("America/Anchorage"),                  // UTC-9
        "Los Angeles" to ZoneId.of("America/Los_Angeles"),              // UTC-8
        "Denver" to ZoneId.of("America/Denver"),                        // UTC-7
        "Chicago" to ZoneId.of("America/Chicago"),                      // UTC-6
        "New York" to ZoneId.of("America/New_York"),                    // UTC-5
        "Boston" to ZoneId.of("America/New_York"),                      // UTC-5 (same as New York)
        "Caracas" to ZoneId.of("America/Caracas"),                      // UTC-4
        "Santiago" to ZoneId.of("America/Santiago"),                    // UTC-3
        "São Paulo" to ZoneId.of("America/Sao_Paulo"),                  // UTC-3
        "Rio de Janeiro" to ZoneId.of("America/Sao_Paulo"),             // UTC-3
        "Buenos Aires" to ZoneId.of("America/Argentina/Buenos_Aires"),  // UTC-3
        "Nuuk" to ZoneId.of("America/Nuuk"),                            // UTC-3 (Greenland)
        "Cape Verde" to ZoneId.of("Atlantic/Cape_Verde"),               // UTC-1
        
        // UTC+0
        "London" to ZoneId.of("Europe/London"),         // UTC+0
        "Dublin" to ZoneId.of("Europe/Dublin"),         // UTC+0
        "Lisbon" to ZoneId.of("Europe/Lisbon"),         // UTC+0
        "Reykjavik" to ZoneId.of("Atlantic/Reykjavik"), // UTC+0
        
        // UTC+1 to UTC+3
        "Paris" to ZoneId.of("Europe/Paris"),           // UTC+1
        "Berlin" to ZoneId.of("Europe/Berlin"),         // UTC+1
        "Rome" to ZoneId.of("Europe/Rome"),             // UTC+1
        "Madrid" to ZoneId.of("Europe/Madrid"),         // UTC+1
        "Amsterdam" to ZoneId.of("Europe/Amsterdam"),   // UTC+1
        "Brussels" to ZoneId.of("Europe/Brussels"),     // UTC+1
        "Vienna" to ZoneId.of("Europe/Vienna"),         // UTC+1
        "Stockholm" to ZoneId.of("Europe/Stockholm"),   // UTC+1
        "Warsaw" to ZoneId.of("Europe/Warsaw"),         // UTC+1
        "Prague" to ZoneId.of("Europe/Prague"),         // UTC+1
        "Budapest" to ZoneId.of("Europe/Budapest"),     // UTC+1
        "Cairo" to ZoneId.of("Africa/Cairo"),           // UTC+2
        "Athens" to ZoneId.of("Europe/Athens"),         // UTC+2
        "Helsinki" to ZoneId.of("Europe/Helsinki"),     // UTC+2
        "Jerusalem" to ZoneId.of("Asia/Jerusalem"),     // UTC+2
        "Moscow" to ZoneId.of("Europe/Moscow"),         // UTC+3
        "Istanbul" to ZoneId.of("Europe/Istanbul"),     // UTC+3
        "Nairobi" to ZoneId.of("Africa/Nairobi"),       // UTC+3
        
        // UTC+4 to UTC+6
        "Dubai" to ZoneId.of("Asia/Dubai"),         // UTC+4
        "Tehran" to ZoneId.of("Asia/Tehran"),       // UTC+3:30
        "Karachi" to ZoneId.of("Asia/Karachi"),     // UTC+5
        "Mumbai" to ZoneId.of("Asia/Kolkata"),      // UTC+5:30
        "New Delhi" to ZoneId.of("Asia/Kolkata"),   // UTC+5:30
        "Dhaka" to ZoneId.of("Asia/Dhaka"),         // UTC+6
        "Almaty" to ZoneId.of("Asia/Almaty"),       // UTC+6
        
        // UTC+7 to UTC+9
        "Bangkok" to ZoneId.of("Asia/Bangkok"),     // UTC+7
        "Jakarta" to ZoneId.of("Asia/Jakarta"),     // UTC+7
        "Hanoi" to ZoneId.of("Asia/Ho_Chi_Minh"),   // UTC+7
        "Singapore" to ZoneId.of("Asia/Singapore"), // UTC+8
        "Beijing" to ZoneId.of("Asia/Shanghai"),    // UTC+8
        "Shanghai" to ZoneId.of("Asia/Shanghai"),   // UTC+8
        "Hong Kong" to ZoneId.of("Asia/Hong_Kong"), // UTC+8
        "Taipei" to ZoneId.of("Asia/Taipei"),       // UTC+8
        "Manila" to ZoneId.of("Asia/Manila"),       // UTC+8
        "Perth" to ZoneId.of("Australia/Perth"),    // UTC+8
        "Seoul" to ZoneId.of("Asia/Seoul"),         // UTC+9
        "Tokyo" to ZoneId.of("Asia/Tokyo"),         // UTC+9
        "Osaka" to ZoneId.of("Asia/Tokyo"),         // UTC+9 (same as Tokyo)
        
        // UTC+10 to UTC+12
        "Sydney" to ZoneId.of("Australia/Sydney"),          // UTC+10
        "Melbourne" to ZoneId.of("Australia/Melbourne"),    // UTC+10
        "Brisbane" to ZoneId.of("Australia/Brisbane"),      // UTC+10
        "Vladivostok" to ZoneId.of("Asia/Vladivostok"),     // UTC+10
        "Auckland" to ZoneId.of("Pacific/Auckland"),        // UTC+12
        "Fiji" to ZoneId.of("Pacific/Fiji"),                // UTC+12
        "Wellington" to ZoneId.of("Pacific/Auckland"),      // UTC+12 (same as Auckland)
    )
}

