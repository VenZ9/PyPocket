package dev.pypocket

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

private val Bg = Color(0xFF111318)
private val Panel = Color(0xFF181B22)
private val Panel2 = Color(0xFF20242D)
private val Border = Color(0xFF303541)
private val TextMain = Color(0xFFE5E7EB)
private val Muted = Color(0xFF9299A8)
private val Accent = Color(0xFF79B8FF)
private val Green = Color(0xFF8BD49C)
private val Orange = Color(0xFFE5B567)
private val Error = Color(0xFFFF8585)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PyPocketApp() }
    }
}

private enum class Page(val label: String) { Editor("Editor"), Projects("Projects"), Learn("Learn"), Lesson("Lesson") }

@Composable
private fun PyPocketApp() {
    val context = LocalContext.current
    val store = remember { ProjectStore(context.applicationContext) }
    val worker = remember { Executors.newSingleThreadExecutor() }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val prefs = remember { context.getSharedPreferences("learning", Activity.MODE_PRIVATE) }
    val completed = remember {
        mutableStateListOf<Int>().apply {
            addAll(prefs.getStringSet("completed", emptySet()).orEmpty().mapNotNull(String::toIntOrNull))
        }
    }
    var page by remember { mutableStateOf(Page.Editor) }
    var selectedLesson by remember { mutableStateOf<Lesson?>(null) }
    val tabs = remember { mutableStateListOf("main.py") }
    val savedTabs = remember { mutableStateMapOf<String, String>() }
    var activeFile by remember { mutableStateOf("main.py") }
    var code by remember { mutableStateOf(TextFieldValue("name = input(\"What is your name? \")\nprint(f\"Hello, {name}!\")")) }
    var undo by remember { mutableStateOf(code) }
    var output by remember { mutableStateOf("Ready. Run a Python file to see its output here.") }
    var showConsole by remember { mutableStateOf(true) }
    var consoleHeight by remember { mutableFloatStateOf(150f) }
    var running by remember { mutableStateOf(false) }
    var inputRequest by remember { mutableStateOf<InputRequest?>(null) }
    var inputText by remember { mutableStateOf("") }
    var fileDialog by remember { mutableStateOf(false) }
    var newDialog by remember { mutableStateOf(false) }
    var renameDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf("") }
    var nameInput by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var files by remember { mutableStateOf(emptyList<String>()) }
    val cancelFlag = remember { AtomicReference<AtomicBoolean?>(null) }
    val future = remember { AtomicReference<Future<*>?>(null) }

    DisposableEffect(Unit) { onDispose { worker.shutdownNow() } }
    LaunchedEffect(Unit) {
        runCatching {
            var available = store.list()
            if (available.isEmpty()) {
                store.create("main.py", code.text)
                available = store.list()
            }
            activeFile = available.firstOrNull { it == "main.py" } ?: available.first()
            tabs.clear()
            tabs.add(activeFile)
            code = TextFieldValue(store.read(activeFile))
            savedTabs[activeFile] = code.text
        }.onFailure { message = it.message ?: "Could not load workspace." }
    }
    LaunchedEffect(message) {
        if (message != null) {
            kotlinx.coroutines.delay(2500)
            message = null
        }
    }

    fun open(name: String) {
        runCatching {
            savedTabs[activeFile] = code.text
            activeFile = name
            if (name !in tabs) tabs.add(name)
            code = TextFieldValue(savedTabs[name] ?: store.read(name))
            savedTabs[name] = code.text
            undo = code
            fileDialog = false
            page = Page.Editor
        }.onFailure { message = it.message ?: "Could not open file." }
    }
    fun deleteFile(name: String) {
        runCatching { store.delete(name) }.onSuccess {
            tabs.remove(name)
            savedTabs.remove(name)
            if (activeFile == name) {
                val remaining = store.list()
                if (remaining.isNotEmpty()) open(remaining.first())
                else {
                    store.create("main.py")
                    activeFile = "main.py"
                    code = TextFieldValue("")
                    savedTabs["main.py"] = ""
                    tabs.clear()
                    tabs.add("main.py")
                }
            }
        }.onFailure { message = it.message }
    }
    fun makeFile() {
        val filename = if (nameInput.endsWith(".py", true)) nameInput.trim() else "${nameInput.trim()}.py"
        runCatching {
            store.create(filename)
            activeFile = filename
            code = TextFieldValue("")
            undo = code
            savedTabs[filename] = ""
            if (filename !in tabs) tabs.add(filename)
            newDialog = false
            nameInput = ""
            page = Page.Editor
        }.onFailure { message = it.message ?: "Could not create file." }
    }
    fun renameFile() {
        val filename = if (nameInput.endsWith(".py", true)) nameInput.trim() else "${nameInput.trim()}.py"
        runCatching {
            store.rename(renameTarget, filename)
            tabs.indexOf(renameTarget).takeIf { it >= 0 }?.let { tabs[it] = filename }
            savedTabs.remove(renameTarget)?.let { savedTabs[filename] = it }
            if (activeFile == renameTarget) activeFile = filename
            files = store.list()
            renameDialog = false
            nameInput = ""
        }.onFailure { message = it.message ?: "Could not rename file." }
    }
    fun runCode() {
        if (running) return
        runCatching {
            store.save(activeFile, code.text)
            savedTabs[activeFile] = code.text
            val cancelled = AtomicBoolean(false)
            cancelFlag.set(cancelled)
            running = true
            output = "Starting embedded Python…"
            showConsole = true
            val bridge = InputBridge(cancelled) { request ->
                inputText = ""
                inputRequest = request
            }
            future.set(worker.submit {
                val result = runCatching { PythonExecution.run(context.applicationContext, code.text, bridge) }
                    .fold(
                        { it },
                        { error ->
                            if (cancelled.get()) "Execution cancelled.\n"
                            else "Python runtime error: ${error.message ?: error.javaClass.simpleName}\n"
                        }
                    )
                mainHandler.post {
                    output = result
                    running = false
                    inputRequest = null
                    cancelFlag.set(null)
                    future.set(null)
                }
            })
        }.onFailure {
            output = "Could not save file: ${it.message}"
            showConsole = true
        }
    }
    fun stopCode() {
        cancelFlag.get()?.set(true)
        inputRequest?.submit("")
        future.get()?.cancel(true)
        inputRequest = null
        output = "Execution cancelled.\n"
        running = false
    }
    fun openExample(item: Lesson) {
        val existing = store.list().toSet()
        val stem = "lesson_%02d".format(item.id)
        var filename = "$stem.py"
        var suffix = 1
        while (filename in existing) filename = "${stem}_example_${suffix++}.py"
        runCatching {
            store.create(filename, item.example)
            savedTabs[filename] = item.example
            activeFile = filename
            code = TextFieldValue(item.example)
            if (filename !in tabs) tabs.add(filename)
            page = Page.Editor
        }.onFailure { message = it.message ?: "Could not load lesson example." }
    }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Accent, background = Bg, surface = Panel, onBackground = TextMain,
        onSurface = TextMain, error = Error
    )) {
        Column(Modifier.fillMaxSize().background(Bg)) {
            TopBar(
                onRun = { runCode() },
                running = running,
                onFiles = { files = store.list(); fileDialog = true },
                onNew = { nameInput = ""; newDialog = true }
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
            when (page) {
                Page.Editor -> EditorScreen(
                    code = code,
                    onCodeChange = { changed ->
                        undo = code
                        val newlineAdded = changed.text.length == code.text.length + 1 &&
                            changed.text.getOrNull(changed.selection.start - 1) == '\n'
                        code = if (newlineAdded) {
                            val priorLine = changed.text.substring(0, changed.selection.start - 1).substringAfterLast('\n')
                            val indent = priorLine.takeWhile { it == ' ' || it == '\t' } +
                                if (priorLine.trimEnd().endsWith(":")) "    " else ""
                            val updated = changed.text.substring(0, changed.selection.start) + indent +
                                changed.text.substring(changed.selection.start)
                            changed.copy(text = updated, selection = androidx.compose.ui.text.TextRange(changed.selection.start + indent.length))
                        } else changed
                        savedTabs[activeFile] = code.text
                    },
                    fileName = activeFile,
                    tabs = tabs,
                    onSelectTab = { open(it) },
                    onCloseTab = { tab ->
                        if (tabs.size > 1) {
                            tabs.remove(tab)
                            if (activeFile == tab) open(tabs.last())
                        }
                    },
                    onSave = {
                        runCatching { store.save(activeFile, code.text); savedTabs[activeFile] = code.text; message = "Saved $activeFile" }
                            .onFailure { message = it.message }
                    },
                    onUndo = { code = undo; savedTabs[activeFile] = code.text },
                    onNew = { nameInput = ""; newDialog = true },
                    output = output,
                    showConsole = showConsole,
                    consoleHeight = consoleHeight,
                    running = running,
                    onToggleConsole = { showConsole = !showConsole },
                    onConsoleDrag = { delta -> consoleHeight = (consoleHeight - delta).coerceIn(90f, 300f) },
                    onRun = { runCode() },
                    onCancel = { stopCode() }
                )
                Page.Projects -> ProjectsScreen(
                    files = store.list(),
                    onNew = { nameInput = ""; newDialog = true },
                    onOpen = { open(it) },
                    onRename = { renameTarget = it; nameInput = it; renameDialog = true },
                    onDelete = { deleteFile(it) }
                )
                Page.Learn -> LearnScreen(completed) { selectedLesson = it; page = Page.Lesson }
                Page.Lesson -> selectedLesson?.let { item ->
                    LessonScreen(
                        item, item.id in completed,
                        onBack = { page = Page.Learn },
                        onExample = { openExample(item) },
                        onComplete = {
                            if (item.id !in completed) completed.add(item.id)
                            prefs.edit().putStringSet("completed", completed.map(Int::toString).toSet()).apply()
                            message = "Lesson completed"
                        }
                    )
                }
            }
            }
            NavigationBar(containerColor = Panel, contentColor = TextMain) {
                listOf(Page.Editor, Page.Projects, Page.Learn).forEach { item ->
                    NavigationBarItem(
                        selected = page == item || (page == Page.Lesson && item == Page.Learn),
                        onClick = { page = item },
                        icon = {
                            Icon(when (item) {
                                Page.Editor -> Icons.Default.Code
                                Page.Projects -> Icons.Default.FolderOpen
                                else -> Icons.Default.School
                            }, item.label)
                        },
                        label = { Text(item.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Accent, selectedTextColor = Accent, indicatorColor = Panel2,
                            unselectedIconColor = Muted, unselectedTextColor = Muted
                        )
                    )
                }
            }
        }
        if (fileDialog) AlertDialog(
            onDismissRequest = { fileDialog = false },
            title = { Text("Open Python file") },
            text = {
                if (files.isEmpty()) Text("No Python files yet.")
                else Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                    files.forEach { name ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(name, Modifier.weight(1f).clickable { open(name) }.padding(vertical = 10.dp))
                            IconButton(onClick = { renameTarget = name; nameInput = name; fileDialog = false; renameDialog = true }) {
                                Icon(Icons.Default.Edit, "Rename $name", tint = Muted)
                            }
                            IconButton(onClick = {
                                deleteFile(name)
                                files = store.list()
                            }) { Icon(Icons.Default.DeleteOutline, "Delete $name", tint = Muted) }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { fileDialog = false; newDialog = true }) { Text("New file") } },
            dismissButton = { TextButton(onClick = { fileDialog = false }) { Text("Close") } },
            containerColor = Panel
        )
        if (newDialog || renameDialog) {
            val renaming = renameDialog
            AlertDialog(
                onDismissRequest = { newDialog = false; renameDialog = false },
                title = { Text(if (renaming) "Rename file" else "Create Python file") },
                text = {
                    OutlinedTextField(
                        value = nameInput, onValueChange = { nameInput = it },
                        label = { Text("Filename") }, placeholder = { Text("example.py") }, singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(onClick = { if (renaming) renameFile() else makeFile() }) { Text(if (renaming) "Rename" else "Create") }
                },
                dismissButton = { TextButton(onClick = { newDialog = false; renameDialog = false }) { Text("Cancel") } },
                containerColor = Panel
            )
        }
        inputRequest?.let { request ->
            AlertDialog(
                onDismissRequest = { request.submit(""); inputRequest = null },
                title = { Text("Python input") },
                text = {
                    Column {
                        if (request.prompt.isNotBlank()) Text(request.prompt, color = Muted)
                        OutlinedTextField(
                            value = inputText, onValueChange = { inputText = it }, label = { Text("Type a value") },
                            singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { request.submit(inputText); inputRequest = null })
                        )
                    }
                },
                confirmButton = { TextButton(onClick = { request.submit(inputText); inputRequest = null }) { Text("Send") } },
                dismissButton = { TextButton(onClick = { request.submit(""); inputRequest = null }) { Text("Cancel input") } },
                containerColor = Panel
            )
        }
        message?.let { Text(it, Modifier.padding(12.dp).background(Panel2, RoundedCornerShape(8.dp)).padding(12.dp), color = TextMain) }
    }
}

