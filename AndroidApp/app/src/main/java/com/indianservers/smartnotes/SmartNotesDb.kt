package com.indianservers.smartnotes

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.UUID

class SmartNotesDb(context: Context) : SQLiteOpenHelper(context, "smart_notes_native.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE notes(
                id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                content TEXT NOT NULL,
                type TEXT NOT NULL,
                source_url TEXT,
                is_pinned INTEGER NOT NULL DEFAULT 0,
                is_archived INTEGER NOT NULL DEFAULT 0,
                is_deleted INTEGER NOT NULL DEFAULT 0,
                updated_at INTEGER NOT NULL,
                sync_status TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE tasks(
                id TEXT PRIMARY KEY,
                note_id TEXT,
                title TEXT NOT NULL,
                done INTEGER NOT NULL DEFAULT 0,
                due_at INTEGER
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE attachments(
                id TEXT PRIMARY KEY,
                note_id TEXT NOT NULL,
                file_name TEXT NOT NULL,
                mime_type TEXT NOT NULL,
                local_path TEXT NOT NULL,
                extracted_text TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun listNotes(query: String = ""): List<Note> {
        val q = "%${query.lowercase()}%"
        val cursor = readableDatabase.rawQuery(
            """
            SELECT DISTINCT n.* FROM notes n
            LEFT JOIN attachments a ON a.note_id = n.id
            WHERE n.is_deleted = 0 AND (
                ? = '%%' OR lower(n.title) LIKE ? OR lower(n.content) LIKE ? OR lower(a.file_name) LIKE ? OR lower(a.extracted_text) LIKE ?
            )
            ORDER BY n.is_pinned DESC, n.updated_at DESC
            """.trimIndent(),
            arrayOf(q, q, q, q, q)
        )
        return cursor.use {
            buildList {
                while (it.moveToNext()) add(cursorToNote(it))
            }
        }
    }

    fun getNote(id: String): Note? {
        return readableDatabase.query("notes", null, "id=?", arrayOf(id), null, null, null).use {
            if (it.moveToFirst()) cursorToNote(it) else null
        }
    }

    fun saveNote(note: Note): Note {
        val saved = note.copy(updatedAt = System.currentTimeMillis())
        writableDatabase.insertWithOnConflict("notes", null, saved.toValues(), SQLiteDatabase.CONFLICT_REPLACE)
        return saved
    }

    fun createNote(title: String, content: String, type: String = "rich", sourceUrl: String? = null): Note {
        return saveNote(Note(UUID.randomUUID().toString(), title.ifBlank { "Untitled" }, content, type, sourceUrl))
    }

    fun deleteNote(id: String) {
        val values = ContentValues().apply {
            put("is_deleted", 1)
            put("sync_status", "pending_delete")
            put("updated_at", System.currentTimeMillis())
        }
        writableDatabase.update("notes", values, "id=?", arrayOf(id))
    }

    fun addTask(noteId: String?, title: String): TaskItem {
        val task = TaskItem(UUID.randomUUID().toString(), noteId, title)
        writableDatabase.insert("tasks", null, ContentValues().apply {
            put("id", task.id)
            put("note_id", task.noteId)
            put("title", task.title)
            put("done", 0)
        })
        return task
    }

    fun listTasks(noteId: String? = null): List<TaskItem> {
        val selection = noteId?.let { "note_id=?" }
        val args = noteId?.let { arrayOf(it) }
        return readableDatabase.query("tasks", null, selection, args, null, null, "done ASC").use {
            buildList {
                while (it.moveToNext()) add(
                    TaskItem(
                        id = it.getString(it.getColumnIndexOrThrow("id")),
                        noteId = it.getString(it.getColumnIndexOrThrow("note_id")),
                        title = it.getString(it.getColumnIndexOrThrow("title")),
                        done = it.getInt(it.getColumnIndexOrThrow("done")) == 1,
                        dueAt = if (it.isNull(it.getColumnIndexOrThrow("due_at"))) null else it.getLong(it.getColumnIndexOrThrow("due_at"))
                    )
                )
            }
        }
    }

    fun toggleTask(id: String, done: Boolean) {
        writableDatabase.update("tasks", ContentValues().apply { put("done", if (done) 1 else 0) }, "id=?", arrayOf(id))
    }

    fun addAttachment(item: AttachmentItem) {
        writableDatabase.insertWithOnConflict("attachments", null, ContentValues().apply {
            put("id", item.id)
            put("note_id", item.noteId)
            put("file_name", item.fileName)
            put("mime_type", item.mimeType)
            put("local_path", item.localPath)
            put("extracted_text", item.extractedText)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun listAttachments(noteId: String): List<AttachmentItem> {
        return readableDatabase.query("attachments", null, "note_id=?", arrayOf(noteId), null, null, "file_name ASC").use {
            buildList {
                while (it.moveToNext()) add(
                    AttachmentItem(
                        id = it.getString(it.getColumnIndexOrThrow("id")),
                        noteId = it.getString(it.getColumnIndexOrThrow("note_id")),
                        fileName = it.getString(it.getColumnIndexOrThrow("file_name")),
                        mimeType = it.getString(it.getColumnIndexOrThrow("mime_type")),
                        localPath = it.getString(it.getColumnIndexOrThrow("local_path")),
                        extractedText = it.getString(it.getColumnIndexOrThrow("extracted_text"))
                    )
                )
            }
        }
    }

    private fun cursorToNote(cursor: android.database.Cursor): Note {
        return Note(
            id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
            title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
            content = cursor.getString(cursor.getColumnIndexOrThrow("content")),
            type = cursor.getString(cursor.getColumnIndexOrThrow("type")),
            sourceUrl = cursor.getString(cursor.getColumnIndexOrThrow("source_url")),
            isPinned = cursor.getInt(cursor.getColumnIndexOrThrow("is_pinned")) == 1,
            isArchived = cursor.getInt(cursor.getColumnIndexOrThrow("is_archived")) == 1,
            isDeleted = cursor.getInt(cursor.getColumnIndexOrThrow("is_deleted")) == 1,
            updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow("updated_at")),
            syncStatus = cursor.getString(cursor.getColumnIndexOrThrow("sync_status"))
        )
    }

    private fun Note.toValues(): ContentValues = ContentValues().apply {
        put("id", id)
        put("title", title)
        put("content", content)
        put("type", type)
        put("source_url", sourceUrl)
        put("is_pinned", if (isPinned) 1 else 0)
        put("is_archived", if (isArchived) 1 else 0)
        put("is_deleted", if (isDeleted) 1 else 0)
        put("updated_at", updatedAt)
        put("sync_status", syncStatus)
    }
}
