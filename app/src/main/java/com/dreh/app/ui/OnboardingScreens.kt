package com.dreh.app.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.material3.Text
import kotlinx.coroutines.delay
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import com.dreh.app.AppState
// ---------------------------------------------------------------- the splash loader (first open only)

/** Themed splash: the wordmark + companion sketch breathe while three ink dots load, then
 *  the whole thing fades over to the start frame. Pure design language - no text UI. */
@Composable
fun SplashScreen(onDone: () -> Unit) = DesignFrame {
    val float = rememberInfiniteTransition(label = "splash")
    val bob = float.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "spbob"
    )
    LaunchedEffect(Unit) { delay(1900); onDone() }

    FBox(-9f, 0f, 424f, 917f, radius = 0f, border = null)
    val frameS = LocalScale.current
    Box(Modifier.at(107f, 268f, 197f, 56f).graphicsLayer {
        translationY = bob.value * 8f * frameS * density
    }) { FImage("dr_eh_wordmark_start", 0f, 0f, 197f, 56f) }
    FImage("tired_3", 118f, 340f - bob.value * 6f, 176f, 176f, ContentScale.Crop)
    // loader dots: three tiny ink circles pulsing left to right
    val dots = float.animateFloat(0.2f, 1f,
        infiniteRepeatable(tween(620), RepeatMode.Reverse), label = "sd")
    listOf(178f, 198f, 218f).forEachIndexed { i, x ->
        val alpha = (dots.value - i * 0.15f).coerceIn(0.15f, 1f)
        FBox(x, 548f, 9f, 9f, radius = 4.5f, fill = Ink.copy(alpha = alpha), border = null)
    }
    FText("loading your companion...", 0f, 580f, 11f, w = 412f, align = TextAlign.Center, color = PlaceholderGray)
}
@Composable
fun StartScreen(onSignUp: () -> Unit, onLogIn: () -> Unit) = DesignFrame {
    FImage("tired_3", 98f, 279f, 216f, 216f, ContentScale.Crop)
    FImage("dr_eh_wordmark_start", 107f, 203f, 197f, 56f)   // "Dr.Eh" wordmark (Figma slot 110,206 + 3px stroke padding)
    FText("Take Care of Your Tasks. \nTake Care of Dr. Eh.", 0f, 518f, 15f, w = 412f, align = TextAlign.Center)
    FButton("SIGN UP", 143f, 616f, 107f, 40f, onClick = onSignUp)
    FButton("LOG IN", 144f, 672f, 107f, 40f, onClick = onLogIn)
}

// ---------------------------------------------------------------- frame "start up"
@Composable
fun IntroScreen(onNext: () -> Unit) = DesignFrame {
    FImage("tired_2", 81f, 149f, 400f, 400f, ContentScale.Crop)
    FText("Meet Dr. Eh", 51f, 89f, 32f)
    FText("Dr. Eh is your academic companion.", 51f, 573f, 20f, w = 303f)
    FText("The more you take care of your responsibilities, the healthier and happier Dr. Eh becomes!",
        51f, 665f, 16f, w = 320f)
    FButton("NEXT", 271f, 800f, 83f, 40f, filled = true, weight = FontWeight.Medium, onClick = onNext)
}

// ---------------------------------------------------------------- frame "sign up"
@Composable
fun SignUpScreen(app: AppState, onCreated: () -> Unit, onLogIn: () -> Unit) = DesignFrame {
    val say = toast()
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var female by remember { mutableStateOf(false) }
    var male by remember { mutableStateOf(false) }
    var terms by remember { mutableStateOf(false) }

    FImage("prof_2", 108f, 214f, 195f, 157f, ContentScale.Crop)
    FImage("dr_eh_wordmark_auth", 136.3f, 164f, 140f, 42f)
    /** Extra UX: the last field's keyboard "Done" submits the form, like tapping CREATE ACCOUNT. */
    fun create() {
        val emailOk = android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
        when {
            name.isBlank() || email.isBlank() || pass.isBlank() -> say("Please fill in every field")
            !emailOk -> say("That doesn't look like a valid email")
            pass.length < 6 -> say("Password needs at least 6 characters")
            pass != confirm -> say("Passwords do not match")
            !terms -> say("Please agree to the Terms & Conditions")
            else -> {
                app.displayName = name.trim(); app.email = email.trim()
                app.password = pass; app.session = true
                onCreated()
            }
        }
    }

    FField(name, { name = it }, "Display Name", 66f, 404f)
    FField(email, { email = it }, "Email Address", 66f, 456f, keyboardType = KeyboardType.Email)
    FField(pass, { pass = it }, "Password", 66f, 508f, password = true)
    FField(confirm, { confirm = it }, "Confirm Password", 66f, 560f, password = true,
        imeAction = ImeAction.Done, onDone = ::create)

    FButton("CREATE ACCOUNT", 119f, 683f, 173f, 40f) { create() }

    FCheck(female, 127f, 622f) { female = true; male = false }
    FText("Female", 148f, 618f, 12f, Modifier.tap { female = true; male = false })
    FCheck(male, 219f, 622f) { male = true; female = false }
    FText("Male", 240f, 618f, 12f, Modifier.tap { male = true; female = false })

    FCheck(terms, 73f, 654f) { terms = !terms }
    FText("I agree to the Terms & Conditions", 94f, 650f, 12f, Modifier.tap { terms = !terms })

    FButton("CREATE ACCOUNT", 119f, 683f, 173f, 40f) {
        when {
            name.isBlank() || email.isBlank() || pass.isBlank() -> say("Please fill in every field")
            pass != confirm -> say("Passwords do not match")
            !terms -> say("Please agree to the Terms & Conditions")
            else -> { app.displayName = name.trim(); app.email = email.trim(); onCreated() }
        }
    }

    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = PlaceholderGray)) { append("Already have an account? ") }
            withStyle(SpanStyle(color = Ink)) { append("Log In") }
        },
        fontSize = ssp(12f), fontFamily = DrEhFont, softWrap = false,
        modifier = Modifier.at(91f, 734f).tap(onLogIn)
    )
}

