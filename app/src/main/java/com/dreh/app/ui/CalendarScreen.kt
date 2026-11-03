package com.dreh.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import com.dreh.app.AppState
import com.dreh.app.todayKey
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private const val MONTHS = "JANUARY FEBRUARY MARCH APRIL MAY JUNE JULY AUGUST SEPTEMBER OCTOBER NOVEMBER DECEMBER"

@Composable
fun CalendarScreen(
    app: AppState,
    onBack: () -> Unit,
    onTab: (Tab) -> Unit,
    onAskAi: () -> Unit = {}
) = DesignFrame {
    val say = toast()
    val cal = remember { Calendar.getInstance() }
    val today = todayKey()
    var year by remember { mutableIntStateOf(cal.get(Calendar.YEAR)) }
    var month by remember { mutableIntStateOf(cal.get(Calendar.MONTH)) }
    var selected by remember { mutableStateOf(today) }
    var newTitle by remember { mutableStateOf<String?>(null) }
    var newTime by remember { mutableStateOf<String?>(null) }
    var pendingTitle by remember { mutableStateOf("") }

    val monthName = MONTHS.split(" ")[month.coerceIn(0, 11)]
    val dayCount = remember(year, month) {
        cal.set(Calendar.YEAR, year)
        cal.set(Calendar.MONTH, month)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        val lead = cal.get(Calendar.DAY_OF_WEEK) - 1
        val len = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val out = ArrayList<String?>(42)
        repeat(lead) { out.add(null) }
        for (d in 1..len) out.add(dayKeyOf(cal, d))
        while (out.size < 42) out.add(null)
        out
    }

    fun shift(delta: Int) {
        var m = month + delta
        var y = year
        while (m < 0) { m += 12; y-- }
        while (m > 11) { m -= 12; y++ }
        month = m; year = y
    }

    BackPill2(27f, 60f, onBack)
    FText("CALENDAR", 96f, 58f, 30f, w = 220f, align = TextAlign.Center)

    // ---- month header ----
    FBox(18f, 128f, 40f, 40f, radius = 12f, modifier = Modifier.tap { shift(-1) }) {
        Box(Modifier.at(11f, 11f, 18f, 18f).rotate(90f)) { FImage("chevron_down", 0f, 0f, 18f, 18f) }
    }
    FText("$monthName $year", 78f, 138f, 20f, w = 256f, align = TextAlign.Center, weight = FontWeight.Medium)
    FBox(354f, 128f, 40f, 40f, radius = 12f, modifier = Modifier.tap { shift(1) }) {
        Box(Modifier.at(11f, 11f, 18f, 18f).rotate(270f)) { FImage("chevron_down", 0f, 0f, 18f, 18f) }
    }

    // ---- weekday header ----
    val letters = listOf("S", "M", "T", "W", "T", "F", "S")
    letters.forEachIndexed { i, l ->
        FText(l, colX(i), 186f, 12f, w = 50f, align = TextAlign.Center, color = PlaceholderGray)
    }

    // ---- 6x7 grid ----
    dayCount.forEachIndexed { i, key ->
        val col = i % 7
        val row = i / 7
        val x = colX(col)
        val y = 212f + row * 52f
        val isSelected = key != null && key == selected
        val isToday = key != null && key == today
        val hasEvents = key != null && app.eventsOn(key).isNotEmpty()

        FBox(
            x, y, 50f, 46f, radius = 10f,
            fill = if (isSelected) Ink else Paper,
            border = if (isToday && !isSelected) Ink else if (isSelected) null else null,
            modifier = Modifier.tap { if (key != null) selected = key }
        ) {
            if (key != null) {
                val n = key.substring(8).toInt()
                FText(
                    "$n", 0f, 14f, 14f, w = 50f,
                    align = TextAlign.Center,
                    color = if (isSelected) Paper else Ink
                )
                if (hasEvents) {
                    FBox(22f, 35f, 6f, 6f, radius = 3f, fill = if (isSelected) Paper else Ink, border = null)
                }
            }
        }
    }

    // ---- events on the selected day ----
    val dayEvents = app.eventsOn(selected)
    FText("EVENTS", 18f, 548f, 14f, color = PlaceholderGray)
    FText(selected.substring(8).toInt().toString(), 366f, 548f, 14f, w = 28f, align = TextAlign.End)

    // scrollable list between the header and the fixed New-event bar - no more stacking under the nav
    Column(
        Modifier.at(18f, 572f, 375f, 118f).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(sc(10f))
    ) {
        if (dayEvents.isEmpty()) {
            FText("No events on this day", 0f, 8f, 14f, color = PlaceholderGray)
        } else {
            dayEvents.forEach { e ->
                EventCard(0f, 0f, e.title, e.timeLabel, e.done) {
                    if (app.toggleEvent(e.id)) say(if (app.levelUpPending) "+5 XP - LEVEL UP! LV ${app.level}" else "+5 XP")
                }
            }
        }
    }

    FBox(18f, 700f, 375f, 48f, radius = Unverified.CardRadius, modifier = Modifier.tap { newTitle = "" }) {
        FText("New event", 20f, 15f, 14f)
        FText("+", 334f, 10f, 20f, w = 24f, align = TextAlign.End)
    }

    FBox(255f, 754f, 138f, 40f, radius = 12f, fill = Ink, border = null, modifier = Modifier.tap(onAskAi)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Ask Dr. Eh", color = Paper, fontSize = ssp(13f), fontFamily = DrEhFont, softWrap = false)
        }
    }

    BottomNav(Tab.HOME, onTab)

    newTitle?.let { t ->
        NameDialog("New event", t, onDismiss = { newTitle = null }) {
            newTitle = null
            newTime = ""
            pendingTitle = it
        }
    }
    newTime?.let {
        TimeDialog(onDismiss = { newTime = null }) { t ->
            app.addEvent(selected, pendingTitle, t)
            pendingTitle = ""
            newTime = null
        }
    }
}



