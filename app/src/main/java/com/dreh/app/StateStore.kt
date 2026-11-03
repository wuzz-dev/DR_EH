package com.dreh.app

import android.content.Context
import android.content.SharedPreferences

/**
 * Persistence for the whole prototype state: progression (level/XP/coins/streak...), the
 * companion's vitals, tasks, calendar events, claim/shop state and profile basics. Everything
 * lives in one SharedPreferences file so every cold open continues where the last leave-off was.
 */
/** Process-wide holder: one AppState, loaded from prefs on first call, savable any time. */
object AppStateHolder {
    var state: AppState? = null
    private fun store(ctx: Context) = StateStore(ctx.applicationContext)

    fun load(context: Context): AppState {
        if (state == null) {
            state = AppState()
            store(context).load(state!!)
        }
        return state!!
    }

    fun save(context: Context) { state?.let { store(context).save(it) } }
    fun reset(context: Context) { store(context).clear() }
}

class StateStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("dr_eh_state", Context.MODE_PRIVATE)

    fun load(app: AppState) {
        app.displayName = prefs.getString("displayName", app.displayName) ?: app.displayName
        app.email = prefs.getString("email", app.email) ?: app.email
        app.studentType = prefs.getString("studentType", app.studentType) ?: app.studentType
        app.avatar = prefs.getString("avatar", app.avatar) ?: app.avatar
        app.password = prefs.getString("password", app.password) ?: app.password
        app.session = prefs.getBoolean("session", app.session)
        app.level = prefs.getInt("level", app.level)
        app.coins = prefs.getInt("coins", app.coins)
        app.streak = prefs.getInt("streak", app.streak)
        app.completedTasks = prefs.getInt("completedTasks", app.completedTasks)
        app.reviewersCompleted = prefs.getInt("reviewersCompleted", app.reviewersCompleted)
        app.setStreakShield(prefs.getInt("streakShield", 0))
        app.dailyDecay = prefs.getBoolean("dailyDecay", app.dailyDecay)
        app.setVitals(
            prefs.getFloat("health", 38f / 47f), prefs.getFloat("energy", 27f / 47f),
            prefs.getFloat("mood", 23f / 47f), prefs.getFloat("hunger", 40f / 47f)
        )
        app.setXp(avg("xp", app.xp), prefs.getInt("totalXp", app.totalXp))
        app.claimedRewards.clear()
        prefs.getString("claimed", "")?.split('\n')?.filter { it.isNotBlank() }?.forEach {
            val i = it.toIntOrNull(); if (i != null) app.claimedRewards.add(i)
        }
        restoreTasks(app, prefs.getString("tasks", null))
        restoreEvents(app, prefs.getString("events", null))
    }

    fun save(app: AppState) {
        prefs.edit()
            .putString("displayName", app.displayName)
            .putString("email", app.email)
            .putString("studentType", app.studentType)
            .putString("avatar", app.avatar)
            .putString("password", app.password)
            .putBoolean("session", app.session)
            .putInt("level", app.level).putInt("coins", app.coins)
            .putInt("streak", app.streak)
            .putInt("completedTasks", app.completedTasks)
            .putInt("reviewersCompleted", app.reviewersCompleted)
            .putInt("streakShield", app.streakShield)
            .putBoolean("dailyDecay", app.dailyDecay)
            .putFloat("health", app.health).putFloat("energy", app.energy)
            .putFloat("mood", app.mood).putFloat("hunger", app.hunger)
            .putInt("xp", app.xp).putInt("totalXp", app.totalXp)
            .putString("claimed", app.claimedRewards.joinToString("\n"))
            .putString("tasks", app.tasks.joinToString("|") { t ->
                "${t.id}~${t.title}~${t.dueLabel ?: ""}~${t.detail ?: ""}~${t.fromClassroom}~${t.done}"
            })
            .putString("events", app.events.joinToString("|") { e ->
                "${e.id}~${e.dayKey}~${e.title}~${e.timeLabel ?: ""}~${e.done}"
            })
            .apply()
    }

    fun clear() { prefs.edit().clear().apply() }

    private fun avg(key: String, fallback: Int): Int = prefs.getInt(key, fallback)

    private fun restoreTasks(app: AppState, raw: String?) {
        if (raw.isNullOrBlank()) return
        app.tasks.clear()
        raw.split("|").filter { it.isNotBlank() }.forEach { line ->
            val f = line.split("~")
            if (f.size >= 6) app.tasks.add(com.dreh.app.TaskItem(
                id = f[0].toLongOrNull() ?: return@forEach,
                title = f[1], dueLabel = f[2].takeIf { it.isNotBlank() },
                detail = f[3].takeIf { it.isNotBlank() },
                fromClassroom = f[4] == "true", done = f[5] == "true"
            ))
        }
    }

    private fun restoreEvents(app: AppState, raw: String?) {
        if (raw.isNullOrBlank()) return
        app.events.clear()
        raw.split("|").filter { it.isNotBlank() }.forEach { line ->
            val f = line.split("~")
            if (f.size >= 5) app.events.add(com.dreh.app.CalEvent(
                id = f[0].toLongOrNull() ?: return@forEach,
                dayKey = f[1], title = f[2],
                timeLabel = f[3].takeIf { it.isNotBlank() }, done = f[4] == "true"
            ))
        }
    }
}
