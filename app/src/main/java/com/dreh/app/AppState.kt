package com.dreh.app

import androidx.compose.runtime.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** "yyyy-MM-dd" for today shifted by [dayOffset] days. */
fun todayKey(dayOffset: Int = 0): String {
    val c = Calendar.getInstance()
    c.add(Calendar.DAY_OF_YEAR, dayOffset)
    return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.time)
}

data class TaskItem(
    val id: Long,
    val title: String,
    val dueLabel: String? = null,   // "Due today"
    val detail: String? = null,     // "11:59 - Activity 1"
    val fromClassroom: Boolean = false,
    val done: Boolean = false
)

data class ImportedTaskChange(val added: Boolean, val updated: Boolean, val taskId: Long)

data class FileItem(val id: Long, val name: String, val text: String = "")

/** A calendar entry. dayKey is "yyyy-MM-dd". */
data class CalEvent(
    val id: Long,
    val dayKey: String,
    val title: String,
    val timeLabel: String? = null,
    val done: Boolean = false
)
data class FolderItem(val id: Long, val name: String, val files: List<FileItem> = emptyList())

/** Matches the Figma home frames: neutral / happy / tired / sick / dead */
enum class PetState { NEUTRAL, HAPPY, TIRED, SICK, DEAD }

class AppState {
    // ---- profile ----
    var displayName by mutableStateOf("Student")
    var email by mutableStateOf("")
    var studentType by mutableStateOf("College Student")
    var avatar by mutableStateOf("avatar_4")

    // ---- auth (prototype-local): the credentials the account was created with + a session flag.
    // On the next cold open a valid session skips straight past the onboarding flow to Home.
    var password by mutableStateOf("")
    var session by mutableStateOf(false)

    // ---- progression: everything the EXP bar, the level-up splash and the rewards feed on ----
    var level by mutableIntStateOf(1)
    var coins by mutableIntStateOf(15)
    var streak by mutableIntStateOf(1)
    var completedTasks by mutableIntStateOf(0)
    var reviewersCompleted by mutableIntStateOf(0)

    /** XP inside the current level. The EXP tile shows xp / xpForLevel(level), so a full bar = level up. */
    var xp by mutableIntStateOf(14)
        private set
    /** Lifetime XP - what the "Unlock for N+ xp" reward cards are gated on. */
    var totalXp by mutableIntStateOf(14)
        private set
    fun xpForLevel(l: Int) = 30 + (l - 1) * 15

    // ---- live celebration signals, watched by the Home screen ----
    /** +1 on every XP gain: the pet pulses, the happy gif flashes, a "+n XP" tag rises. */
    var cheerTick by mutableIntStateOf(0)
        private set
    var xpGained by mutableIntStateOf(0)
        private set
    /** +1 when the pet is tapped: just the heartbeat pulse, no XP. */
    var bopTick by mutableIntStateOf(0)
    /** +1 whenever the companion "speaks" - drives the head-wiggle / typewriter bubble. */
    var talkTick by mutableIntStateOf(0)
        private set
    fun talk() { talkTick++ }
    /** Set while a level-up happened and the "LEVEL UP!" splash should be shown. Cleared on tap. */
    var levelUpPending by mutableStateOf(false)

    // ---- Dr. Eh vitals, 0f..1f (initial values = the "neutral" frame fill levels) ----
    var health by mutableFloatStateOf(38f / 47f)
        private set
    var energy by mutableFloatStateOf(27f / 47f)
        private set
    var mood by mutableFloatStateOf(23f / 47f)
        private set
    var hunger by mutableFloatStateOf(40f / 47f)
        private set

    /** Test/persistence setter used by StateStore to restore the restored bars. */
    fun setVitals(h: Float, e: Float, m: Float, hu: Float) {
        health = h.coerceIn(0f, 1f); energy = e.coerceIn(0f, 1f)
        mood = m.coerceIn(0f, 1f); hunger = hu.coerceIn(0f, 1f)
    }

    /** Persistence setter for the XP pair (StateStore restore). */
    fun setXp(current: Int, total: Int) {
        xp = current.coerceAtLeast(0); totalXp = total.coerceAtLeast(xp)
    }

    /** Persistence setter for the shielded days. */
    internal fun setStreakShield(n: Int) { streakShield = n.coerceAtLeast(0) }

    /** EXP tile fill: current-level progress, bottom-up fill like the Figma frames. */
    val exp: Float
        get() = (xp.toFloat() / xpForLevel(level)).coerceIn(0f, 1f)

