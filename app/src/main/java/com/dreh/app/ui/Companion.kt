package com.dreh.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dreh.app.AppState
import com.dreh.app.PetState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The living Dr. Eh layer - the same Figma coordinates and the same assets as the home frames,
 * plus: gentle idle breathing, smooth crossfades between moods, a springy pulse whenever XP
 * lands, and a "+n XP" tag that rises from him. Every element is drawn
 * with the plain Figma primitives (FBox / FText / FImage / FGif) so it stays on-theme.
 */

// ------------------------------------------------------------------ the companion itself

/** Bin + mood gif, crossfaded per emotion, breathing at idle, pulsing on XP.
 *  The gif is never swapped just for a cheer: every AnimatedContent fade of a ~14 MB gif
 *  reads as ghosting/half-flicker on slower phones, so crossfades are reserved for real mood
 *  changes. The companion is visual feedback and does not react to taps with a body wiggle. */
@Composable
fun CompanionStage(app: AppState) {
    val pet = app.pet
    if (pet != PetState.TIRED) FImage("bin_2", 81f, 148f, 331f, 400f, ContentScale.Crop)
    if (pet == PetState.DEAD) return   // the dead frame draws its veil + tombstone on top instead

    // heartbeat pulse when XP lands, around the gif's centre
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(app.cheerTick) {
        if (app.cheerTick == 0) return@LaunchedEffect
        pulse.animateTo(1.035f, spring(dampingRatio = 0.45f, stiffness = 380f))
        pulse.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 240f))
    }

    // gentle breathing loop; read straight into the draw layer so no recomposition is needed
    val breath = rememberInfiniteTransition(label = "breath")
    val bob = breath.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bob"
    )

    Box {
        AnimatedContent(pet, transitionSpec = {
            fadeIn(tween(650)) togetherWith fadeOut(tween(450))
        }, label = "pet-gif") { state ->
            when (state) {
                // Keep the visible character centered in the phone viewport. The source art has
                // more visual weight on its left side, so these small per-mood offsets compensate
                // for the transparent/cropped frame bounds rather than changing the artwork.
                PetState.NEUTRAL -> PetGif("pet_neutral", 4f, 184f, 471f, 471f, pulse, bob)
                PetState.HAPPY -> PetGif("pet_happy", 4f, 184f, 471f, 471f, pulse, bob)
                PetState.TIRED -> PetGif("pet_tired", 40f, 149f, 400f, 400f, pulse, bob)
                PetState.SICK -> PetGif("pet_sick", 20f, 239f, 439f, 439f, pulse, bob)
                else -> Unit
            }
        }
    }
}

/** One mood gif with the pulse/breath applied in one graphics layer (draw-only, cheap). */
@Composable
private fun PetGif(
    name: String, x: Float, y: Float, w: Float, h: Float,
    pulse: Animatable<Float, AnimationVector1D>, bob: State<Float>,
    contentScale: ContentScale = ContentScale.Crop
) {
    val s = LocalScale.current
    Box(Modifier.at(x, y, w, h).graphicsLayer {
        scaleX = pulse.value
        scaleY = pulse.value
        translationY = bob.value * 5f * s * density   // ~5 design px of breathing
    }) {
        FGif(name, 0f, 0f, w, h, contentScale)
    }
}

// ------------------------------------------------------------------ the small "echo" companion (chat header)

/** The little companion in the Ask-Dr.-Eh corner: same mood gif the big one uses, breathing,
 *  pulses whenever you tap him or the chat answers, and taps make him talk. Decodes at the big
 *  companion's size so the gif is shared with home's Coil cache instead of freezing on a
 *  downsampled first frame. */
@Composable
fun CompanionEcho(app: AppState, x: Float, y: Float, w: Float, h: Float) {
    val pet = if (app.pet == PetState.DEAD) PetState.TIRED else app.pet
    val breath = rememberInfiniteTransition(label = "echo-breath")
    val bob = breath.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "e-bob"
    )
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(app.talkTick, app.bopTick) {
        if (app.talkTick + app.bopTick == 0) return@LaunchedEffect
        pulse.animateTo(1.08f, spring(dampingRatio = 0.4f, stiffness = 400f))
        pulse.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 240f))
    }
    val s = LocalScale.current
    val srcPx = with(LocalDensity.current) { (471f * s).dp.roundToPx().coerceIn(160, 900) }
    Box(modifier = Modifier.at(x, y, w, h).tap { app.talk() }) {
        Box(Modifier.graphicsLayer {
            scaleX = pulse.value; scaleY = pulse.value
            translationY = bob.value * 3f * s * density
        }) {
            FGif(
                when (pet) {
                    PetState.HAPPY -> "pet_happy"
                    PetState.SICK -> "pet_sick"
                    PetState.TIRED -> "pet_tired"
                    else -> "pet_neutral"
                },
                0f, 0f, w, h, ContentScale.Fit, srcSizePx = srcPx
            )
        }
    }
}

private val IDLE_LINES = listOf(
    "Any tasks for me today?",
    "Finish tasks - my bars fill up!",
    "A streak a day keeps me strong.",
    "Make a reviewer - I feel stronger.",
    "Ask the AI, then quiz me?"
)

// ------------------------------------------------------------------ speech bubble (ties the pet to the bars)

