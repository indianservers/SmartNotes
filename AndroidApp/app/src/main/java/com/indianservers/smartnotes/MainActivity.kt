package com.indianservers.smartnotes

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.text.DateFormat

class MainActivity : AppCompatActivity() {
    private lateinit var db: SmartNotesDb
    private lateinit var attachmentStore: AttachmentStore
    private lateinit var syncClient: SyncClient
    private lateinit var list: LinearLayout
    private lateinit var search: EditText
    private var activeNote: Note? = null

    private val pickAttachment = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        val note = activeNote ?: return@registerForActivityResult
        if (uri != null) {
            val item = attachmentStore.importUri(note.id, uri)
            db.addAttachment(item)
            openEditor(note.id)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = SmartNotesDb(this)
        attachmentStore = AttachmentStore(this)
        syncClient = SyncClient(this)
        handleShareIntent(intent)
        showHome()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
        showHome()
    }

    private fun handleShareIntent(intent: Intent) {
        if (intent.action != Intent.ACTION_SEND) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
        val title = intent.getStringExtra(Intent.EXTRA_TITLE) ?: "Web clip"
        val stream = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        if (!text.isNullOrBlank()) {
            db.createNote(title, text, "webclip", text.takeIf { it.startsWith("http") })
        } else if (stream != null) {
            val note = db.createNote("Shared attachment", "", "file")
            db.addAttachment(attachmentStore.importUri(note.id, stream))
        }
    }

    private fun showHome() {
        activeNote = null
        val root = vertical()
        val header = horizontal().apply {
            setPadding(dp(16), dp(14), dp(16), dp(8))
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        TextView(this).apply {
            text = "Smart Notes"
            textSize = 22f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            header.addView(this, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        Button(this).apply {
            text = "Sync"
            setOnClickListener { syncAll() }
            header.addView(this)
        }
        root.addView(header)

        search = EditText(this).apply {
            hint = "Search notes, PDFs, images, attachments"
            setSingleLine(true)
            setPadding(dp(16), 0, dp(16), 0)
            setOnEditorActionListener { _, _, _ -> refreshList(); hideKeyboard(); true }
        }
        root.addView(search)

        val scroll = ScrollView(this)
        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(96))
        }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val fab = FloatingActionButton(this).apply {
            setImageResource(android.R.drawable.ic_input_add)
            setOnClickListener { openEditor(db.createNote("", "").id) }
        }
        val frame = FrameLayout(this)
        frame.addView(root)
        frame.addView(fab, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, android.view.Gravity.BOTTOM or android.view.Gravity.END).apply {
            setMargins(0, 0, dp(20), dp(20))
        })
        setContentView(frame)
        refreshList()
    }

    private fun refreshList() {
        list.removeAllViews()
        db.listNotes(search.text.toString()).forEach { note ->
            list.addView(noteRow(note))
        }
    }

    private fun noteRow(note: Note): View {
        return vertical().apply {
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(0xFF1C1C22.toInt())
            }
            val title = TextView(context).apply {
                text = note.title.ifBlank { "Untitled" }
                textSize = 16f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
            val body = TextView(context).apply {
                text = note.content.take(160)
                textSize = 13f
                setTextColor(0xFFB8B8C2.toInt())
            }
            val meta = TextView(context).apply {
                text = "${note.type} · ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(note.updatedAt)}"
                textSize = 11f
                setTextColor(0xFF8B8B96.toInt())
            }
            addView(title)
            if (note.content.isNotBlank()) addView(body)
            addView(meta)
            setOnClickListener { openEditor(note.id) }
            (layoutParams as? LinearLayout.LayoutParams)?.setMargins(0, 0, 0, dp(8))
        }.also {
            it.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, dp(8))
            }
        }
    }

    private fun openEditor(noteId: String) {
        val note = db.getNote(noteId) ?: return
        activeNote = note
        val root = vertical().apply { setPadding(dp(16), dp(12), dp(16), dp(12)) }
        val top = horizontal()
        Button(this).apply {
            text = "Back"
            setOnClickListener { showHome() }
            top.addView(this)
        }
        Button(this).apply {
            text = "Attach"
            setOnClickListener { pickAttachment.launch("*/*") }
            top.addView(this)
        }
        Button(this).apply {
            text = "Task"
            setOnClickListener { addTaskDialog(note.id) }
            top.addView(this)
        }
        Button(this).apply {
            text = "Share"
            setOnClickListener { shareDialog(note) }
            top.addView(this)
        }
        root.addView(top)

        val title = EditText(this).apply {
            hint = "Title"
            textSize = 22f
            setText(note.title)
        }
        val content = EditText(this).apply {
            hint = "Start writing"
            minLines = 10
            gravity = android.view.Gravity.TOP
            setText(note.content)
        }
        root.addView(title)
        root.addView(content, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val details = TextView(this).apply {
            text = buildString {
                val tasks = db.listTasks(note.id)
                val attachments = db.listAttachments(note.id)
                if (tasks.isNotEmpty()) append("Tasks\n").append(tasks.joinToString("\n") { if (it.done) "✓ ${it.title}" else "□ ${it.title}" }).append("\n\n")
                if (attachments.isNotEmpty()) append("Attachments\n").append(attachments.joinToString("\n") { "• ${it.fileName}" })
            }
            setTextColor(0xFFB8B8C2.toInt())
        }
        root.addView(details)

        Button(this).apply {
            text = "Save"
            setOnClickListener {
                db.saveNote(note.copy(title = title.text.toString(), content = content.text.toString(), syncStatus = "pending_update"))
                Toast.makeText(this@MainActivity, "Saved offline", Toast.LENGTH_SHORT).show()
                openEditor(note.id)
            }
            root.addView(this)
        }
        setContentView(root)
    }

    private fun addTaskDialog(noteId: String) {
        val input = EditText(this).apply { hint = "Task title" }
        AlertDialog.Builder(this)
            .setTitle("Add task")
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                db.addTask(noteId, input.text.toString())
                openEditor(noteId)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun shareDialog(note: Note) {
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, note.title)
            putExtra(Intent.EXTRA_TEXT, "${note.title}\n\n${note.content}")
        }
        startActivity(Intent.createChooser(share, "Share note"))
    }

    private fun syncAll() {
        Thread {
            val ok = db.listNotes().all { note -> note.syncStatus == "synced" || syncClient.pushNote(note) }
            runOnUiThread { Toast.makeText(this, if (ok) "Sync attempted" else "Some notes did not sync", Toast.LENGTH_SHORT).show() }
        }.start()
    }

    private fun vertical() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(0xFF0C0C10.toInt())
    }

    private fun horizontal() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun hideKeyboard() {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(search.windowToken, 0)
    }
}
