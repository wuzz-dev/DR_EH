package com.dreh.app.gclass

import android.content.Context
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.util.Base64
import com.dreh.app.AppState
import com.dreh.app.todayKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.text.ParsePosition
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Google Classroom sync WITHOUT a Google Cloud account:
 *
 * Classroom auto-creates one Google Calendar per class; coursework (assignments/quizzes) shows
 * up there as events. Any calendar exposes a "secret address in iCal format" - a plain HTTPS
 * link to an .ics file that needs no API key, no OAuth, no console access. The student pastes
 * one link per class (Tasks screen), and syncing just downloads that file and imports the
 * coursework as tasks.
 *
 * It imports calendar events and due dates in one direction. It cannot read submission state or
 * send task completion back to Classroom. Sync runs on request and when the Tasks screen opens.
 */

// ------------------------------------------------------------------ saved links (+ remembered imports)

class GClassStore(context: Context) {
    private val prefs = context.getSharedPreferences("dr_eh_gclass", Context.MODE_PRIVATE)

    fun links(): List<String> = (prefs.getString("links", "") ?: "").split("\n")
        .mapNotNull(::normalizeLink).distinct()

    /** UIDs already seen; used to keep old imports refreshable and avoid duplicate task rows. */
    fun imported(): Set<String> =
        (prefs.getString("imported", "") ?: "").split("\n").filter { it.isNotBlank() }.toSet()

    /** Accepts Google's secret iCal URL (webcal:// is normalized to https). */
    fun addLink(raw: String): Boolean {
        val url = normalizeLink(raw) ?: return false
        val links = links().toMutableList()
        if (url !in links) links.add(url)
        prefs.edit().putString("links", links.joinToString("\n")).apply()
        return true
    }

    private fun normalizeLink(raw: String): String? {
        val candidate = raw.trim().replace(Regex("^webcal://", RegexOption.IGNORE_CASE), "https://")
        val parsed = runCatching { java.net.URI(candidate) }.getOrNull() ?: return null
        val path = parsed.path.orEmpty()
        if (parsed.scheme?.equals("https", true) != true ||
            parsed.host?.equals("calendar.google.com", true) != true ||
            !path.contains("/ical/", true) || !path.endsWith(".ics", true) ||
            parsed.userInfo != null) return null
        return parsed.toASCIIString()
    }

    fun removeLink(raw: String) {
        val links = links().toMutableList()
        links.remove(raw)
        prefs.edit().putString("links", links.joinToString("\n")).apply()
    }

    fun taskId(eventUid: String): Long? {
        val encodedUid = Base64.encodeToString(
            eventUid.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        return prefs.getString("task_$encodedUid", null)?.toLongOrNull()
    }

    fun remember(eventUid: String, taskId: Long? = null) {
        val all = imported().toMutableSet()
        all.add(eventUid)
        val editor = prefs.edit().putString("imported", all.joinToString("\n"))
        if (taskId != null) {
            val encodedUid = Base64.encodeToString(
                eventUid.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
            )
            editor.putString("task_$encodedUid", taskId.toString())
        }
        editor.apply()
    }

    // ---- AUTO mode: the class calendars already synced by the phone's Google account ----
    /** Device calendar IDs the student marked as class calendars (auto-picked once). */
    fun deviceCalendars(): Set<Long> =
        (prefs.getString("devCals", "") ?: "").split(",").filter { it.isNotBlank() }.mapNotNull { it.toLongOrNull() }.toSet()
    fun setDeviceCalendars(ids: Set<Long>) { prefs.edit().putString("devCals", ids.joinToString(",")).apply() }

    /** One passive permission ask per install (Android silently drops repeats), one-tap connect after. */
    fun askedOnce(): Boolean = prefs.getBoolean("autoAsked", false)
    fun markAsked() { prefs.edit().putBoolean("autoAsked", true).apply() }

    /** Quiet auto-syncs are throttled so screen opens do not hammer the calendar provider. */
    fun autoSyncDue(): Boolean {
        val now = System.currentTimeMillis()
        return now - prefs.getLong("autoLast", 0) > 15 * 60_000 &&
            now - prefs.getLong("autoAttempt", 0) > 2 * 60_000
    }
    fun markAutoAttempt() { prefs.edit().putLong("autoAttempt", System.currentTimeMillis()).apply() }
    fun markAutoSync() {
        val now = System.currentTimeMillis()
        prefs.edit().putLong("autoLast", now).putLong("autoAttempt", now).apply()
    }
}

// ------------------------------------------------------------------ the sync itself

object GClassSync {