/** Dr. Eh talks through a little paper bubble; his lines react to his own bars and your tasks.
 *  Talking animation: green "speaking" dots, then the line renders w/ a typewriter reveal -
 *  each talkTick (claim, XP landing, bubble change) plays the sequence again. */
@Composable
fun SpeechBubble(app: AppState) {
    val line = lineFor(app) ?: return
    var revealed by remember { mutableStateOf(0) }
    var talking by remember { mutableStateOf(true) }
    // blink while Dr. Eh is "speaking": made of two states, in/out dots pulse
    val dots = rememberInfiniteTransition(label = "talk")
    val dotA = dots.animateFloat(
        initialValue = 0.25f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(320), RepeatMode.Reverse), label = "dotA"
    )
    val dotB = dots.animateFloat(
        initialValue = 1f, targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(320, delayMillis = 160, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "dotB"
    )
    LaunchedEffect(line, app.talkTick) {
        talking = true
        revealed = 0
        delay(400)                     // dots blink while he starts talking
        // type it out ~1 letter per 18ms
        while (revealed < line.length) { revealed += 1; delay(18) }
        delay(500)
        talking = false
    }
    val shown = line.take(revealed.coerceAtMost(line.length))
    val stroke = sc(1f)   // Border width; resolved to px in the draw scope (DrawScope has toPx())
    FBox(96f, 150f, 216f, 62f, radius = Unverified.CardRadius)
    Canvas(Modifier.at(96f + 100f, 150f + 61f, 18f, 12f)) {   // the little tail near his head
        val p = Path().apply {
            moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width * 0.25f, size.height); close()
        }
        drawPath(p, Paper)
        drawPath(p, Ink, style = Stroke(stroke.toPx()))
    }
    FText(shown, 112f, 158f, 13f, w = 184f, align = TextAlign.Center)
    if (talking && revealed == 0) {
        // "..."-style speech pulse in place of the text - only before the words start
        // appearing, so the dots can never collide with what he is saying
        FBox(188f, 163f, 24f, 6f, radius = 3f, fill = Paper, border = null)
        FBox(189f, 164f, 4f, 4f, radius = 2f, fill = Ink.copy(alpha = dotA.value), border = null)
        FBox(195f, 164f, 4f, 4f, radius = 2f, fill = Ink.copy(alpha = dotB.value), border = null)
        FBox(201f, 164f, 4f, 4f, radius = 2f, fill = Ink.copy(alpha = dotA.value), border = null)
    }
}

private fun lineFor(app: AppState): String? = when {
    app.pet == PetState.DEAD -> null
    app.levelUpPending -> "LEVEL UP! Now LV ${app.level}."
    app.health < 0.3f -> "I feel faint... make a reviewer to revive me."
    app.mood < 0.3f -> "Feeling down... a reviewer would cheer me."
    app.hunger < 0.3f -> "Feed me a task, I'm starving."
    app.energy < 0.3f -> "Low energy... finish one small task."
    app.pet == PetState.HAPPY -> "All bars full! Keep the streak alive."
    else -> IDLE_LINES[(app.bopTick + app.cheerTick) % IDLE_LINES.size]
}

// ------------------------------------------------------------------ "+n XP" float (the bar talks back)

/** Rises from the companion whenever XP is earned - the visible link between work and bars. */
@Composable
fun XpFloat(app: AppState) {
    if (app.pet == PetState.DEAD) return
    val rise = remember { Animatable(0f) }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(app.cheerTick) {
        if (app.cheerTick <= 0) return@LaunchedEffect
        rise.snapTo(0f); alpha.snapTo(1f)
        launch { rise.animateTo(1f, tween(1100, easing = FastOutSlowInEasing)) }
        delay(300)
        alpha.animateTo(0f, tween(800, easing = FastOutSlowInEasing))
    }
    if (alpha.value <= 0.01f || app.xpGained <= 0) return
    FText("+${app.xpGained} XP", 190f, 280f - rise.value * 64f, 16f,
        w = 100f, align = TextAlign.Center, color = Ink.copy(alpha = alpha.value))
}

// ------------------------------------------------------------------ the "LEVEL UP!" splash (a new frame, same design language)

/**
 * The level-up moment as its own frame: the dead-frame veil reused as a spotlight, the happy gif
 * celebrating above, and a plain Ink/Paper card in the Figma style. Auto-focus; tap to dismiss.
 */
@Composable
fun LevelUpOverlay(app: AppState) {
    if (!app.levelUpPending) return
    val dismiss = { app.levelUpPending = false }
    FBox(-9f, 0f, 424f, 917f, radius = 0f, fill = Unverified.DeadVeil, border = null,
        modifier = Modifier.tap(dismiss))
    FGif("pet_happy", 6f, 96f, 400f, 400f, ContentScale.Fit)
    FBox(61f, 470f, 290f, 250f, modifier = Modifier.tap(dismiss)) {
        // NOTE: content inside an FBox is placed in the box's own coordinates
        FText("LEVEL UP!", 50f, 34f, 30f, w = 190f, align = TextAlign.Center)
        FText("Welcome to LV ${app.level}", 10f, 78f, 16f, w = 270f, align = TextAlign.Center)
        FText("+25 coins", 10f, 114f, 20f, w = 270f, align = TextAlign.Center)
        FText("tap to continue", 10f, 162f, 12f, w = 270f, align = TextAlign.Center, color = PlaceholderGray)
    }
}