/** "Rectangle 71" + "chevron-down 2": back control (same pill as ReviewerScreens). */
@Composable
private fun BackPill2(x: Float, y: Float, onBack: () -> Unit) {
    FBox(x, y, 60f, 42f, modifier = Modifier.tap(onBack)) {
        Box(Modifier.at(12f, 2f, 37f, 37f).rotate(90f)) { FImage("chevron_down", 0f, 0f, 37f, 37f) }
    }
}

private fun colX(col: Int) = 18f + col * 54f

@Composable
private fun EventCard(x: Float, y: Float, title: String, timeLabel: String?, done: Boolean, onToggle: () -> Unit) {
    FBox(x, y, 375f, 48f, radius = Unverified.CardRadius, modifier = Modifier.tap(onToggle)) {
        Text(
            text = title,
            color = if (done) PlaceholderGray else Ink,
            fontSize = ssp(14f),
            fontFamily = DrEhFont,
            softWrap = false,
            textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None,
            modifier = Modifier.at(20f, if (timeLabel != null) 6f else 16f)
        )
        if (timeLabel != null) {
            FText(timeLabel, 20f, 27f, 12f, color = PlaceholderGray)
        }
        FCheck(done, 340f, 20f, 10f, onToggle)
    }
}

private fun dayKeyOf(cal: Calendar, day: Int): String {
    val c = cal.clone() as Calendar
    c.set(Calendar.DAY_OF_MONTH, day)
    return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.time)
}

@Composable
private fun TimeDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var v by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Paper,
        title = { Text("Time", fontFamily = DrEhFont, color = Ink) },
        text = {
            BasicTextField(
                value = v, onValueChange = { v = it }, singleLine = true,
                textStyle = TextStyle(color = Ink, fontSize = ssp(14f), fontFamily = DrEhFont),
                cursorBrush = SolidColor(Ink),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                decorationBox = { inner ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                        if (v.isEmpty()) Text(
                            "09:00", color = PlaceholderGray, fontSize = ssp(14f),
                            fontFamily = DrEhFont, softWrap = false
                        )
                        inner()
                    }
                }
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(v.trim()) }) { Text("OK", fontFamily = DrEhFont, color = Ink) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL", fontFamily = DrEhFont, color = Ink) } }
    )
}
