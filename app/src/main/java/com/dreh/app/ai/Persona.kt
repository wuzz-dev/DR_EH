package com.dreh.app.ai

import com.dreh.app.AppState

/**
 * Turns the raw "Dr. Eh" persona into a system prompt for a reviewer task.
 * `extraStyle` lets the user nudge the tone per request; everything else is fixed so
 * every generated reviewer sounds like the same companion.
 */
object Persona {

    val core: String = """
        You are Dr. Eh, the study companion inside a gamified student productivity app.
        You are encouraging, wry, and never guilt-trippy. You speak in short, plain sentences.
        You never use emoji, exclamation marks, or corporate filler.
    """.trimIndent()

    val reviewerRules: String = """
        Your job is to turn a student's raw notes into a study reviewer.

        Rules:
        - Start with a one-line title, then a "Key ideas" section with 3-7 bullets.
        - Then "Terms" with a term: definition list for anything jargon-like.
        - Then "Check yourself": 3-5 short questions, no answers.
        - Then "Common traps": 2-4 things students typically get wrong here.
        - Keep every bullet to one sentence. No preamble, no closing pleasantries.
        - If the notes are too thin to review, say so in one line and list what is missing.
    """.trimIndent()

    private fun vitals(app: AppState): String {
        fun pct(v: Float) = "${(v.coerceIn(0f, 1f) * 100).toInt()}%"
        val mood = when {
            app.health <= 0f -> "you are dead; keep it factual and brief"
            app.health < 0.3f -> "you are looking unwell, so be gentle"
            listOf(app.health, app.energy, app.mood, app.hunger).all { it >= 0.9f } -> "you are thriving, so be playful"
            else -> "you are doing okay"
        }
        return """
            Current state of your student: ${app.displayName}, $mood.
            Vitals - health ${pct(app.health)}, energy ${pct(app.energy)}, mood ${pct(app.mood)}, hunger ${pct(app.hunger)}.
            You may reference your state in at most one short line.
        """.trimIndent()
    }

    /** The distiller: raw note text + live vitals + user style tweak -> a single system prompt. */
    fun systemPrompt(app: AppState, styleTweak: String = "", mode: String = "Reviewer"): String {
        val tweak = styleTweak.trim().takeIf { it.isNotEmpty() }
        return buildString {
            append(core); append("\n\n")
            append("Mode: $mode."); append("\n\n")
            append(reviewerRules); append("\n\n")
            append(vitals(app))
            if (tweak != null) append("\n\nFor this one, adjust your tone like this: $tweak")
            append("\n\nRespond with the finished $mode in plain text. Use # for headings and - for bullets. No code fence.")
        }
    }

    /** The user half of the request: the note body plus whatever the user typed as their ask. */
    fun userPrompt(notes: String, ask: String = ""): String {
        val body = notes.trim().ifEmpty { "(the note is empty - plan a reviewer from the subject line)" }
        val extra = ask.trim().takeIf { it.isNotEmpty() }?.let { "\n\nAlso: $it" }.orEmpty()
        return "Notes to review:\n$body$extra"
    }

    // ---------------------------------------------------------- chat mode (the "Ask Dr. Eh" screen)

    /** System prompt for casual chat: same persona, plus awareness of the app's loop and his stats. */
    fun chatSystem(app: AppState): String = buildString {
        append(core); append("\n\n")
        append("You are now in casual chat mode on the companion page of the same app.")
        append(" Keep replies short and natural - 1 to 3 plain sentences, no headings.")
        append("\n\nYou know the app's loop, so you can coach the student: tasks feed your vitals and give XP,")
        append(" a full EXP bar raises your level (+25 coins), calendar events lift your mood,")
        append(" reviewers lift your energy and give +15 XP, and reward cards unlock at lifetime XP gates")
        append(" of 10 / 30 / 60 / 90 / 120 / 150 and claim +10 coins each.")
        append("\n\n"); append(vitals(app))
        append("\n\nLevel ${app.level}, streak ${app.streak} day(s), ${app.completedTasks} tasks done, coins ${app.coins}.")
        append("\n\nNever invent fake features; if asked something the app cannot do, say so plainly.")
    }

    /** What Dr. Eh says without a key configured - still useful, still in character. */
    fun offlineReply(app: AppState, text: String): String {
        fun pct(v: Float) = "${(v.coerceIn(0f, 1f) * 100).toInt()}%"
        val msg = text.lowercase()
        val pending = app.tasks.filter { !it.done }
        return when {
            msg.containsAny("hi", "hello", "hey", "kumusta") ->
                "Hey ${app.displayName}. ${app.streak}-day streak going."
            msg.containsAny("task", "due", "homework", "assign") ->
                if (pending.isEmpty()) "No open tasks. Add one and I feel full."
                else "${pending.size} waiting: " + pending.take(3).joinToString { it.title } + ". Finish one - I take +10 XP."
            msg.containsAny("level", "xp", "exp") ->
                "LV ${app.level}, EXP bar at ${pct(app.exp)}. ${app.xpForLevel(app.level) - app.xp} XP to level up."
            msg.containsAny("reward", "prize", "coin") ->
                "Next reward unlocks at ${app.xpGates.firstOrNull { app.totalXp < it } ?: "${app.xpGates.last()}"}+ lifetime XP (you are at ${app.totalXp})."
            msg.containsAny("streak") ->
                "Streak is at ${app.streak}. Do one task a day and it holds."
            else -> {
                val low = listOf("energy" to app.energy, "mood" to app.mood, "hunger" to app.hunger, "health" to app.health)
                    .minByOrNull { it.second }
                "Bars: health ${pct(app.health)}, energy ${pct(app.energy)}, mood ${pct(app.mood)}, hunger ${pct(app.hunger)}. " +
                    "Lowest is ${low?.first} - a task or a reviewer fixes that (+10/+15 XP)."
            }
        }
    }

    private fun String.containsAny(vararg needles: String) = needles.any { lowercase().contains(it) }
}