@Composable
private fun TopBar(onRun: () -> Unit, running: Boolean, onFiles: () -> Unit, onNew: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Panel).padding(start = 14.dp, end = 6.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(34.dp).background(Color(0xFF26384A), RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Code, null, tint = Accent)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("PyPocket", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            Text("Offline Python IDE", color = Muted, fontSize = 11.sp)
        }
        IconButton(onClick = onNew) { Icon(Icons.Default.NoteAdd, "New file", tint = Muted) }
        IconButton(onClick = onFiles) { Icon(Icons.Default.FolderOpen, "Open files", tint = Muted) }
        IconButton(onClick = onRun) {
            Icon(if (running) Icons.Default.HourglassTop else Icons.Default.PlayArrow, "Run code", tint = Green)
        }
    }
    HorizontalDivider(color = Border)
}

@Composable
private fun EditorScreen(
    code: TextFieldValue, onCodeChange: (TextFieldValue) -> Unit, fileName: String,
    tabs: List<String>, onSelectTab: (String) -> Unit, onCloseTab: (String) -> Unit,
    onSave: () -> Unit, onUndo: () -> Unit, onNew: () -> Unit,
    output: String, showConsole: Boolean, consoleHeight: Float, running: Boolean,
    onToggleConsole: () -> Unit, onConsoleDrag: (Float) -> Unit, onRun: () -> Unit, onCancel: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().background(Bg).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            tabs.forEach { tab ->
                Row(
                    Modifier.background(if (tab == fileName) Panel else Bg).clickable { onSelectTab(tab) }
                        .padding(start = 10.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Code, null, tint = Green, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(5.dp)); Text(tab, fontSize = 12.sp)
                    IconButton(onClick = { onCloseTab(tab) }, modifier = Modifier.size(25.dp)) {
                        Icon(Icons.Default.Close, "Close $tab", tint = Muted, modifier = Modifier.size(14.dp))
                    }
                }
            }
            IconButton(onClick = onNew) { Icon(Icons.Default.Add, "New file", tint = Muted) }
        }
        HorizontalDivider(color = Border)
        Row(Modifier.fillMaxWidth().background(Panel).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            ToolButton("Save", Icons.Default.Save, onSave)
            ToolButton("Undo", Icons.Default.Undo, onUndo)
            ToolButton("Indent", Icons.Default.FormatIndentIncrease) { onCodeChange(code.insert("    ")) }
            ToolButton("Comment", Icons.Default.Comment) {
                val lineStart = code.text.lastIndexOf('\n', (code.selection.start - 1).coerceAtLeast(0)) + 1
                val updated = code.text.substring(0, lineStart) + "# " + code.text.substring(lineStart)
                onCodeChange(code.copy(text = updated, selection = androidx.compose.ui.text.TextRange(code.selection.start + 2)))
            }
        }
        HorizontalDivider(color = Border)
        Row(Modifier.weight(1f).fillMaxWidth().background(Bg)) {
            Column(
                Modifier.width(40.dp).fillMaxHeight().background(Panel).padding(top = 12.dp, end = 7.dp),
                horizontalAlignment = Alignment.End
            ) {
                repeat(maxOf(1, code.text.count { it == '\n' } + 1)) { index ->
                    Text("${index + 1}", color = Muted, fontSize = 13.sp, lineHeight = 23.sp, fontFamily = FontFamily.Monospace)
                }
            }
            BasicTextField(
                value = code, onValueChange = onCodeChange,
                modifier = Modifier.fillMaxSize().padding(start = 10.dp, top = 12.dp, end = 8.dp),
                textStyle = TextStyle(color = TextMain, fontFamily = FontFamily.Monospace, fontSize = 14.sp, lineHeight = 23.sp),
                visualTransformation = PythonHighlight, cursorBrush = SolidColor(Accent)
            )
        }
        HorizontalDivider(color = Border)
        Row(
            Modifier.fillMaxWidth().background(Panel).clickable { onToggleConsole() }.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Terminal, null, tint = Muted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp)); Text("OUTPUT", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Muted)
            Spacer(Modifier.weight(1f)); Text(if (running) "RUNNING" else "Python 3.11", color = if (running) Orange else Muted, fontSize = 10.sp)
            Spacer(Modifier.width(6.dp)); Icon(if (showConsole) Icons.Default.ExpandMore else Icons.Default.ExpandLess, null, tint = Muted)
        }
        if (showConsole) {
            Box(
                Modifier.fillMaxWidth().height(consoleHeight.dp).background(Color(0xFF0D0F13))
                    .pointerInput(Unit) { detectDragGestures { change, drag -> change.consume(); onConsoleDrag(drag.y) } }
                    .verticalScroll(rememberScrollState()).padding(12.dp)
            ) {
                Text(output, color = Color(0xFFC6D0DE), fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 19.sp)
            }
        }
        Row(Modifier.fillMaxWidth().background(Panel).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Python 3.11 · Offline runtime", color = Muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
            if (running) {
                OutlinedButton(onClick = onCancel, contentPadding = PaddingValues(horizontal = 13.dp, vertical = 5.dp)) {
                    Icon(Icons.Default.Stop, null, modifier = Modifier.size(16.dp), tint = Error)
                    Spacer(Modifier.width(4.dp)); Text("Stop", color = Error)
                }
            } else Button(
                onClick = onRun, contentPadding = PaddingValues(horizontal = 15.dp, vertical = 6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF244B39), contentColor = Green)
            ) {
                Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(5.dp)); Text("Run")
            }
        }
    }
}

