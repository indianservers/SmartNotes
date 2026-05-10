package com.indianservers.smartnotes

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.util.UUID

class AttachmentStore(private val context: Context) {
    fun importUri(noteId: String, uri: Uri): AttachmentItem {
        val resolver = context.contentResolver
        val name = resolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && idx >= 0) cursor.getString(idx) else null
        } ?: "attachment-${System.currentTimeMillis()}"
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        val dir = File(context.filesDir, "attachments/$noteId").apply { mkdirs() }
        val outFile = File(dir, "${UUID.randomUUID()}-$name")
        resolver.openInputStream(uri)?.use { input ->
            outFile.outputStream().use { output -> input.copyTo(output) }
        }
        val extracted = when {
            mime.startsWith("text/") -> outFile.readText()
            mime.startsWith("image/") -> "Image captured: $name"
            mime == "application/pdf" -> "PDF captured: $name"
            else -> ""
        }
        return AttachmentItem(UUID.randomUUID().toString(), noteId, name, mime, outFile.absolutePath, extracted)
    }
}