// ---------------------------------------------------------------- frame "log in" (183:42)
@Composable
fun LogInScreen(app: AppState, onLoggedIn: () -> Unit, onSignUp: () -> Unit) = DesignFrame {
    val say = toast()
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }

    FImage("prof_2", 108f, 304f, 195f, 157f, ContentScale.Crop)
    FImage("dr_eh_wordmark_auth", 136.3f, 254f, 140f, 42f)
    fun logIn() {
        val okEmail = android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
        when {
            email.isBlank() || pass.isBlank() -> say("Enter your email and password")
            !okEmail -> say("That doesn't look like a valid email")
            // if an account was created in this install, the credentials must actually match
            app.password.isNotBlank() && (app.email != email.trim() || app.password != pass) ->
                say("Wrong email or password for this account")
            else -> {
                if (app.email.isBlank()) app.email = email.trim()
                app.session = true
                onLoggedIn()
            }
        }
    }

    FField(email, { email = it }, "Email Address", 66f, 495f, keyboardType = KeyboardType.Email)
    FField(pass, { pass = it }, "Password", 66f, 547f, password = true,
        imeAction = ImeAction.Done, onDone = ::logIn)
    FText("Forgot password?", 214f, 591f, 12f, Modifier.tap { say("Password reset is not part of the prototype") })
    FButton("LOG IN", 119f, 621f, 173f, 40f) { logIn() }
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = PlaceholderGray)) { append("Don’t have an account? ") }
            withStyle(SpanStyle(color = Ink)) { append("Sign up") }
        },
        fontSize = ssp(12f), fontFamily = DrEhFont, softWrap = false,
        modifier = Modifier.at(91f, 673f).tap(onSignUp)
    )
}

// ---------------------------------------------------------------- frame "log in" (284:864) - Choose a profile
@Composable
fun ChooseProfileScreen(app: AppState, onConfirm: () -> Unit) = DesignFrame {
    var selected by remember { mutableStateOf("avatar_4") }
    FText("Choose a profile", 88f, 133f, 24f, w = 236f, align = TextAlign.Center)

    Box(Modifier.at(88f, 209f, 235f, 235f).clip(CircleShape).border(sc(1f), Ink, CircleShape)) {
        FImage(if (selected == "avatar_4") "avatar_4_big" else selected, 0f, 0f, 235f, 235f, ContentScale.Crop)
    }

    val grid = listOf(
        Triple("avatar_1", 60f, 503f), Triple("avatar_2", 164f, 503f), Triple("avatar_3", 268f, 503f),
        Triple("avatar_4", 60f, 605f), Triple("avatar_5", 164f, 605f), Triple("avatar_6", 268f, 605f)
    )
    grid.forEach { (n, x, y) -> FImage(n, x, y, 83f, 83f, ContentScale.Crop, onClick = { selected = n }) }

    FBox(143f, 766f, 125f, 64f, modifier = Modifier.tap { app.avatar = selected; onConfirm() }) {
        FImage("check", 36f, 5f, 53f, 53f)
    }
}

// ---------------------------------------------------------------- frame "log in" (284:920) - type of student
@Composable
fun StudentTypeScreen(app: AppState, onDone: () -> Unit) = DesignFrame {
    FText("What type of student are you?", 51f, 362f, 24f, w = 310f, align = TextAlign.Center)
    listOf("High School" to 471f, "College" to 519f).forEach { (label, y) ->
        FBox(100f, y, 212f, 37f, modifier = Modifier.tap { app.studentType = "$label Student"; onDone() }) {
            Box(Modifier.at(0f, 0f, 212f, 37f)) {
                Text(label, fontSize = ssp(24f), fontFamily = DrEhFont, color = Ink, softWrap = false,
                    modifier = Modifier.align(androidx.compose.ui.Alignment.Center))
            }
        }
    }
}
