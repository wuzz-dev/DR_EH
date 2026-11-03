package com.dreh.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import com.dreh.app.AppState

private data class Row(val id: Long, val name: String)

/**
 * Frames "REVIEWER", "REVIEWER-withOUT content", "REVIEWER-with content", "REVIEWER- with content".
 * folderId == null -> list of folders ("Create a folder"); otherwise the files inside that folder ("Create a file").
 */
@Composable
fun ReviewerScreen(
    app: AppState, folderId: Long?, onBack: () -> Unit,
    onOpenFolder: (Long) -> Unit, onOpenFile: (Long, Long) -> Unit, onTab: (Tab) -> Unit
) = DesignFrame {
    val say = toast()
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Long?>(null) }

    val rows: List<Row> =
        if (folderId == null) app.folders.map { Row(it.id, it.name) }
        else app.folder(folderId)?.files.orEmpty().map { Row(it.id, it.name) }

    FText("REVIEWER", 96f, 58f, 30f, w = 220f, align = TextAlign.Center)
    BackPill(27f, 60f, onBack)

    // "Create a folder" / "Create a file"
    val createLabel = if (folderId == null) "Create a folder" else "Create a file"
    FBox(18f, 136f, 375f, 106f, radius = Unverified.CardRadius, modifier = Modifier.tap { creating = true }) {
        FText(createLabel, 123f, 26f, 14f)
        FText("+", 174f, 36f, 48f)
    }

    Column(
        Modifier.at(18f, 263f, 375f, 535f).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(sc(21f))
    ) {
        rows.forEach { r ->
            val shape = RoundedCornerShape(sc(Unverified.CardRadius))
            Box(
                Modifier.fillMaxWidth().height(sc(106f)).clip(shape).background(Paper, shape)
                    .border(sc(1f), Ink, shape)
                    .tap { if (folderId == null) onOpenFolder(r.id) else onOpenFile(folderId, r.id) }
            ) {
                FText(r.name, 26f, 36f, 24f)
                FImage("edit_1", 284f, 41f, 24f, 24f, onClick = { renaming = r.id })
                FImage("trash_1", 321f, 41f, 24f, 24f, onClick = {
                    if (folderId == null) app.deleteFolder(r.id) else app.deleteFile(folderId, r.id)
                })
            }
        }
        Spacer(Modifier.height(sc(120f)))
    }

    BottomNav(Tab.TASKS, onTab)

    if (creating) NameDialog(createLabel, onDismiss = { creating = false }) {
        val made = if (folderId == null) { app.createFolder(it); false } else app.createFile(folderId, it)
        if (made) say(if (app.levelUpPending) "+15 XP - LEVEL UP! LV ${app.level}" else "+15 XP")
        creating = false
    }
    renaming?.let { id ->
        NameDialog("Rename", rows.firstOrNull { it.id == id }?.name.orEmpty(), onDismiss = { renaming = null }) {
            if (folderId == null) app.renameFolder(id, it) else app.renameFile(folderId, id, it)
            renaming = null
        }
    }
}

// ---------------------------------------------------------------- frame "inside note pad"
@Composable
fun NoteScreen(app: AppState, folderId: Long, fileId: Long, onBack: () -> Unit) = DesignFrame {
    val file = app.file(folderId, fileId)
    var text by remember { mutableStateOf(file?.text.orEmpty()) }
    val undo = remember { mutableStateListOf<String>() }
    val redo = remember { mutableStateListOf<String>() }

    fun change(new: String) {
        if (new != text) { undo.add(text); redo.clear(); text = new; app.setFileText(folderId, fileId, new) }
    }

    BackPill(39f, 60f, onBack)

    // undo / redo pills
    FBox(250f, 59f, 61f, 42f, modifier = Modifier.tap {
        if (undo.isNotEmpty()) { redo.add(text); text = undo.removeAt(undo.lastIndex); app.setFileText(folderId, fileId, text) }
    }) { FImage("undo", 21f, 9f, 24f, 24f) }
    FBox(335f, 59f, 61f, 42f, modifier = Modifier.tap {
        if (redo.isNotEmpty()) { undo.add(text); text = redo.removeAt(redo.lastIndex); app.setFileText(folderId, fileId, text) }
    }) { FImage("redo", 18f, 9f, 24f, 24f) }

    FText(file?.name.orEmpty(), 38f, 136f, 36f, w = 340f)

    BasicTextField(
        value = text, onValueChange = ::change,
        textStyle = TextStyle(color = Ink, fontSize = ssp(20f), fontFamily = DrEhFont),
        cursorBrush = SolidColor(Ink),
        modifier = Modifier.at(39f, 198f, 334f, 590f),
        decorationBox = { inner ->
            Box {
                if (text.isEmpty()) Text("Note something down", color = PlaceholderGray, fontSize = ssp(20f), fontFamily = DrEhFont)
                inner()
            }
        }
    )

    // bottom toolbar (Rectangle 75)
    FBox(103f, 813f, 206f, 54f) {
        FText("Aa", 21f, 10f, 24f)
        FImage("camera_alt", 70f, 16f, 27f, 27f)
        FImage("highlight", 114f, 16f, 28f, 27f)
        FImage("check_circle", 159f, 16f, 27f, 27f, onClick = onBack)
    }
}