@Composable
private fun ToolButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp)) {
        Icon(icon, label, modifier = Modifier.size(15.dp), tint = Muted); Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 11.sp, color = Muted)
    }
}

private fun TextFieldValue.insert(value: String): TextFieldValue {
    val start = minOf(selection.start, selection.end)
    val end = maxOf(selection.start, selection.end)
    return copy(text = text.substring(0, start) + value + text.substring(end),
        selection = androidx.compose.ui.text.TextRange(start + value.length))
}

private object PythonHighlight : VisualTransformation {
    private val tokens = Regex("(?m)(#[^\\n]*|\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'|\\b(?:and|as|assert|async|await|break|class|continue|def|del|elif|else|except|False|finally|for|from|global|if|import|in|is|lambda|None|nonlocal|not|or|pass|raise|return|True|try|while|with|yield)\\b|\\b(?:print|input|range|len|int|float|str|list|dict|set|tuple|bool|sum|min|max|enumerate|open|type)\\b|\\b\\d+(?:\\.\\d+)?\\b)")
    override fun filter(text: androidx.compose.ui.text.AnnotatedString): TransformedText {
        val styled = buildAnnotatedString {
            append(text)
            tokens.findAll(text).forEach { match ->
                val item = match.value
                val tint = when {
                    item.startsWith("#") -> Muted
                    item.startsWith('\"') || item.startsWith('\'') -> Orange
                    item.firstOrNull()?.isDigit() == true -> Color(0xFFB9A0F5)
                    item in setOf("print", "input", "range", "len", "int", "float", "str", "list", "dict", "set", "tuple", "bool", "sum", "min", "max", "enumerate", "open", "type") -> Accent
                    else -> Color(0xFFC792EA)
                }
                addStyle(SpanStyle(color = tint), match.range.first, match.range.last + 1)
            }
        }
        return TransformedText(styled, OffsetMapping.Identity)
    }
}