    val pet: PetState
        get() = when {
            health <= 0f -> PetState.DEAD
            listOf(health, energy, mood, hunger).all { it >= 0.9f } -> PetState.HAPPY
            health < 0.3f -> if (hunger < 0.3f) PetState.SICK else PetState.TIRED
            else -> PetState.NEUTRAL
        }

    /** The XP heart of the game: fills the bar, overflows into a level-up (coins + a vitals recharge). */
    fun gainXp(amount: Int) {
        if (amount <= 0) return
        xp += amount
        totalXp += amount
        xpGained = amount
        cheerTick++
        talkTick++
        while (xp >= xpForLevel(level)) {
            xp -= xpForLevel(level)
            level += 1
            coins += 25
            // levelling up visibly helps the companion - his tiles fill back up
            health = (health + 0.20f).coerceAtMost(1f)
            energy = (energy + 0.20f).coerceAtMost(1f)
            mood = (mood + 0.20f).coerceAtMost(1f)
            hunger = (hunger + 0.10f).coerceAtMost(1f)
            levelUpPending = true
        }
    }

    /** Tap-to-cheer: petting Dr. Eh lifts his mood bar a touch (no XP, just a heartbeat). */
    fun bop() {
        bopTick++
        mood = (mood + 0.03f).coerceAtMost(1f)
    }

    // ---- daily rhythm: a new calendar day drains the bars a little (the companion needs care) ----
    private var lastOpenDay by mutableStateOf<String?>(null)
    private var lastDoneDay by mutableStateOf<String?>(null)

    /** Call once per app open; handles the calendar-day change - decay + streak break. */
    fun onAppOpen() {
        val today = todayKey()
        if (lastOpenDay == today) return
        if (lastOpenDay != null) {
            if (dailyDecay) {
                if (streakShield > 0) { streakShield -= 1 }
                else {
                    health = (health - 0.10f).coerceAtLeast(0f)
                    energy = (energy - 0.12f).coerceAtLeast(0f)
                    mood = (mood - 0.10f).coerceAtLeast(0f)
                    hunger = (hunger - 0.06f).coerceAtLeast(0f)
                }
            }
            if (lastDoneDay != today && lastDoneDay != todayKey(-1) && streak > 1) {
                streak -= streakShield
                if (streak < 1) streak = 1
                spendStreakShieldIfAny()
            }
        }
        lastOpenDay = today
    }

    // ---- tasks ----
    var hasClassroom by mutableStateOf(true)
    val tasks = mutableStateListOf(
        TaskItem(1, "2542A - IT104", "Due today", "11:59 - Activity 1", fromClassroom = true),
        TaskItem(2, "2555A - CC106", fromClassroom = true, done = true)
    )
    private var nextId = 100L

    private fun idInUse(id: Long): Boolean = tasks.any { it.id == id } || events.any { it.id == id } ||
        folders.any { folder -> folder.id == id || folder.files.any { it.id == id } }

    private fun nextUniqueId(): Long {
        while (idInUse(nextId)) nextId++
        return nextId++
    }

    fun addTask(title: String) { tasks.add(TaskItem(nextUniqueId(), title)) }

    /** Removes a task (its detail sheet offers delete). */
    fun deleteTask(id: Long) { tasks.removeAll { it.id == id } }

    /** Tasks synced from Google Classroom (iCal addresses) land here. */
    fun addImportedTask(id: Long, title: String, dueLabel: String?, detail: String?): Boolean =
        upsertImportedTask(id, id, title, dueLabel, detail).added

    /** Insert or refresh one synced event. A legacy ID lets older imports migrate without
     * duplicating the task; completion is preserved when its title or deadline changes. */
    fun upsertImportedTask(
        id: Long, legacyId: Long, title: String, dueLabel: String?, detail: String?
    ): ImportedTaskChange {
        val index = tasks.indexOfFirst { it.fromClassroom && it.id == id }
            .takeIf { it >= 0 } ?: tasks.indexOfFirst { it.fromClassroom && it.id == legacyId }
        if (index >= 0) {
            val old = tasks[index]
            val safeId = if (!idInUse(id) || old.id == id) id else old.id
            val updated = old.copy(id = safeId, title = title, dueLabel = dueLabel, detail = detail)
            val changed = updated != old
            if (changed) tasks[index] = updated
            hasClassroom = true
            return ImportedTaskChange(added = false, updated = changed, taskId = safeId)
        }

        val safeId = if (idInUse(id)) nextUniqueId() else id
        tasks.add(TaskItem(safeId, title, dueLabel, detail, fromClassroom = true))
        hasClassroom = true
        return ImportedTaskChange(added = true, updated = false, taskId = safeId)
    }

