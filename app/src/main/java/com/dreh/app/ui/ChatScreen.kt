package com.dreh.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import com.dreh.app.AppState
import com.dreh.app.ai.AiClient
import com.dreh.app.ai.AiConfig
import com.dreh.app.ai.AiStore
import com.dreh.app.ai.ChatMsg
import com.dreh.app.ai.Persona
import kotlinx.coroutines.launch

/**
 * "Ask Dr. Eh" as a natural chat: his replies run through AiClient.chat (OpenAI / Groq compatible),
 * driven by Persona.chatSystem(app) so he always knows his own vitals, your tasks and the XP loop.
 * Without a key he still answers from Persona.offlineReply - and the gear button opens AiApiDialog
 * to plug the API key in.
 */
@Composable
fun ChatScreen(app: AppState, store: AiStore, onBack: () -> Unit) = DesignFrame(fitHeight = false) {
    val ai = remember(store) { ChatState(store) }
    var draft by remember { mutableStateOf("") }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val keyboardVisible = imeBottomPx > 0
    val frameHeight = LocalFrameHeight.current
    val imeBottomDesign = with(density) { imeBottomPx.toDp().value } / LocalScale.current
    var restingFrameHeight by remember { mutableFloatStateOf(frameHeight) }
    LaunchedEffect(keyboardVisible, frameHeight) {
        if (!keyboardVisible) restingFrameHeight = frameHeight
    }
    val windowResizedForKeyboard = keyboardVisible && frameHeight < restingFrameHeight - 24f
    val usableHeight = if (keyboardVisible && !windowResizedForKeyboard) {
        frameHeight - imeBottomDesign
    } else frameHeight
    val composerY = (usableHeight - 46f - if (keyboardVisible) 14f else 55f).coerceAtLeast(140f)
    val listeningY = minOf(500f, frameHeight - 411f).coerceAtLeast(360f)
    val conversationTop = if (keyboardVisible) 150f else 280f
    val conversationBottom = if (keyboardVisible) composerY - 28f else minOf(482f, listeningY - 18f)
    val conversationHeight = (conversationBottom - conversationTop).coerceAtLeast(120f)

    FText("ASK DR. EH", 118f, 60f, 20f, w = 177f, align = TextAlign.Center)
    BackPill(27f, 60f, onBack)
    FImage("cog_1", 352f, 62f, 32f, 32f, onClick = { ai.showSettings = true },
        contentDescription = "AI settings")

    // The seated companion anchors the conversation when the keyboard is closed.
    if (!keyboardVisible) {
        FGif("listening", 8f, listeningY, 270f, 310f, ContentScale.Fit,
            contentDescription = "Dr. Eh listening from a red chair")
    }

    // The message area scrolls independently, leaving the companion and composer in place.
    Column(
        Modifier.at(18f, conversationTop, 375f, conversationHeight).verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(sc(12f))
    ) {
        ai.log.forEach { m -> ChatLine(m.content, fromUser = m.role == "user") }
        // typing indicator while the API round-trip runs
        if (ai.typing) ChatLine("Dr. Eh is typing...", fromUser = false, ghost = true)
        LaunchedEffect(ai.log.size, ai.typing, keyboardVisible) {
            scroll.animateScrollTo(scroll.maxValue)
        }
    }

    // input row: field + send, in the frame's own style
    FBox(38f, composerY, 274f, 46f, radius = 23f) {
        BasicTextField(
            value = draft, onValueChange = { draft = it }, singleLine = true,
            modifier = Modifier.fillMaxSize().padding(horizontal = sc(16f)),
            textStyle = TextStyle(color = Ink, fontSize = ssp(14f), fontFamily = DrEhFont),
            cursorBrush = SolidColor(Ink),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = {
                if (draft.isNotBlank()) { ai.send(scope, app, draft); draft = "" }
            }),
            decorationBox = { inner ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                    if (draft.isEmpty()) Text("Message Dr. Eh...", color = PlaceholderGray,
                        fontSize = ssp(14f), fontFamily = DrEhFont, softWrap = false)
                    inner()
                }
            }
        )
    }
    FBox(329f, composerY, 60f, 46f, radius = 23f, fill = Ink, border = null,
        modifier = Modifier.tap {
            if (draft.isNotBlank()) { ai.send(scope, app, draft); draft = "" }
        }) {
        Text("›", color = Paper, fontSize = ssp(28f), fontFamily = DrEhFont,
            modifier = Modifier.align(Alignment.Center))
    }

    if (ai.showSettings) {
        AiApiDialog(ai.config, { ai.update(it) }) { ai.showSettings = false }
    }
}

/** Minimal chat holder: history, typing flag and its own copy of the API config. */
private class ChatState(private val store: AiStore) {
    var config by mutableStateOf(store.load())
    var typing by mutableStateOf(false)
    var showSettings by mutableStateOf(false)
    val log = mutableStateListOf<ChatMsg>()

    fun update(c: AiConfig) { config = c; store.save(c) }

    init {
        log.add(ChatMsg("assistant",
            "Hey. I'm Dr. Eh - ask me about your tasks, your level, or just talk. I read my own bars."))
    }

    fun send(scope: kotlinx.coroutines.CoroutineScope, app: AppState, text: String) {
        log.add(ChatMsg("user", text))
        typing = true
        val question = text.trim()
        scope.launch {
            val answer: String = if (config.isConfigured) {
                try {
                    // the API hookup: Persona builds the same Dr. Eh voice with live vitals/tasks/XP
                    AiClient.chat(config, Persona.chatSystem(app),
                        log.dropLast(1).map { ChatMsg(if (it.role == "assistant") "assistant" else "user", it.content) }
                            .plus(ChatMsg("user", question)))
                } catch (e: Exception) {
                    "(connection failed - ${e.message?.take(120) ?: "try again"}) " + Persona.offlineReply(app, question)
                }
            } else Persona.offlineReply(app, question)
            log.add(ChatMsg("assistant", answer.trim()))
            typing = false
            app.talk()   // the companion visibly talks back on the home page, too
        }
    }
}

// ------------------------------------------------------------------ bubble (same Ink/Paper boxes as the cards)

@Composable
private fun ChatLine(text: String, fromUser: Boolean, ghost: Boolean = false) {
    val shape = if (fromUser) {
        RoundedCornerShape(topStart = sc(18f), topEnd = sc(5f), bottomEnd = sc(18f), bottomStart = sc(18f))
    } else {
        RoundedCornerShape(topStart = sc(5f), topEnd = sc(18f), bottomEnd = sc(18f), bottomStart = sc(18f))
    }
    AnimatedVisibility(
        visible = true,
        modifier = Modifier.fillMaxWidth(),
        enter = fadeIn(tween(180)) + slideInVertically(tween(180)) { it / 10 }
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = if (fromUser) Arrangement.End else Arrangement.Start) {
            Column(Modifier.width(sc(if (fromUser) 190f else 274f))) {
                if (!fromUser) {
                    Text("DR. EH", color = PlaceholderGray, fontSize = ssp(9f), fontFamily = DrEhFont,
                        modifier = Modifier.padding(start = sc(10f), bottom = sc(4f)))
                }
                Box(
                    Modifier.fillMaxWidth().animateContentSize(tween(180)).clip(shape)
                        .background(if (fromUser) Ink else Paper, shape)
                        .border(sc(1f), Ink, shape).padding(sc(14f), sc(10f))
                ) {
                    Text(
                        text, color = if (fromUser) Paper else if (ghost) PlaceholderGray else Ink,
                        fontSize = ssp(13f), fontFamily = DrEhFont
                    )
                }
            }
        }
    }
}