@Composable
private fun ProjectsScreen(
    files: List<String>, onNew: () -> Unit, onOpen: (String) -> Unit,
    onRename: (String) -> Unit, onDelete: (String) -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Projects", fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
                Text("Stored privately on this device", color = Muted, fontSize = 13.sp)
            }
            FilledTonalButton(onClick = onNew) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(5.dp)); Text("New")
            }
        }
        Spacer(Modifier.height(19.dp))
        if (files.isEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.FolderOpen, null, tint = Muted); Spacer(Modifier.height(8.dp))
                    Text("No files yet"); Text("Create a .py file to get started.", color = Muted, fontSize = 12.sp)
                }
            }
        } else files.forEach { filename ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) {
                Row(Modifier.padding(start = 13.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.InsertDriveFile, null, tint = Green); Spacer(Modifier.width(10.dp))
                    Text(filename, Modifier.weight(1f).clickable { onOpen(filename) })
                    IconButton(onClick = { onRename(filename) }) { Icon(Icons.Default.Edit, "Rename", tint = Muted) }
                    IconButton(onClick = { onDelete(filename) }) { Icon(Icons.Default.DeleteOutline, "Delete", tint = Muted) }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Files stay in app-private storage and work without a network connection.", color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun LearnScreen(completed: List<Int>, onSelect: (Lesson) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text("Learn Python", fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
        Text("Lessons, editable examples, and hands-on exercises.", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(15.dp))
        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(12.dp)) {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${completed.size}/${beginnerLessons.size}", color = Accent, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Lessons completed", fontWeight = FontWeight.Medium)
                    Text("Progress is saved on this device.", color = Muted, fontSize = 12.sp)
                }
            }
            LinearProgressIndicator(
                progress = { completed.size.toFloat() / beginnerLessons.size },
                modifier = Modifier.fillMaxWidth().height(3.dp), color = Accent, trackColor = Panel2
            )
        }
        Spacer(Modifier.height(17.dp))
        beginnerLessons.forEach { item ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).clickable { onSelect(item) }
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(38.dp).background(Panel2, RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
                        if (item.id in completed) Icon(Icons.Default.Check, null, tint = Green)
                        else Text("%02d".format(item.id), color = Accent, fontFamily = FontFamily.Monospace)
                    }
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.title, fontWeight = FontWeight.Medium); Text(item.summary, fontSize = 12.sp, color = Muted)
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = Muted)
                }
            }
        }
    }
}