    /** Refreshes imported task details without resetting a completion the student already made. */
    fun updateImportedTask(id: Long, title: String, dueLabel: String?, detail: String?): Boolean {
        val index = tasks.indexOfFirst { it.id == id && it.fromClassroom }
        if (index < 0) return false
        val old = tasks[index]
        val updated = old.copy(title = title, dueLabel = dueLabel, detail = detail)
        if (updated == old) return false
        tasks[index] = updated
        return true
    }

    /** Completes a task, feeds the companion's bars, grants XP and returns a toast-style gain message. */
    fun completeTask(id: Long): String {
        val i = tasks.indexOfFirst { it.id == id }
        if (i < 0 || tasks[i].done) return ""
        tasks[i] = tasks[i].copy(done = true)
        health = (health + 0.10f).coerceAtMost(1f)
        energy = (energy + 0.05f).coerceAtMost(1f)
        mood = (mood + 0.10f).coerceAtMost(1f)
        completedTasks += 1
        // streak: the first completion of a day extends (or restarts) it
        val today = todayKey()
        if (lastDoneDay != today) {
            streak = if (lastDoneDay == todayKey(-1)) streak + 1 else 1
            lastDoneDay = today
        }
        gainXp(10)
        return "+10 XP" + if (levelUpPending) " - LEVEL UP! LV $level" else ""
    }

    // ---- calendar ----
    val events = mutableStateListOf<CalEvent>(
        CalEvent(nextId++, todayKey(0), "Water the plants", "08:00"),
        CalEvent(nextId++, todayKey(0), "Dr. Eh check-in", "20:00"),
        CalEvent(nextId++, todayKey(1), "Submit 2542A - IT104", "11:59")
    )

    fun eventsOn(dayKey: String): List<CalEvent> = events.filter { it.dayKey == dayKey }

    fun addEvent(dayKey: String, title: String, timeLabel: String?) {
        events.add(CalEvent(nextUniqueId(), dayKey, title, timeLabel?.takeIf { it.isNotBlank() }))
    }

    /** Toggles an event; returns true when it was just ticked off (worth +5 XP). */
    fun toggleEvent(id: Long): Boolean {
        val i = events.indexOfFirst { it.id == id }
        if (i < 0) return false
        val now = !events[i].done
        events[i] = events[i].copy(done = now)
        if (now) { mood = (mood + 0.05f).coerceAtMost(1f); gainXp(5) }
        return now
    }

    // ---- reviewer (folders -> files -> notes) ----
    val folders = mutableStateListOf<FolderItem>()

    fun createFolder(name: String) { folders.add(FolderItem(nextUniqueId(), name)) }
    fun renameFolder(id: Long, name: String) {
        val i = folders.indexOfFirst { it.id == id }
        if (i >= 0) folders[i] = folders[i].copy(name = name)
    }
    fun deleteFolder(id: Long) { folders.removeAll { it.id == id } }

    /** Creates a reviewer file; returns true when one was actually made (worth +15 XP). */
    fun createFile(folderId: Long, name: String): Boolean {
        val i = folders.indexOfFirst { it.id == folderId }
        if (i < 0) return false
        folders[i] = folders[i].copy(files = folders[i].files + FileItem(nextUniqueId(), name))
        // "Restore it by making a reviewer" - brings the companion back and heals halfway
        if (health <= 0f) { health = 0.5f; energy = 0.5f; mood = 0.5f; hunger = 0.5f }
        reviewersCompleted += 1
        energy = (energy + 0.10f).coerceAtMost(1f)
        hunger = (hunger + 0.10f).coerceAtMost(1f)
        gainXp(15)
        return true
    }
    fun renameFile(folderId: Long, fileId: Long, name: String) = updateFile(folderId, fileId) { it.copy(name = name) }
    fun deleteFile(folderId: Long, fileId: Long) {
        val i = folders.indexOfFirst { it.id == folderId }
        if (i >= 0) folders[i] = folders[i].copy(files = folders[i].files.filterNot { it.id == fileId })
    }
    fun setFileText(folderId: Long, fileId: Long, text: String) = updateFile(folderId, fileId) { it.copy(text = text) }

    private fun updateFile(folderId: Long, fileId: Long, f: (FileItem) -> FileItem) {
        val i = folders.indexOfFirst { it.id == folderId }
        if (i < 0) return
        folders[i] = folders[i].copy(files = folders[i].files.map { if (it.id == fileId) f(it) else it })
    }

    fun folder(id: Long) = folders.firstOrNull { it.id == id }
    fun file(folderId: Long, fileId: Long) = folder(folderId)?.files?.firstOrNull { it.id == fileId }