    data class SyncResult(
        val added: Int,
        val updated: Int = 0,
        val error: String? = null,
        val failedSources: Int = 0
    ) {
        val succeeded: Boolean get() = error == null
        val message: String
            get() {
                val changes = buildList {
                    if (added > 0) add("+$added added")
                    if (updated > 0) add("$updated updated")
                }.joinToString(", ")
                return when {
                    error != null && changes.isNotEmpty() -> "Synced with some errors: $changes. $error"
                    error != null -> "Sync failed. $error"
                    changes.isNotEmpty() -> "Classwork synced: $changes"
                    else -> "Calendars synced. No new changes"
                }
            }
    }

    data class Ev(val uid: String, val title: String, val timeMillis: Long, val allDay: Boolean)

    /** Downloads every linked class calendar and imports new coursework into [app]'s task list.
     *  Two sources merged: AUTO (the phone's own calendar store, needs READ_CALENDAR)
     *  and any manually pasted secret-iCal links. */
    suspend fun sync(store: GClassStore, app: AppState, context: Context? = null): SyncResult {
        val urls = store.links()
        val selectedDeviceCalendars = store.deviceCalendars()
        val canReadDeviceCalendars = context != null && DeviceCalendar.canRead(context)
        val auto = canReadDeviceCalendars && selectedDeviceCalendars.isNotEmpty()
        if (urls.isEmpty() && !auto) {
            val cals = context?.let {
                if (DeviceCalendar.canRead(it)) runCatching { DeviceCalendar.classCalendars(it) }.getOrDefault(emptyList())
                else emptyList()
            } ?: emptyList()
            return when {
                selectedDeviceCalendars.isNotEmpty() && !canReadDeviceCalendars ->
                    SyncResult(0, error = "Allow calendar access in sync settings, then try again")
                cals.isNotEmpty() -> SyncResult(0, error = "Choose calendars in sync settings first")
                else -> SyncResult(0, error = "Connect a class calendar in sync settings first")
            }
        }

        val sourceCount = urls.size + if (selectedDeviceCalendars.isNotEmpty()) 1 else 0
        var failedSources = if (selectedDeviceCalendars.isNotEmpty() && !canReadDeviceCalendars) 1 else 0
        val events = ArrayList<Ev>()
        withContext(Dispatchers.IO) {
            urls.forEach { url ->
                try { events.addAll(parseIcs(fetch(url))) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { failedSources++ }
            }
            if (auto) {
                try {
                    val ctx = context ?: throw IllegalStateException("Calendar access is unavailable")
                    events.addAll(DeviceCalendar.events(ctx, selectedDeviceCalendars).map {
                        Ev(it.uid, it.title.take(60), it.beginMs, it.allDay)
                    })
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { failedSources++ }
            }
        }

        if (sourceCount > 0 && failedSources == sourceCount) {
            return SyncResult(
                0,
                error = "Check the internet, calendar access, and saved iCal links, then try again.",
                failedSources = failedSources
            )
        }

        // Keep first-time imports focused on current coursework. Previously imported items still
        // refresh even after they become overdue or move beyond this window.
        val knownUids = store.imported()
        val now = System.currentTimeMillis()
        val earliest = now - 3L * 24 * 3_600_000L
        val latest = now + 120L * 24 * 3_600_000L
        val currentEvents = events.filter {
            it.uid in knownUids || it.timeMillis in earliest..latest
        }

        // A device calendar and its secret iCal feed can expose the same event with different
        // UIDs. Keep one copy by title + due instant, while preserving same-title assignments
        // with different deadlines.
        val eventsByDeadline = LinkedHashMap<Pair<String, Long>, Ev>()
        val eventAliases = HashMap<Pair<String, Long>, MutableSet<String>>()
        currentEvents.forEach { ev ->
            if (ev.title.isBlank()) return@forEach
            val key = ev.title.trim().lowercase(Locale.ROOT) to ev.timeMillis
            eventsByDeadline.putIfAbsent(key, ev)
            eventAliases.getOrPut(key) { linkedSetOf() }.add(ev.uid)
        }
        val uniqueEvents = eventsByDeadline.entries.sortedBy { it.key.second }
        var added = 0
        var updated = 0
        uniqueEvents.forEach { (key, ev) ->
            val aliases = eventAliases[key].orEmpty()
            val rememberedId = aliases.firstNotNullOfOrNull { store.taskId(it) }
            val stableId = rememberedId ?: uidKey(ev.uid)
            val change = app.upsertImportedTask(
                stableId, legacyUidKey(ev.uid), ev.title, dueLabel(ev), detail(ev)
            )
            if (change.added) added++
            if (change.updated) updated++
            aliases.forEach { store.remember(it, change.taskId) }
        }

        val error = if (failedSources > 0) {
            "$failedSources of $sourceCount calendars could not sync. Check their links or phone calendar access."
        } else null
        return SyncResult(added, updated, error, failedSources)
    }

    private fun legacyUidKey(uid: String) = "gclass_$uid".hashCode().toLong()

    /** Stable 64-bit ID avoids the collision rate of the legacy 32-bit Java hash. */
    private fun uidKey(uid: String): Long {
        var hash = -3750763034362895579L // FNV-1a 64-bit offset basis
        uid.toByteArray(Charsets.UTF_8).forEach { byte ->
            hash = hash xor (byte.toLong() and 0xff)
            hash *= 1099511628211L
        }
        return (hash and 0x3fffffffffffffffL) or 0x4000000000000000L
    }

    private fun fetch(urlSpec: String): String {
        val conn = (URL(urlSpec).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "DrEh/1.0 student prototype")
            setRequestProperty("Accept", "text/calendar, text/plain;q=0.9")
        }
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
            if (code !in 200..299) throw IllegalStateException("Calendar request failed (HTTP $code)")
            return text
        } finally { conn.disconnect() }
    }

