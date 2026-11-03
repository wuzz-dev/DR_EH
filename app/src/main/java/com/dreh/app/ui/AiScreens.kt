package com.dreh.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dreh.app.AppState
import com.dreh.app.ai.AiClient
import com.dreh.app.ai.AiConfig
import com.dreh.app.ai.GROQ_BASE_URL
import com.dreh.app.ai.GROQ_DEFAULT_MODEL
import com.dreh.app.ai.Persona
import kotlinx.coroutines.launch

/** State holder for the whole AI flow: config, the prompt being sent, and the streamed-in result. */
class AiViewModel(private val store: com.dreh.app.ai.AiStore) {
    var config by mutableStateOf(store.load()); private set
    var running by mutableStateOf(false); private set
    var result by mutableStateOf(""); private set
    var error by mutableStateOf<String?>(null); private set

    fun update(c: AiConfig) { config = c; store.save(c); error = null }

    fun generate(
        scope: kotlinx.coroutines.CoroutineScope,
        app: AppState, notes: String, ask: String, styleTweak: String, onDone: (String) -> Unit
    ) {
        if (running) return
        if (!config.isConfigured) { error = "Add an API key first"; return }
        running = true; result = ""; error = null
        val cfg = config
        scope.launch {
            try {
                val text = AiClient.complete(cfg, Persona.systemPrompt(app, styleTweak), Persona.userPrompt(notes, ask))
                result = text
                onDone(text)
            } catch (e: Exception) {
                error = e.message ?: "Request failed"
            } finally { running = false }
        }
    }
}

/**
 * "REVIEWER-with content" + an AI block. Lets the user point at a folder/file (or nothing),
 * set the API, tune the persona, and fire a request that comes back as a formatted reviewer.
 */
@Composable
fun AiReviewerScreen(
    app: AppState,
    store: com.dreh.app.ai.AiStore,
    onBack: () -> Unit,
    onSaveInto: (Long, Long, String) -> Unit,
    onTab: (Tab) -> Unit
) = DesignFrame {
    val vm = remember(store) { AiViewModel(store) }
    var ask by remember { mutableStateOf("") }
    var style by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    // default target = first file of the first folder, so the flow works with zero setup
    val folder = app.folders.firstOrNull()
    val target = folder?.files?.firstOrNull()
    val notes = target?.text.orEmpty()

    val say = toast()

    FText("AI REVIEWER", 86f, 58f, 30f, w = 240f, align = TextAlign.Center)
    Box(Modifier.at(27f, 60f, 60f, 42f).tap(onBack)) {
        Box(Modifier.at(12f, 2f, 37f, 37f)) { FImage("chevron_down", 0f, 0f, 37f, 37f) }
    }

    // No verticalScroll here: a scroll container reports an infinite maxHeight, and the
    // fixed-position Figma helpers below cannot be measured against it.
    Column(
        Modifier.at(18f, 136f, 375f, 640f),
        verticalArrangement = Arrangement.spacedBy(sc(14f))
    ) {
        // source card
        FBox(0f, 0f, 375f, 92f) {
            FText("SOURCE", 20f, 14f, 12f)
            FText(target?.name ?: "No file selected", 20f, 34f, 20f, w = 260f)
            FText(if (notes.isBlank()) "empty note" else "${notes.length} chars", 20f, 62f, 12f)
            FBox(300f, 22f, 55f, 26f, radius = 8f, modifier = Modifier.tap { say("Pick a file in Reviewer first") })
            { FText("pick", 310f, 24f, 12f) }
        }

        // ask + style fields
        FBox(0f, 0f, 375f, 92f) {
            FText("ASK (optional)", 20f, 14f, 12f)
            BasicField(ask, { ask = it }, "e.g. focus on the formulas", 20f, 34f, 335f, 42f)
        }
        FBox(0f, 0f, 375f, 92f) {
            FText("PERSONA TONE", 20f, 14f, 12f)
            BasicField(style, { style = it }, "e.g. blunter, more examples", 20f, 34f, 335f, 42f)
        }

        // api status + actions
        FBox(0f, 0f, 375f, 62f, fill = if (vm.config.isConfigured) Paper else Unverified.LockedFill,
             modifier = Modifier.tap { showSettings = true }) {
            FText(if (vm.config.isConfigured) "API: ${vm.config.model}" else "No API key - tap to add", 20f, 10f, 14f, w = 250f)
            FText(if (vm.config.isConfigured) vm.config.baseUrl else "settings", 20f, 34f, 12f, w = 250f)
            FText("edit", 310f, 24f, 12f)
        }

        FButton(
            if (vm.running) "Working..." else "Generate reviewer",
            0f, 0f, 375f, 50f, filled = true,
            onClick = {
                if (!vm.config.isConfigured) { showSettings = true; say("Add your API key first") }
                else vm.generate(scope, app, notes, ask, style) { }
            }
        )

        if (vm.error != null) {
            FBox(0f, 0f, 375f, 70f, fill = Paper) {
                FText("Error", 20f, 10f, 12f, color = Color(0xFFB00020))
                FText(vm.error!!, 20f, 30f, 12f, w = 335f, color = Color(0xFFB00020))
            }
        }

        // result
        if (vm.result.isNotBlank()) {
            FBox(0f, 0f, 375f, 380f) {
                FText("RESULT", 20f, 14f, 12f)
                Column(Modifier.at(20f, 40f, 335f, 320f).verticalScroll(rememberScrollState())) {
                    Text(vm.result, color = Ink, fontSize = ssp(12f), fontFamily = DrEhFont)
                }
            }
            FButton("Save into note", 0f, 0f, 180f, 44f, onClick = {
                if (folder != null && target != null) {
                    val existing = app.file(folder.id, target.id)?.text.orEmpty()
                    app.setFileText(folder.id, target.id, listOf(existing, vm.result).filter { it.isNotBlank() }.joinToString("\n\n"))
                    say(if (app.levelUpPending) "+20 XP - LEVEL UP! LV ${app.level}" else "+20 XP")
                    onSaveInto(folder.id, target.id, vm.result)
                } else say("Create a file first")
            })
        }
        Spacer(Modifier.height(sc(120f)))
    }

    BottomNav(Tab.HOME, onTab)

    if (showSettings) AiApiDialog(vm.config, { vm.update(it); showSettings = false }) { showSettings = false }
}

