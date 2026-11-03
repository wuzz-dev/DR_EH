package com.dreh.app.ui

import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.composed
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage

/**
 * Every Figma frame is 412 x 917. Coordinates in the screens below are the EXACT Figma x/y/width/height;
 * LocalScale maps the 412-wide design onto the real device width.
 */
val LocalScale = compositionLocalOf { 1f }
val LocalFrameHeight = compositionLocalOf { 917f }

@Composable fun sc(v: Float): Dp = (v * LocalScale.current).dp
@Composable fun ssp(v: Float): TextUnit = (v * LocalScale.current).sp

@Composable
fun DesignFrame(fitHeight: Boolean = true, content: @Composable BoxScope.() -> Unit) {
    // full system safe area: status bar on top AND the gesture/nav bar at the bottom, so the
    // Figma bottom nav (which reaches y=917) never hides under the gesture area.
    BoxWithConstraints(Modifier.fillMaxSize().background(Paper)
        .windowInsetsPadding(WindowInsets.systemBars)) {
        // fit the 412 x 917 design into the safe area: width-fit normally, but shrink a hair
        // when the height doesn't fit - keeps the top stats row and the bottom nav from
        // colliding with the system bars on short screens.
        val widthScale = maxWidth.value / 412f
        val scale = if (fitHeight) minOf(widthScale, maxHeight.value / 917f) else widthScale
        val frameHeight = maxHeight.value / scale
        CompositionLocalProvider(LocalScale provides scale, LocalFrameHeight provides frameHeight) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Box(Modifier.width((412f * scale).dp), content = content)
            }
        }
    }
}

/** Absolute placement at Figma coordinates (x, y) with optional Figma size (w, h). */
@Composable
fun Modifier.at(x: Float, y: Float, w: Float? = null, h: Float? = null): Modifier {
    val s = LocalScale.current
    return this.layout { measurable, constraints ->
        val wPx = w?.let { (it * s).dp.roundToPx() }
        val hPx = h?.let { (it * s).dp.roundToPx() }
        // A scroll container hands its children an infinite maxHeight, which Compose refuses to
        // measure. Cap it so FText/FBox without an explicit height still lay out.
        val c = androidx.compose.ui.unit.Constraints(
            minWidth = wPx ?: 0, maxWidth = wPx ?: androidx.compose.ui.unit.Constraints.Infinity,
            minHeight = hPx ?: 0, maxHeight = hPx ?: androidx.compose.ui.unit.Constraints.Infinity
        )
        val p = measurable.measure(c)
        // Report the ACTUAL measured size when no width/height was given - reporting the raw
        // constraints crashed whenever a child sat inside a scroll container (infinite height).
        layout(wPx ?: p.width, hPx ?: p.height) {
            p.place((x * s).dp.roundToPx(), (y * s).dp.roundToPx())
        }
    }
}

/**
 * Clickable without a Material ripple, replaced by the app's own press feedback: the whole
 * tapped block dip-scales to 97% while pressed, so every pill/card/button/table row reacts.
 */
@Composable
fun Modifier.tap(onClick: () -> Unit): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 800f),
        label = "press-scale"
    )
    this
        .graphicsLayer {
            scaleX = scale; scaleY = scale
        }
        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
}

@Composable
fun FText(
    text: String, x: Float, y: Float, size: Float,
    modifier: Modifier = Modifier,
    color: Color = Ink,
    weight: FontWeight = FontWeight.Normal,
    w: Float? = null,
    align: TextAlign = TextAlign.Start,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip
) {
    Text(
        text = text, color = color, fontSize = ssp(size), fontFamily = DrEhFont,
        fontWeight = weight, textAlign = align, softWrap = w != null,
        maxLines = maxLines, overflow = overflow,
        modifier = Modifier.at(x, y, w).then(modifier)
    )
}

@Composable
fun FBox(
    x: Float, y: Float, w: Float, h: Float,
    radius: Float = 14f, fill: Color = Paper, border: Color? = Ink,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val shape = RoundedCornerShape(sc(radius))
    var m = Modifier.at(x, y, w, h).clip(shape).background(fill, shape)
    if (border != null) m = m.border(sc(1f), border, shape)
    Box(m.then(modifier), content = content)
}

/** Loads a drawable by its (normalised) Figma layer name, e.g. "tired 3" -> R.drawable.tired_3. Draws nothing if missing. */
@Composable
fun FImage(
    name: String, x: Float, y: Float, w: Float, h: Float,
    contentScale: ContentScale = ContentScale.Fit,
    onClick: (() -> Unit)? = null,
    contentDescription: String? = null
) {
    val ctx = LocalContext.current
    val id = remember(name) { ctx.resources.getIdentifier(name, "drawable", ctx.packageName) }
    var m = Modifier.at(x, y, w, h)
    if (onClick != null) m = m.tap(onClick)
    Box(m) {
        if (id != 0) Image(painterResource(id), contentDescription, Modifier.fillMaxSize(), contentScale = contentScale)
    }
}

@Composable
fun FButton(label: String, x: Float, y: Float, w: Float, h: Float, filled: Boolean = false,
            weight: FontWeight = FontWeight.Normal, onClick: () -> Unit) {
    FBox(x, y, w, h, fill = if (filled) Ink else Paper, modifier = Modifier.tap(onClick)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(label, color = if (filled) Paper else Ink, fontSize = ssp(14f), fontFamily = DrEhFont,
                fontWeight = weight, softWrap = false)
        }
    }
}

