package com.example.lattice.data.local.room.db

import androidx.room.TypeConverter
import com.example.lattice.domain.model.Attachment
import com.example.lattice.domain.model.AttachmentType
import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject

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
    
    /**
     * Convert List<Attachment> to JSON string for storage.
     */
    @TypeConverter
    fun fromAttachmentList(value: List<Attachment>?): String? {
        if (value == null || value.isEmpty()) return null
        return try {
            val jsonArray = JSONArray()
            value.forEach { attachment ->
                val jsonObject = JSONObject().apply {
                    put("id", attachment.id)
                    put("filePath", attachment.filePath)
                    put("fileName", attachment.fileName)
                    put("fileType", attachment.fileType.name)
                    attachment.mimeType?.let { put("mimeType", it) }
                    attachment.fileSize?.let { put("fileSize", it) }
                }
                jsonArray.put(jsonObject)
            }
            jsonArray.toString()
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Convert JSON string back to List<Attachment>.
     */
    @TypeConverter
    fun toAttachmentList(value: String?): List<Attachment>? {
        if (value.isNullOrBlank()) return emptyList()
        return try {
            val jsonArray = JSONArray(value)
            val attachments = mutableListOf<Attachment>()
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                attachments.add(
                    Attachment(
                        id = jsonObject.getString("id"),
                        filePath = jsonObject.getString("filePath"),
                        fileName = jsonObject.getString("fileName"),
                        fileType = AttachmentType.valueOf(jsonObject.getString("fileType")),
                        mimeType = jsonObject.optString("mimeType").takeIf { !it.isEmpty() },
                        fileSize = if (jsonObject.has("fileSize")) jsonObject.getLong("fileSize") else null
                    )
                )
            }
            attachments
        } catch (e: Exception) {
            emptyList()
        }
    }
}




