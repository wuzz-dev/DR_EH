package com.dreh.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.dreh.app.AppState
import com.dreh.app.PetState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// ---------------------------------------------------------------- frames neutral / happy / tired / sick / dead
@Composable
fun HomeScreen(app: AppState, onTab: (Tab) -> Unit, onCalendar: () -> Unit, onMakeReviewer: () -> Unit,
               onAskAi: () -> Unit = {}) = DesignFrame {
    val pet = app.pet

    // animated companion: mood gif + bin, breathing, crossfades, tap-to-cheer, XP floats
    CompanionStage(app)
    FImage("cal_1", 281f, 682f, 112f, 112f, onClick = onCalendar)
    BottomNav(Tab.HOME, onTab)

    if (pet == PetState.DEAD) {
        FBox(-9f, 0f, 424f, 917f, radius = 0f, fill = Unverified.DeadVeil, border = null)
        FImage("tombstone_1", 6f, 148f, 400f, 400f, ContentScale.Crop)
        FText("Restore it by making a reviewer...", 77f, 557f, 20f, w = 258f)
        FBox(92f, 643f, 224f, 39f, modifier = Modifier.tap(onMakeReviewer)) {
            FText("Make a reviewer", 21f, 3f, 20f)
        }
    }

    StatsHeader(app.health, app.energy, app.mood, app.hunger)
    ExpTile(app.exp)
    // tiny caption tying the EXP bar to the level it fills up - tucked under the tile,
    // inside its 47f column, so it never floats off into the status-bar area
    FText("LV ${app.level}", 302f, 119f, 11f, w = 47f, align = TextAlign.Center)

    // the companion talks about his bars / your tasks
    SpeechBubble(app)
    // the "+n XP" feedback rising from him
    XpFloat(app)

    // entry point to the AI flow
    FBox(137f, 666f, 138f, 40f, radius = 12f, fill = Ink, border = null, modifier = Modifier.tap(onAskAi)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Ask Dr. Eh", color = Paper, fontSize = ssp(14f), fontFamily = DrEhFont, softWrap = false)
        }
    }

    // full-frame "LEVEL UP!" splash (tap to continue), drawn above everything
    LevelUpOverlay(app)
}