@Composable
private fun BasicField(value: String, onChange: (String) -> Unit, placeholder: String, x: Float, y: Float, w: Float, h: Float) {
    FBox(x, y, w, h, radius = 8f) {
        androidx.compose.foundation.text.BasicTextField(
            value = value, onValueChange = onChange,
            modifier = Modifier.fillMaxSize().padding(horizontal = sc(12f)),
            textStyle = androidx.compose.ui.text.TextStyle(color = Ink, fontSize = ssp(14f), fontFamily = DrEhFont),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(Ink),
            decorationBox = { inner ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) Text(placeholder, color = PlaceholderGray, fontSize = ssp(14f), fontFamily = DrEhFont, softWrap = false)
                    inner()
                }
            }
        )
    }
}

/** Endpoint / key / model. Generic on purpose so any OpenAI-compatible API works. */
@Composable
fun AiApiDialog(initial: AiConfig, onSave: (AiConfig) -> Unit, onDismiss: () -> Unit) {
    var base by remember { mutableStateOf(initial.baseUrl) }
    var key by remember { mutableStateOf(initial.apiKey) }
    var model by remember { mutableStateOf(initial.model) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Paper,
        title = { Text("API", fontFamily = DrEhFont, color = Ink) },
        text = {
                Column(verticalArrangement = Arrangement.spacedBy(8f.dp)) {
                DialogField(base, { base = it }, "Base URL ($GROQ_BASE_URL)")
                DialogField(key, { key = it }, "API key (stored on this device)", password = true)
                DialogField(model, { model = it }, "Model ($GROQ_DEFAULT_MODEL)")
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                onSave(AiConfig(base.trim(), key.trim(), model.trim(), initial.temperature))
            }) { Text("SAVE", fontFamily = DrEhFont, color = Ink) }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("CANCEL", fontFamily = DrEhFont, color = Ink) }
        }
    )
}

@Composable
private fun DialogField(v: String, onChange: (String) -> Unit, label: String, password: Boolean = false) {
    androidx.compose.material3.OutlinedTextField(
        value = v, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        visualTransformation = if (password) androidx.compose.ui.text.input.PasswordVisualTransformation()
            else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = if (password) KeyboardType.Password else KeyboardType.Text),
        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = DrEhFont)
    )
}