    // ---- rewards: XP-gated redeemables + a coin shop, both apply real effects ----
    val xpGates = listOf(10, 30, 60, 90, 120, 150, 180, 210)
    val rewardNames = listOf(
        "Coffee stop", "Load +100", "Milk tea pass", "Store poc",
        "Gcash card", "Weekend pass", "Mentor call", "Cram power"
    )
    val rewardEffects = listOf(
        "energy +25% for Dr. Eh",
        "hunger +25% for Dr. Eh",
        "mood +25% for Dr. Eh",
        "health +15% for Dr. Eh",
        "+1 streak-shield day",
        "+50 XP boost",
        "heal +25% all bars",
        "+100 XP boost"
    )
    /** Days bought with a "Gcash card" - daily decay is skipped while > 0. */
    var streakShield by mutableIntStateOf(0)
        private set
    val claimedRewards = mutableStateListOf<Int>()
    fun rewardUnlocked(index: Int) = index in xpGates.indices && totalXp >= xpGates[index]

    /** Redeems an XP reward: +10 coins AND applies the real effect to the companion. */
    fun claimReward(index: Int): Boolean {
        if (index in claimedRewards || !rewardUnlocked(index)) return false
        claimedRewards.add(index)
        coins += 10
        if (index == 5) { xpGained = 0; cheerTick++ }   // Weekend pass: gainXp does the cheer
        applyPerk(index)
        return true
    }

    // ---- the coin shop: spend the coins you have banked on the same perks, any time ----
    val shopNames = listOf("Snack break", "Energy drink", "Memory jazz", "Check-up", "Bar shield", "Double bars")
    val shopDescs = listOf(
        "hunger +20%", "energy +20%", "mood +20%", "health +30%", "+1 streak-shield day", "all bars +25%"
    )
    val shopCosts = listOf(20, 20, 25, 50, 60, 80)

    /** Buys a shop perk with coins. Returns the result message, or null when the coins are short. */
    fun buyPerk(index: Int): String? {
        if (index !in shopCosts.indices) return null
        if (coins < shopCosts[index]) return null
        coins -= shopCosts[index]
        when (index) {
            0 -> hunger = (hunger + 0.20f).coerceAtMost(1f)
            1 -> energy = (energy + 0.20f).coerceAtMost(1f)
            2 -> mood = (mood + 0.20f).coerceAtMost(1f)
            3 -> health = (health + 0.30f).coerceAtMost(1f)
            4 -> streakShield += 1
            5 -> {   // Double bars: a modest recharge of everything
                health = (health + 0.25f).coerceAtMost(1f)
                energy = (energy + 0.25f).coerceAtMost(1f)
                mood = (mood + 0.25f).coerceAtMost(1f)
                hunger = (hunger + 0.25f).coerceAtMost(1f)
            }
        }
        talkTick++
        return shopDescs[index] + " - done!"
    }

    /** The perk table shared by XP rewards (index matches rewardEffects). */
    private fun applyPerk(i: Int) {
        when (i) {
            0 -> energy = (energy + 0.25f).coerceAtMost(1f)
            1 -> hunger = (hunger + 0.25f).coerceAtMost(1f)
            2 -> mood = (mood + 0.25f).coerceAtMost(1f)
            3 -> health = (health + 0.15f).coerceAtMost(1f)
            4 -> streakShield += 1
            5, 7 -> gainXp(if (i == 5) 50 else 100)
            6 -> { health = (health + 0.25f).coerceAtMost(1f); mood = (mood + 0.25f).coerceAtMost(1f) }
        }
    }

    // ---- settings ----
    /** Whether a new calendar day drains the bars (toggle in Settings). */
    var dailyDecay by mutableStateOf(true)

    /** Uses one banked streak-shield day (Weekend/Gcash perks). */
    fun spendStreakShieldIfAny() {
        if (streakShield > 0) streakShield -= 1
    }

    /** Resets the whole progression to the Figma "neutral" starting frame. */
    fun resetProgress() {
        level = 1; coins = 15; streak = 1
        completedTasks = 0; reviewersCompleted = 0
        xp = 14; totalXp = 14
        health = 38f / 47f; energy = 27f / 47f; mood = 23f / 47f; hunger = 40f / 47f
        claimedRewards.clear()
        streakShield = 0
        tasks.forEachIndexed { i, t -> tasks[i] = t.copy(done = i == 1) }   // back to the sample two
        levelUpPending = false
    }

    /** Ends the session and clears the credentials - the next cold open lands on the start screen. */
    fun logOut() {
        session = false
        password = ""
    }
}