// ---------------------------------------------------------------- frames "task - ..." / "add (no gclass" / "task- completed"
@Composable
fun TasksScreen(app: AppState, gclass: com.dreh.app.gclass.GClassStore, onTab: (Tab) -> Unit, onNotes: () -> Unit) = DesignFrame {
    val say = toast()
    val scope = rememberCoroutineScope()
    var showCompleted by remember { mutableStateOf(false) }
    var detailId by remember { mutableStateOf<Long?>(null) }
    var adding by remember { mutableStateOf(false) }
    var gclassOpen by remember { mutableStateOf(false) }
    var syncing by remember { mutableStateOf(false) }
    var linksRev by remember { mutableIntStateOf(0) }   // bumped when links change -> recompose
    val context = LocalContext.current
    val linked = remember(linksRev) { gclass.links().isNotEmpty() || gclass.deviceCalendars().isNotEmpty() }
    val deviceAllowed = remember(linksRev) { com.dreh.app.gclass.DeviceCalendar.canRead(context) }

    val active = app.tasks.filter { !it.done }
    val done = app.tasks.filter { it.done }

    FText("TASK LIST", 91f, 58f, 30f, w = 230f, align = TextAlign.Center)

    // AUTO gclass side: one passive permission ask per install, then a throttled sync on entry
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        gclass.markAsked()
        if (granted) {
            scope.launch {
                syncing = true
                try {
                    // Keep the one-tap setup; the selected calendars can be refined any time.
                    val found = com.dreh.app.gclass.DeviceCalendar.classCalendars(context)
                    val savedSelection = gclass.deviceCalendars()
                    val foundIds = found.map { it.id }.toSet()
                    if (found.isNotEmpty()) {
                        gclass.setDeviceCalendars(
                            if (savedSelection.isEmpty()) foundIds else savedSelection.intersect(foundIds)
                        )
                    }
                    linksRev++
                    if ((found.isEmpty() || gclass.deviceCalendars().isEmpty()) && gclass.links().isEmpty()) {
                        gclassOpen = true
                        say("Choose a class calendar or add a calendar link to start syncing.")
                    } else {
                        gclass.markAutoAttempt()
                        val r = com.dreh.app.gclass.GClassSync.sync(gclass, app, context)
                        if (r.succeeded) gclass.markAutoSync()
                        linksRev++
                        say(r.message)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    say("Calendar sync failed. Open sync settings and try again.")
                } finally {
                    syncing = false
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        if (!gclass.askedOnce() && !linked) perm.launch(android.Manifest.permission.READ_CALENDAR)
        else if (linked && gclass.autoSyncDue()) {
            gclass.markAutoAttempt()
            try {
                val r = com.dreh.app.gclass.GClassSync.sync(gclass, app, context)
                if (r.succeeded) gclass.markAutoSync()
                linksRev++
                if (r.added > 0 || r.updated > 0 || r.error != null) say(r.message)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (linked) say("Calendar sync failed. Open sync settings and try again.")
            }
        }
    }

    // Keep calendar management reachable after linking; the refresh control stays separate.
    FBox(27f, 60f, 60f, 42f, modifier = Modifier.tap { gclassOpen = true }) {
        FImage("chevron_down", 48f, 2f, 37f, 37f)
    }
    if (linked) {
        FBox(335f, 53f, 39f, 27f, radius = 8f, modifier = Modifier.tap {
            if (!syncing) {
                syncing = true
                scope.launch {
                    gclass.markAutoAttempt()
                    try {
                        val r = com.dreh.app.gclass.GClassSync.sync(gclass, app, context)
                        if (r.succeeded) gclass.markAutoSync()
                        linksRev++
                        say(r.message)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        say("Calendar sync failed. Check your connection and try again.")
                    } finally {
                        syncing = false
                    }
                }
            }
        }) {
            if (syncing) FText("…", 9f, 0f, 22f, w = 22f, align = TextAlign.Center)
            else FImage("undo", 7f, 1f, 24f, 24f)
        }
    }
    FText(
        when {
            deviceAllowed && gclass.deviceCalendars().isNotEmpty() ->
                "Phone calendars: ${gclass.deviceCalendars().size} selected · tap to manage"
            gclass.deviceCalendars().isNotEmpty() -> "Calendar access off · tap to reconnect"
            gclass.links().isNotEmpty() -> "Calendar linked · tap to manage or sync"
            deviceAllowed -> "Calendar access on · choose calendars"
            else -> "Connect a calendar to import class tasks"
        },
        62f, 98f, 11f, w = 288f, align = TextAlign.Center, color = PlaceholderGray, maxLines = 1,
        modifier = Modifier.tap { gclassOpen = true },
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)

    // Today / Completed tabs (selected = filled)
    FBox(27f, 119f, 111f, 37f, fill = if (!showCompleted) Ink else Paper, modifier = Modifier.tap { showCompleted = false }) {
        FText("Today", 30f, 7f, 16f, color = if (!showCompleted) Paper else Ink)
    }
    FBox(144f, 119f, 111f, 37f, fill = if (showCompleted) Ink else Paper, modifier = Modifier.tap { showCompleted = true }) {
        FText("Completed", 11f, 7f, 16f, color = if (showCompleted) Paper else Ink)
    }

    // Keep imported coursework visible if its calendar is later disconnected.
    val visible = if (showCompleted) done else active

    Column(
        Modifier.at(18f, 181f, 375f, 617f).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(sc(15f))
    ) {
        if (!showCompleted) {
            TaskCardShell(106f, Modifier.tap { adding = true }) {
                FText("Add a task", 145f, 26f, 14f)
                FText("+", 174f, 36f, 48f)
            }
        }
        if (visible.isEmpty()) {
            TaskCardShell(88f) {
                val message = if (showCompleted) "Nothing completed yet" else "No tasks for today"
                val detail = if (showCompleted) "Finish a task and it will appear here" else "Add a task to get started"
                FText(message, 22f, 21f, 16f, w = 320f, align = TextAlign.Center)
                FText(detail, 22f, 49f, 12f, w = 320f, align = TextAlign.Center, color = PlaceholderGray)
            }
        } else {
            visible.forEach { t ->
                if (t.fromClassroom && !t.done) {
                    TaskCardShell(129f, Modifier.tap { detailId = t.id }) {
                        FText(t.title, 27f, 14f, 18f, w = 270f, maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        FText(t.dueLabel ?: "", 27f, 64f, 13f, w = 270f, maxLines = 1,
                            color = PlaceholderGray, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        FText(t.detail ?: "", 27f, 87f, 12f, w = 270f, maxLines = 1,
                            color = PlaceholderGray, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        FImage("chevron_down", 330f, 82f, 35f, 35f)
                        // the same checkbox the plain cards have - classroom tasks are completable too
                        FBox(322f, 42f, 22f, 22f, radius = 4f, modifier = Modifier.tap {
                            val msg = app.completeTask(t.id)
                            if (msg.isNotBlank()) say(msg)
                        })
                    }
                } else if (t.fromClassroom) {
                    TaskCardShell(106f, Modifier.tap { detailId = t.id }) {
                        FText(t.title, 27f, 18f, 18f, w = 270f, maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        FText("Turned in", 27f, 66f, 13f, color = PlaceholderGray)
                        FImage("chevron_down", 312f, 72f, 35f, 35f)
                    }
                } else {
                    TaskCardShell(106f, Modifier.tap { detailId = t.id }) {
                        FText(t.title, 27f, 30f, 17f, w = 274f, maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        FBox(322f, 42f, 22f, 22f, radius = 4f, modifier = Modifier.tap {
                            val msg = app.completeTask(t.id)
                            if (msg.isNotBlank()) say(msg)
                        })
                        if (t.done) FImage("check", 321f, 41f, 24f, 24f)
                    }
                }
            }
        }
        Spacer(Modifier.height(sc(120f)))
    }

    // tap a task card -> its detail sheet (works for plain and classroom tasks, done or not)
    detailId?.let { id ->
        app.tasks.firstOrNull { it.id == id }?.let { t -> TaskDetailSheet(app, t, say) { detailId = null } }
    }

    FImage("note_1", 322f, 695f, 67f, 85f, onClick = onNotes)
    BottomNav(Tab.TASKS, onTab)

    if (adding) NameDialog("Add a task", onDismiss = { adding = false }) { app.addTask(it); adding = false }
    if (gclassOpen) GClassDialog(app, gclass, onDone = { linksRev++ }, onDismiss = { gclassOpen = false })
}

/**
 * Same dialog style as NameDialog/SettingsDialog. AUTO half: pick which class calendars on this
 * phone feed the task list (Android mirrors Classroom's calendars locally). Backup half: paste a
 * "secret iCal address" for when the account lives on a desktop only.
 */
@Composable
private fun GClassDialog(app: AppState, store: com.dreh.app.gclass.GClassStore,
                         onDone: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var url by remember { mutableStateOf("") }
    var rev by remember { mutableStateOf(0) }
    var syncing by remember { mutableStateOf(false) }
    var syncError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val say = toast()
    val savedLinks = remember(rev) { store.links() }

    fun runSync() {
        if (syncing) return
        syncing = true
        syncError = null
        scope.launch {
            store.markAutoAttempt()
            try {
                val r = com.dreh.app.gclass.GClassSync.sync(store, app, context)
                if (r.succeeded) store.markAutoSync()
                onDone()
                rev++
                syncError = r.error
                say(r.message)
                if (r.succeeded) onDismiss()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                syncError = "Check the internet, calendar access, and saved links, then try again."
                say("Calendar sync failed. Check settings and try again.")
            } finally {
                syncing = false
            }
        }
    }

    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val found = com.dreh.app.gclass.DeviceCalendar.classCalendars(context)
            val savedSelection = store.deviceCalendars()
            val foundIds = found.map { it.id }.toSet()
            if (found.isNotEmpty()) {
                store.setDeviceCalendars(if (savedSelection.isEmpty()) foundIds else savedSelection.intersect(foundIds))
            }
            rev++; onDone()
            if (found.isEmpty()) {
                syncError = "No class calendars were found on this phone. Open Google Calendar and sync the class calendars, or add an iCal link."
            } else runSync()
        } else {
            say("Calendar access is needed for auto-sync")
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Paper,
        title = { Text("LINK GOOGLE CLASSROOM", fontFamily = DrEhFont, color = Ink) },
        text = {
            Column(Modifier.heightIn(max = sc(470f)).verticalScroll(rememberScrollState())) {
                // ---- AUTO: the phone's own calendar store ----
                Text("FROM THIS PHONE (AUTO)", fontFamily = DrEhFont, color = Ink, fontSize = ssp(12f))
                if (!com.dreh.app.gclass.DeviceCalendar.canRead(context)) {
                    Text("allow Dr. Eh to read the calendars your Google account already synced here",
                        fontFamily = DrEhFont, color = PlaceholderGray, fontSize = ssp(10f))
                    TextButton(onClick = { perm.launch(android.Manifest.permission.READ_CALENDAR) }) {
                        Text("ALLOW CALENDAR ACCESS", fontFamily = DrEhFont, color = Ink)
                    }
                } else {
                    val cals = remember(rev) {
                        runCatching { com.dreh.app.gclass.DeviceCalendar.classCalendars(context) }
                            .getOrDefault(emptyList())
                    }
                    val selected = remember(rev) { store.deviceCalendars() }
                    if (cals.isEmpty()) {
                        Text("no class calendars on this phone yet - open the Google Calendar app once, then come back",
                            fontFamily = DrEhFont, color = PlaceholderGray, fontSize = ssp(10f))
                        if (selected.isNotEmpty()) {
                            TextButton(onClick = {
                                store.setDeviceCalendars(emptySet())
                                rev++; onDone()
                            }) {
                                Text("CLEAR UNAVAILABLE CALENDAR SELECTION", fontFamily = DrEhFont, color = Ink)
                            }
                        }
                    } else {
                        cals.forEach { cal ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                androidx.compose.material3.Checkbox(
                                    checked = cal.id in selected,
                                    onCheckedChange = { on ->
                                        val availableIds = cals.map { it.id }.toSet()
                                        val next = selected.intersect(availableIds).toMutableSet()
                                        if (on) next.add(cal.id) else next.remove(cal.id)
                                        store.setDeviceCalendars(next)
                                        rev++; onDone()
                                    },
                                    colors = androidx.compose.material3.CheckboxDefaults.colors(
                                        checkedColor = Ink, checkmarkColor = Paper, uncheckedColor = Ink
                                    )
                                )
                                Text(cal.name, fontFamily = DrEhFont, color = Ink,
                                    fontSize = ssp(11f), modifier = Modifier.padding(start = sc(4f)))
                            }
                        }
                    }
                    TextButton(onClick = { runSync() }, enabled = !syncing) {
                        Text(if (syncing) "SYNCING PHONE CALENDARS…" else "SYNC FROM PHONE NOW",
                            fontFamily = DrEhFont, color = Ink)
                    }
                }

                // ---- backup: paste the secret iCal link (desktop-only accounts) ----
                Spacer(Modifier.height(sc(14f)))
                Text("OR PASTE A SECRET ICAL LINK", fontFamily = DrEhFont, color = Ink, fontSize = ssp(12f))
                Text(
                    "any browser: calendar.google.com > your class calendar > Settings > Integrate " +
                        "calendar > copy the \"Secret address in iCal format\"",
                    fontFamily = DrEhFont, color = PlaceholderGray, fontSize = ssp(10f)
                )
                OutlinedTextField(
                    value = url, onValueChange = { url = it }, singleLine = true,
                    textStyle = TextStyle(fontFamily = DrEhFont, fontSize = ssp(12f)),
                    placeholder = { Text("paste the secret iCal link...", fontFamily = DrEhFont, fontSize = ssp(12f)) }
                )
                if (savedLinks.isNotEmpty()) {
                    Spacer(Modifier.height(sc(8f)))
                    Text("SAVED CLASS CALENDARS · tap one to remove", fontFamily = DrEhFont,
                        color = PlaceholderGray, fontSize = ssp(10f))
                    savedLinks.forEachIndexed { index, link ->
                        Text(
                            "Calendar ${index + 1}  ·  remove",
                            fontFamily = DrEhFont, color = Ink, fontSize = ssp(11f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { store.removeLink(link); rev++; syncError = null; onDone() }
                                .padding(vertical = sc(4f))
                        )
                    }
                }
                if (syncError != null) {
                    Spacer(Modifier.height(sc(8f)))
                    Text(syncError.orEmpty(), fontFamily = DrEhFont, color = Ink, fontSize = ssp(11f))
                }
                Spacer(Modifier.height(sc(8f)))
                Text("Sync imports calendar events and due dates. Marking a task done stays in Dr. Eh.",
                    fontFamily = DrEhFont, color = PlaceholderGray, fontSize = ssp(10f))
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (url.isBlank() && savedLinks.isNotEmpty()) runSync()
                    else if (store.addLink(url)) {
                        url = ""
                        rev++
                        onDone()
                        runSync()
                    } else {
                        syncError = "Paste a Google Calendar secret iCal address that ends in .ics."
                        say("That is not a Google iCal link")
                    }
                },
                enabled = !syncing
            ) {
                Text(
                    when {
                        syncing -> "SYNCING…"
                        url.isBlank() && savedLinks.isNotEmpty() -> "SYNC LINKED CALENDARS"
                        else -> "ADD + SYNC"
                    },
                    fontFamily = DrEhFont, color = Ink, fontSize = ssp(12f)
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("CLOSE", fontFamily = DrEhFont, color = Ink) }
        }
    )
}

/** Detail sheet for any task card - classroom or plain, open or done. Mark done / delete / close. */
@Composable
private fun TaskDetailSheet(app: AppState, t: com.dreh.app.TaskItem, say: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Paper,
        title = { Text(t.title, fontFamily = DrEhFont, color = Ink) },
        text = {
            Column {
                Text(if (t.done) "STATUS: done" else "STATUS: to do",
                    fontFamily = DrEhFont, color = PlaceholderGray, fontSize = ssp(12f))
                if (t.dueLabel != null) Spacer(Modifier.height(sc(8f)))
                t.dueLabel?.let { Text(it, fontFamily = DrEhFont, color = Ink, fontSize = ssp(14f)) }
                if (t.detail != null) Spacer(Modifier.height(sc(4f)))
                t.detail?.let { Text(it, fontFamily = DrEhFont, color = PlaceholderGray, fontSize = ssp(12f)) }
                Spacer(Modifier.height(sc(8f)))
                Text("SOURCE: " + if (t.fromClassroom) "Google Classroom" else "added by you",
                    fontFamily = DrEhFont, color = PlaceholderGray, fontSize = ssp(11f))
                Spacer(Modifier.height(sc(14f)))
                Row(horizontalArrangement = Arrangement.spacedBy(sc(10f))) {
                    if (!t.done) {
                        FBox(0f, 0f, 118f, 36f, radius = 8f, fill = Ink, modifier = Modifier.tap {
                            val msg = app.completeTask(t.id)
                            if (msg.isNotBlank()) say(msg)
                            onDismiss()
                        }) {
                            Text("Mark done", color = Paper, fontFamily = DrEhFont, fontSize = ssp(12f),
                                modifier = Modifier.align(Alignment.Center))
                        }
                    }
                    FBox(0f, 8f, 84f, 36f, radius = 8f, modifier = Modifier.tap {
                        app.deleteTask(t.id); say("Task deleted"); onDismiss()
                    }) {
                        Text("Delete", fontFamily = DrEhFont, fontSize = ssp(12f),
                            modifier = Modifier.align(Alignment.Center))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("CLOSE", fontFamily = DrEhFont, color = Ink) }
        }
    )
}

@Composable
private fun TaskCardShell(height: Float, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val shape = RoundedCornerShape(sc(Unverified.CardRadius))
    Box(
        Modifier.fillMaxWidth().height(sc(height)).clip(shape).background(Paper, shape)
            .border(sc(1f), Ink, shape).then(modifier),
        content = content
    )
}

// ---------------------------------------------------------------- frame "rewards" - XP redeemables + coin shop
@Composable
fun RewardsScreen(app: AppState, onTab: (Tab) -> Unit) = DesignFrame {
    val say = toast()
    FText("REWARDS", 96f, 58f, 30f, w = 220f, align = TextAlign.Center)
    // live coin balance while you shop
    FText("c ${app.coins}", 300f, 66f, 14f, w = 94f, align = TextAlign.End, weight = FontWeight.Medium)

    // everything in one scroll: lifetime-XP redeemables on top, coin shop below
    Column(
        Modifier.at(18f, 128f, 375f, 650f).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(sc(10f))
    ) {
        FText("REDEEM WITH XP", 0f, 6f, 13f, color = PlaceholderGray)
        app.xpGates.forEachIndexed { i, gate ->
            val unlocked = app.rewardUnlocked(i)
            val claimed = i in app.claimedRewards
            FBox(0f, 0f, 375f, 84f, radius = Unverified.CardRadius,
                fill = if (unlocked) Paper else Unverified.LockedFill,
                modifier = if (unlocked && !claimed) Modifier.tap { if (app.claimReward(i)) say(app.rewardEffects[i] + " - +10 coins!") } else Modifier) {
                // NOTE: content inside an FBox is placed in the box's own coordinates
                if (unlocked) {
                    FText(app.rewardNames[i], 20f, 12f, 18f)
                    FText(if (claimed) "Claimed" else app.rewardEffects[i], 20f, 42f,
                        if (claimed) 16f else 12f, w = 240f, maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        color = if (claimed) PlaceholderGray else Ink)
                    FText(if (claimed) "CLAIMED" else "TAP TO CLAIM · ${gate}+ XP", 20f, 62f, 11f, color = PlaceholderGray)
                    FImage("reward_1", 308f, 16f, 56f, 52f)
                } else {
                    FText("Unlock for $gate+ xp", 20f, 22f, 16f, w = 240f)
                    FText("Your lifetime XP: ${app.totalXp}", 20f, 50f, 11f, color = PlaceholderGray)
                    FImage("reward_1", 308f, 16f, 56f, 52f)
                }
            }
        }

        FText("SHOP - SPEND YOUR COINS", 0f, 46f, 13f, color = PlaceholderGray)
        Spacer(Modifier.height(sc(2f)))
        app.shopCosts.forEachIndexed { i, cost ->
            val affordable = app.coins >= cost
            FBox(0f, 0f, 375f, 74f, radius = Unverified.CardRadius,
                modifier = Modifier.tap {
                    val msg = app.buyPerk(i)
                    say(msg ?: "Not enough coins - need $cost c (${app.coins} in wallet)")
                }) {
                FText(app.shopNames[i], 20f, 10f, 18f)
                FText(app.shopDescs[i], 20f, 38f, 12f, w = 250f, color = PlaceholderGray)
                FBox(300f, 20f, 62f, 34f, radius = 10f, fill = if (affordable) Ink else Paper) {
                    Text("$cost c", color = if (affordable) Paper else Ink,
                        fontSize = ssp(12f), fontFamily = DrEhFont,
                        modifier = Modifier.align(Alignment.Center))
                }
            }
        }
        Spacer(Modifier.height(sc(140f)))   // clears the bottom nav at full scroll
    }
    BottomNav(Tab.REWARDS, onTab)
}

// ---------------------------------------------------------------- frame "profile"
@Composable
fun ProfileScreen(app: AppState, onTab: (Tab) -> Unit, onEdit: () -> Unit, onLogOut: () -> Unit) = DesignFrame {
    var settings by remember { mutableStateOf(false) }
    FText("PROFILE", 96f, 58f, 30f, w = 220f, align = TextAlign.Center)

    // ---- identity card: the app's own Dr. Eh logo stamps the profile, stats under it ----
    FBox(25f, 140f, 363f, 377f, radius = Unverified.CardRadius)
    FImage("prof_2", 49f, 167f, 83f, 83f, ContentScale.Crop)
    FText(app.displayName, 164f, 167f, 24f)
    FText(app.email, 166f, 200f, 11f)
    FText(app.studentType, 166f, 220f, 14f)
    if (app.streakShield > 0) {
        FText("${app.streakShield} streak-shield day(s) banked", 164f, 244f, 11f, w = 214f, color = PlaceholderGray)
    }
    // level progress bar: filled toward the next level, like the EXP tile on home
    FText("LV ${app.level}", 49f, 282f, 14f, weight = FontWeight.Medium)
    FText("XP ${app.xp} / ${app.xpForLevel(app.level)}", 180f, 284f, 11f, w = 160f, align = TextAlign.End,
        color = PlaceholderGray)
    FBox(49f, 304f, 290f, 10f, radius = 5f, border = Ink) {
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth()
            .fillMaxHeight(app.exp.coerceIn(0f, 1f)).background(Unverified.StatFill))
    }

    FText("ABOUT", 41f, 326f, 20f)
    val rows = listOf(
        "Level: ${app.level}", "Coins: ${"%,d".format(app.coins)}", "Streak: ${app.streak}",
        "Completed Tasks: ${app.completedTasks}", "Reviewers Completed: ${app.reviewersCompleted}",
        "Lifetime XP: ${app.totalXp}"
    )
    rows.forEachIndexed { i, r -> FText(r, 65f, 358f + i * 26f, 15f) }

    // ---- achievements: unlocked = filled chip, locked = outline ----
    val ach = listOf(
        "First Task" to (app.completedTasks >= 1),
        "3 Streak" to (app.streak >= 3),
        "LV 5" to (app.level >= 5),
        "Scholar" to (app.totalXp >= 150)
    )
    FBox(25f, 530f, 363f, 86f, radius = Unverified.CardRadius) {
        ach.forEachIndexed { i, (name, got) ->
            FBox(12f + i * 88f, 16f, 80f, 54f, radius = 8f, fill = if (got) Ink else Paper) {
                Text(if (got) name else "???",
                    color = if (got) Paper else PlaceholderGray, fontSize = ssp(11f),
                    fontFamily = DrEhFont, textAlign = TextAlign.Center, softWrap = true,
                    modifier = Modifier.align(Alignment.Center))
            }
        }
    }

    FBox(25f, 628f, 363f, 56f, radius = Unverified.CardRadius, modifier = Modifier.tap(onEdit))
    FImage("user_1", 52f, 644f, 24f, 24f)
    FText("Edit Profiles", 97f, 642f, 20f)

    FBox(25f, 692f, 363f, 56f, radius = Unverified.CardRadius, modifier = Modifier.tap { settings = true })
    FImage("cog_1", 49f, 704f, 32f, 32f)
    FText("Settings", 97f, 704f, 20f)

    FBox(25f, 754f, 363f, 40f, radius = Unverified.CardRadius - 4f, modifier = Modifier.tap(onLogOut))
    FImage("door_open_alt_1", 53f, 762f, 24f, 24f)
    FText("Log out", 93f, 762f, 20f)

    if (settings) SettingsDialog(app) { settings = false }
    BottomNav(Tab.PROFILE, onTab)
}

/** 2x3 avatar grid dialog - saves straight into the app state so home/profile follows. */
@Composable
private fun AvatarPicker(app: AppState, onDismiss: () -> Unit) {
    val avatars = listOf("avatar_1", "avatar_2", "avatar_3", "avatar_4", "avatar_5", "avatar_6")
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Paper,
        title = { Text("Choose avatar", fontFamily = DrEhFont, color = Ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(sc(8f))) {
                avatars.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(sc(8f))) {
                        row.forEach { name ->
                            FBox(0f, 0f, 88f, 88f, radius = 10f,
                                fill = if (app.avatar == name) Ink else Paper,
                                modifier = Modifier.tap { app.avatar = name; onDismiss() }) {
                                FImage(name, 12f, 12f, 64f, 64f, ContentScale.Crop)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("CLOSE", fontFamily = DrEhFont, color = Ink) }
        }
    )
}

/** Same dialog style as NameDialog: paper card, mono text, Ink buttons. */
@Composable
private fun SettingsDialog(app: AppState, onDismiss: () -> Unit) {
    var confirmReset by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Paper,
        title = { Text("Settings", fontFamily = DrEhFont, color = Ink) },
        text = {
            Column {
                Text("Daily bar decay (pet needs care)", fontFamily = DrEhFont, color = Ink)
                Row(horizontalArrangement = Arrangement.spacedBy(sc(12f))) {
                    FBox(0f, 8f, 52f, 34f, radius = 8f,
                        fill = if (app.dailyDecay) Ink else Paper,
                        modifier = Modifier.tap { app.dailyDecay = true }) {
                        Text("On", color = if (app.dailyDecay) Paper else Ink,
                            fontFamily = DrEhFont, fontSize = ssp(14f),
                            modifier = Modifier.align(Alignment.Center))
                    }
                    FBox(64f, 8f, 52f, 34f, radius = 8f,
                        fill = if (!app.dailyDecay) Ink else Paper,
                        modifier = Modifier.tap { app.dailyDecay = false }) {
                        Text("Off", color = if (!app.dailyDecay) Paper else Ink,
                            fontFamily = DrEhFont, fontSize = ssp(14f),
                            modifier = Modifier.align(Alignment.Center))
                    }
                }
                Spacer(Modifier.height(sc(16f)))
                FBox(0f, 0f, 220f, 38f, radius = 8f,
                    fill = if (confirmReset) Ink else Paper, modifier = Modifier.tap {
                        if (confirmReset) { app.resetProgress(); onDismiss() } else confirmReset = true
                    }) {
                    Text(if (confirmReset) "tap again to confirm" else "Reset progress (XP, coins, streak)",
                        color = if (confirmReset) Paper else Ink, fontFamily = DrEhFont,
                        fontSize = ssp(12f), modifier = Modifier.align(Alignment.Center))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("DONE", fontFamily = DrEhFont, color = Ink) } }
    )
}