    /** Minimal RFC 5545 parsing, tuned for what Google's .ics exports actually contain. */
    @Suppress("UNUSED_PARAMETER")
    internal fun parseIcs(text: String, alreadyImported: Set<String> = emptySet()): List<Ev> {
        require(text.contains("BEGIN:VCALENDAR", ignoreCase = true)) { "The saved link did not return an iCal calendar" }
        // unfold folded lines first (continuation lines start with a space or tab)
        val lines = ArrayList<String>()
        text.split("\r\n", "\n").forEach { raw ->
            val l = raw.trimEnd('\r')
            if ((l.startsWith(" ") || l.startsWith("\t")) && lines.isNotEmpty()) {
                lines[lines.size - 1] = lines[lines.size - 1] + l.substring(1)
            } else lines.add(l)
        }

        val out = ArrayList<Ev>()
        var block: ArrayList<String>? = null
        lines.forEach { line ->
            when (line.trim().uppercase(Locale.ROOT)) {
                "BEGIN:VEVENT" -> block = ArrayList()
                "END:VEVENT" -> {
                    // Keep the optional parameter for source compatibility. Existing events must
                    // be parsed on every sync so changed titles and deadlines can be refreshed.
                    block?.let { parseEvent(it)?.let { ev -> if (ev.uid.isNotBlank()) out.add(ev) } }
                    block = null
                }
                else -> block?.add(line)
            }
        }
        return out
    }

    /** Turns one VEVENT block into coursework, or null when it is not usable. */
    private fun parseEvent(block: List<String>): Ev? {
        var uid = ""
        var summary = ""
        var start = ""
        var tzid = ""
        var status = ""
        block.forEach { l ->
            val colon = l.indexOf(':')
            if (colon <= 0) return@forEach
            val key = l.substring(0, colon).trim()
            val v = l.substring(colon + 1).trim()
            val name = key.uppercase().substringBefore(';').trim()
            val tz = Regex("TZID=([^;]+)").find(key)?.groupValues?.get(1)
            when (name) {
                "UID" -> uid = unEscape(v)
                "SUMMARY" -> summary = unEscape(v)
                "DTSTART" -> { start = v; tzid = tz.orEmpty() }
                "STATUS" -> status = v.uppercase(Locale.ROOT)
            }
        }
        if (uid.isEmpty() || summary.isEmpty() || start.isEmpty() || status == "CANCELLED") return null

        val time = try { parseWhen(start, tzid) } catch (e: Exception) { return null } ?: return null
        return Ev(uid, summary.take(60), time.first, time.second)
    }

    /** "20260312" -> all-day; "20260312T235900Z"/TZID'd -> a timed deadline. Returns (millis, allDay). */
    private fun parseWhen(value: String, tzid: String): Pair<Long, Boolean>? {
        return if (value.length == 8 && value.all { it.isDigit() }) {
            val f = SimpleDateFormat("yyyyMMdd", Locale.US).apply {
                isLenient = false
                timeZone = TimeZone.getDefault()
            }
            Pair(parseExact(f, value)?.time ?: return null, true)
        } else {
            val f = SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.US).apply { isLenient = false }
            f.timeZone = when {
                value.endsWith("Z") -> TimeZone.getTimeZone("UTC")
                tzid.isNotBlank() && tzid in TimeZone.getAvailableIDs() -> TimeZone.getTimeZone(tzid)
                else -> TimeZone.getDefault()
            }
            Pair(parseExact(f, value.removeSuffix("Z"))?.time ?: return null, false)
        }
    }

    private fun parseExact(format: SimpleDateFormat, value: String): Date? {
        val position = ParsePosition(0)
        val date = format.parse(value, position)
        return date?.takeIf { position.index == value.length }
    }

    private fun unEscape(s: String) = s.replace("\\,", ",").replace("\\;", ";")
        .replace("\\N", " ").replace("\\n", " ").trim()