@Composable
fun FField(value: String, onChange: (String) -> Unit, placeholder: String, x: Float, y: Float,
           w: Float = 279f, h: Float = 40f, password: Boolean = false,
           keyboardType: KeyboardType = KeyboardType.Text,
           imeAction: ImeAction? = null, onDone: (() -> Unit)? = null) {
    val focus = LocalFocusManager.current
    FBox(x, y, w, h) {
        BasicTextField(
            value = value, onValueChange = onChange, singleLine = true,
            modifier = Modifier.fillMaxSize().padding(horizontal = sc(20f)),
            textStyle = TextStyle(color = Ink, fontSize = ssp(14f), fontFamily = DrEhFont),
            cursorBrush = SolidColor(Ink),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (password) KeyboardType.Password else keyboardType,
                imeAction = imeAction ?: if (password) ImeAction.Done else ImeAction.Next
            ),
            keyboardActions = KeyboardActions(
                onNext = { focus.moveFocus(FocusDirection.Down) },
                onDone = { if (onDone != null) onDone() else focus.clearFocus() }
            ),
            decorationBox = { inner ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) Text(placeholder, color = PlaceholderGray, fontSize = ssp(14f),
                        fontFamily = DrEhFont, softWrap = false)
                    inner()
                }
            }
        )
    }
}

@Composable
fun FCheck(checked: Boolean, x: Float, y: Float, size: Float = 10f, onToggle: () -> Unit) {
    Box(Modifier.at(x, y, size, size).border(sc(1f), Ink).background(if (checked) Ink else Paper).tap(onToggle))
}

@Composable
fun NameDialog(title: String, initial: String = "", onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var v by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Paper,
        title = { Text(title, fontFamily = DrEhFont, color = Ink) },
        text = {
            OutlinedTextField(v, { v = it }, singleLine = true, textStyle = TextStyle(fontFamily = DrEhFont))
        },
        confirmButton = {
            TextButton(onClick = { if (v.isNotBlank()) onConfirm(v.trim()) }) {
                Text("OK", fontFamily = DrEhFont, color = Ink)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL", fontFamily = DrEhFont, color = Ink) } }
    )
}

@Composable
fun toast(): (String) -> Unit {
    val ctx = LocalContext.current
    return { msg -> Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show() }
}

// ------------------------------------------------------------------ bottom navigation (same on every main frame)

enum class Tab(
    val route: String, val label: String, val icon: String,
    val ix: Float, val iy: Float, val iw: Float, val ih: Float,
    val lx: Float, val hx: Float
) {
    HOME("home", "home", "home_1", 25f, 821f, 60f, 60f, 39f, 11f),
    TASKS("tasks", "tasks", "task_1", 128f, 821f, 60f, 60f, 142f, 114f),
    REWARDS("rewards", "rewards", "reward_1", 231f, 821f, 60f, 60f, 239f, 217f),
    PROFILE("profile", "profile", "prof_1", 326f, 827f, 60f, 47f, 338f, 312f)
}

@Composable
fun BottomNav(selectedTab: Tab?, onSelect: (Tab) -> Unit) {
    FBox(-11f, 798f, 433f, 148f, radius = 20f, fill = Unverified.NavBar, border = null)
    selectedTab?.let { FBox(it.hx, 798f, 87f, 122f, radius = 20f, fill = Unverified.NavHighlight, border = null) }
    Tab.entries.forEach { t ->
        FImage(t.icon, t.ix, t.iy, t.iw, t.ih)
        FText(t.label, t.lx, 876f, 12f)
        Box(Modifier.at(t.hx, 798f, 87f, 122f).tap { onSelect(t) }.semantics {
            contentDescription = "${t.label} tab"
            role = Role.Tab
            selected = t == selectedTab
        })
    }
}

// ------------------------------------------- "Rectangle 71" + "chevron-down 2": the back pill
@Composable
fun BackPill(x: Float, y: Float, onBack: () -> Unit) {
    FBox(x, y, 60f, 42f, modifier = Modifier.tap(onBack).semantics {
        contentDescription = "Back"
        role = Role.Button
    }) {
        Box(Modifier.at(12f, 2f, 37f, 37f).rotate(90f)) { FImage("chevron_down", 0f, 0f, 37f, 37f) }
    }
}

// ------------------------------------------------------------------ Dr. Eh stat tiles (HEALTH / ENERGY / MOOD / HUNGRY / EXP)

@Composable
fun StatsHeader(health: Float, energy: Float, mood: Float, hunger: Float) {
    val tiles = listOf(
        Triple("stat_health", health, 62f), Triple("stat_energy", energy, 122f),
        Triple("stat_mood", mood, 182f), Triple("stat_hungry", hunger, 242f)
    )
    tiles.forEach { (icon, v, x) ->
        StatTile(icon, v, x)
    }
}

@Composable
fun ExpTile(exp: Float) = StatTile("stat_exp", exp, 302f)

/** 47x47 tile: fill level (bottom-up) under the supplied tile SVG (icon + square border).
 *  The fill animates when the level changes, so bars visibly fill when tasks / XP land -
 *  at rest it looks exactly like the Figma frames. */
@Composable
private fun StatTile(drawable: String, level: Float, x: Float) {
    val fill by androidx.compose.animation.core.animateFloatAsState(
        targetValue = level.coerceIn(0f, 1f),
        animationSpec = androidx.compose.animation.core.tween(600, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "stat-fill"
    )
    FBox(x, 69f, 47f, 47f, radius = 0f, border = null) {
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().fillMaxHeight(fill)
            .background(Unverified.StatFill))
        FImage(drawable, 0f, 0f, 47f, 47f)
    }
}