@Composable
private fun LessonScreen(
    item: Lesson, complete: Boolean, onBack: () -> Unit,
    onExample: () -> Unit, onComplete: () -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) {
            Icon(Icons.Default.ArrowBack, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(5.dp)); Text("All lessons")
        }
        Spacer(Modifier.height(4.dp))
        Text("LESSON %02d".format(item.id), color = Accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Text(item.title, fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
        Text(item.summary, color = Muted, fontSize = 14.sp)
        Spacer(Modifier.height(18.dp)); Text("LEARN", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp)); Text(item.explanation, fontSize = 15.sp, lineHeight = 23.sp)
        Spacer(Modifier.height(17.dp)); Text("EXAMPLE", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0D0F13)), shape = RoundedCornerShape(10.dp)) {
            Text(item.example, Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(13.dp),
                color = Color(0xFFD7DFE9), fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 21.sp)
        }
        Spacer(Modifier.height(9.dp))
        Button(onClick = onExample, colors = ButtonDefaults.buttonColors(containerColor = Panel2, contentColor = Accent)) {
            Icon(Icons.Default.Code, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text("Edit and run example")
        }
        Spacer(Modifier.height(16.dp)); Text("YOUR EXERCISE", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp)); Text(item.exercise, fontSize = 15.sp, lineHeight = 23.sp)
        Spacer(Modifier.height(14.dp))
        OutlinedButton(onClick = onComplete, modifier = Modifier.fillMaxWidth()) {
            Icon(if (complete) Icons.Default.CheckCircle else Icons.Default.Check, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(7.dp)); Text(if (complete) "Completed" else "Mark lesson complete")
        }
    }
}