    private fun dueLabel(ev: Ev): String {
        val f = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getDefault() }
        val day = f.format(java.util.Date(ev.timeMillis))
        return when (day) {
            todayKey(0) -> "Due today"
            todayKey(1) -> "Due tomorrow"
            else -> "Due " + SimpleDateFormat("MMM d", Locale.US).apply { timeZone = TimeZone.getDefault() }
                .format(java.util.Date(ev.timeMillis))
        }
    }

    /** If a due time exists, show it like the sample cards do ("11:59 - Activity 1"). */
    private fun detail(ev: Ev): String? =
        if (ev.allDay) null
        else SimpleDateFormat("HH:mm", Locale.US).apply { timeZone = TimeZone.getDefault() }
            .format(java.util.Date(ev.timeMillis))
}

// ------------------------------------------------------------------ AUTO source: the phone's own calendar store

/**
 * The "sync without pasting anything" half: Android mirrors every Google account's calendars
 * (including the class calendars Classroom creates) into the system CalendarProvider. Reading it
 * needs only the READ_CALENDAR runtime permission - no console, no keys. The Google Calendar app
 * must have synced the account at least once (it does by default).
 */
object DeviceCalendar {

    private val HANDLED = listOf(
        "holiday", "birthday", "week number", "contact", "phase",
        "reminder", "tasks", "family", "sports", "weather", " subscribed", "duplicate"
    )

    fun canRead(context: Context): Boolean =
        androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.READ_CALENDAR
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    data class Cal(val id: Long, val name: String, val account: String)

    /** Google calendars on this phone that look like Classroom class calendars. */
    fun classCalendars(context: Context): List<Cal> {
        if (!canRead(context)) return emptyList()
        val out = ArrayList<Cal>()
        val proj = arrayOf(
            Calendars._ID, Calendars.CALENDAR_DISPLAY_NAME, Calendars.ACCOUNT_NAME,
            Calendars.IS_PRIMARY, Calendars.ACCOUNT_TYPE
        )
        context.contentResolver.query(Calendars.CONTENT_URI, proj, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val type = c.getString(4) ?: ""
                val account = c.getString(2) ?: ""
                val name = c.getString(1) ?: ""
                if (type != "com.google") continue
                if (c.getInt(3) == 1) continue                          // the student's personal calendar
                if (name.equals(account, ignoreCase = true)) continue    // primary duplicate
                if (HANDLED.any { name.lowercase().contains(it.trim()) }) continue
                out.add(Cal(c.getLong(0), name, account))
            }
        }
        return out
    }

    data class DevEvent(val uid: String, val title: String, val beginMs: Long, val allDay: Boolean)

    /** Upcoming events from the chosen class calendars - plus the last 3 days, so overdue
     *  coursework still shows up instead of silently vanishing at its deadline. */
    fun events(context: Context, calIds: Set<Long>, days: Int = 120): List<DevEvent> {
        if (!canRead(context) || calIds.isEmpty()) return emptyList()
        val now = System.currentTimeMillis()
        val from = now - 3L * 24 * 3_600_000L   // keep recently-due items visible
        val to = now + days * 24L * 3_600_000L
        val proj = arrayOf(
            Events._ID, Events.CALENDAR_ID, Events.TITLE, Events.DTSTART, Events.ALL_DAY, Events.STATUS
        )
        val ids = calIds.joinToString(",") { it.toString() }
        val where = "${Events.CALENDAR_ID} IN ($ids) AND ${Events.DELETED}=0 AND ${Events.DTSTART}>=? AND ${Events.DTSTART}<=?"
        val out = ArrayList<DevEvent>()
        context.contentResolver.query(Events.CONTENT_URI, proj, where, arrayOf(from.toString(), to.toString()), "${Events.DTSTART} ASC")
            ?.use { c ->
                while (c.moveToNext()) {
                    if (c.getInt(5) == Events.STATUS_CANCELED || c.getInt(5) == Events.STATUS_TENTATIVE) continue
                    val title = c.getString(2)?.trim().orEmpty()
                    if (title.isEmpty() || title.equals("No title", true)) continue
                    val allDay = c.getInt(4) != 0
                    val start = c.getLong(3)
                    out.add(DevEvent(
                        "dev${c.getLong(1)}_${c.getLong(0)}", title,
                        if (allDay) localAllDayStart(start) else start, allDay
                    ))
                }
            }
        return out
    }

    /** CalendarProvider stores all-day DTSTART at UTC midnight; restore the matching local day. */
    private fun localAllDayStart(utcMidnight: Long): Long {
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMidnight }
        return Calendar.getInstance(TimeZone.getDefault()).apply {
            clear()
            set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
        }.timeInMillis
    }
}
