package com.dreh.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

// ---- Values read directly from the Figma code (start / start up / sign up frames) ----
val Ink = Color(0xFF000000)
val Paper = Color(0xFFFFFFFF)
val PlaceholderGray = Color(0xFF9A9A9A)

/**
 * Figma font: "Intel One Mono" (Regular + Medium).
 * To use it: drop intel_one_mono_regular.ttf / intel_one_mono_medium.ttf into res/font and replace with
 *   FontFamily(Font(R.font.intel_one_mono_regular), Font(R.font.intel_one_mono_medium, FontWeight.Medium))
 */
val DrEhFont: FontFamily = FontFamily.Monospace

/**
 * Fills that the Figma API rate limit stopped me reading (nav bar, highlight, stat fills, locked cards,
 * dead-state veil). Replace with the exact hex values from Figma (Inspect panel) - one place to fix.
 */
object Unverified {
    val NavBar = Color(0xFFFFFFFF)
    val NavHighlight = Color(0xFFE6E6E6)
    val StatFill = Color(0xFFCCCCCC)
    val LockedFill = Color(0xFFD9D9D9)
    val DeadVeil = Color(0xCCD9D9D9)
    const val CardRadius = 14f
}

@Composable
fun DrEhTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(primary = Ink, onPrimary = Paper, surface = Paper, background = Paper, onSurface = Ink),
        content = content
    )
}
