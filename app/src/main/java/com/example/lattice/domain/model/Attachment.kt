package com.example.lattice.domain.model

import java.util.UUID

/**
 * 附件类型枚举
 */
enum class AttachmentType {
    IMAGE,
    PDF,
    DOC,
    OTHER
}

/**
 * Domain层的附件模型
 */
data class Attachment(
    val id: String = UUID.randomUUID().toString(),
    val filePath: String,  // 本地文件路径
    val fileName: String,  // 文件名
    val fileType: AttachmentType,  // 文件类型
    val mimeType: String? = null,  // MIME 类型
    val fileSize: Long? = null  // 文件大小（字节）
) {
    /**
     * 从文件路径推断文件类型
     */
    companion object {
        fun inferTypeFromPath(filePath: String): AttachmentType {
            val extension = filePath.substringAfterLast('.', "").lowercase()
            return when (extension) {
                "jpg", "jpeg", "png", "gif", "webp", "bmp" -> AttachmentType.IMAGE
                "pdf" -> AttachmentType.PDF
                "doc", "docx" -> AttachmentType.DOC
                else -> AttachmentType.OTHER
            }
        }
    }
}
