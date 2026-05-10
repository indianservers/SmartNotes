package com.indianservers.smartnotes

data class Note(
    val id: String,
    val title: String,
    val content: String,
    val type: String = "rich",
    val sourceUrl: String? = null,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val isDeleted: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
    val syncStatus: String = "pending_create"
)

data class TaskItem(
    val id: String,
    val noteId: String?,
    val title: String,
    val done: Boolean = false,
    val dueAt: Long? = null
)

data class AttachmentItem(
    val id: String,
    val noteId: String,
    val fileName: String,
    val mimeType: String,
    val localPath: String,
    val extractedText: String = ""
)
