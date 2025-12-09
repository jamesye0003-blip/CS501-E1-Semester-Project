package com.example.lattice.data.local.room.db

import androidx.room.TypeConverter
import java.time.Instant

/**
 * Room类型转换器。
 * 用于将Java/Kotlin类型转换为Room可存储的类型。
 * Room type converters for non-primitive fields.
 */
class Converters {
    
    /**
     * Convert Instant to epoch millis timestamp (Type Long) for storage.
     */
    @TypeConverter
    fun fromInstant(value: Instant?): Long? {
        return value?.toEpochMilli()
    }
    
    /**
     * Convert epoch millis timestamp (Type Long) back to Instant.
     */
    @TypeConverter
    fun toInstant(value: Long?): Instant? {
        return value?.let { Instant.ofEpochMilli(it) }
    }
}




