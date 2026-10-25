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
import androidx.compose.foundation.Image
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
import androidx.compose.ui.res.painterResource
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

private val Bg = Color(0xFF0B0D12)
private val Panel = Color(0xFF0D1016)
private val Panel2 = Color(0xFF151A23)
private val Border = Color(0xFF202631)
private val TextMain = Color(0xFFD7DCE5)
private val Muted = Color(0xFF8B95A6)
private val Accent = Color(0xFF4C8DFF)
private val Green = Color(0xFF87D69A)
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
    var redo by remember { mutableStateOf(code) }
    var output by remember { mutableStateOf("Ready. Run a Python file to see its output here.") }
    val inputHistory = remember { mutableStateListOf<String>() }
    var consoleTab by remember { mutableStateOf("Output") }
    var projectSearch by remember { mutableStateOf(false) }
    var projectQuery by remember { mutableStateOf("") }
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
                consoleTab = "Input"
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
                    consoleTab = "Output"
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
    fun submitInput(request: InputRequest, value: String) {
        inputHistory.add((request.prompt + value).ifBlank { "(empty input)" })
        request.submit(value)
        inputRequest = null
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
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).background(Bg)) {
            TopBar(
                page = page,
                activeFile = activeFile,
                completedCount = completed.size,
                lessonCount = beginnerLessons.size,
                onRun = { runCode() },
                running = running,
                onFiles = { files = store.list(); fileDialog = true },
                onNew = { nameInput = ""; newDialog = true },
                searchVisible = projectSearch,
                searchQuery = projectQuery,
                onSearchToggle = { projectSearch = !projectSearch; projectQuery = "" },
                onSearchChange = { projectQuery = it }
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
            when (page) {
                Page.Editor -> EditorScreen(
                    code = code,
                    onCodeChange = { changed ->
                        undo = code
                        redo = changed
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
                    onUndo = { redo = code; code = undo; savedTabs[activeFile] = code.text },
                    onRedo = { undo = code; code = redo; savedTabs[activeFile] = code.text },
                    onOutdent = {
                        val lineStart = code.text.lastIndexOf('\n', (code.selection.start - 1).coerceAtLeast(0)) + 1
                        val removeCount = code.text.substring(lineStart).take(4).takeWhile { it == ' ' || it == '\t' }.length
                        val updated = code.text.removeRange(lineStart, lineStart + removeCount)
                        code = code.copy(text = updated, selection = androidx.compose.ui.text.TextRange((code.selection.start - removeCount).coerceAtLeast(lineStart)))
                        savedTabs[activeFile] = code.text
                    },
                    onNew = { nameInput = ""; newDialog = true },
                    output = output,
                    consoleTab = consoleTab,
                    inputHistory = inputHistory,
                    onConsoleTabChange = { consoleTab = it },
                    onClearOutput = { output = ""; inputHistory.clear() },
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
                    activeFile = activeFile,
                    searchQuery = projectQuery,
                    totalBytes = store.list().sumOf(store::size),
                    fileDetails = store.list().associateWith { store.size(it) to store.modified(it) },
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
                                else -> Icons.Default.MenuBook
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
                onDismissRequest = { submitInput(request, "") },
                title = { Text("Python input") },
                text = {
                    Column {
                        if (request.prompt.isNotBlank()) Text(request.prompt, color = Muted)
                        OutlinedTextField(
                            value = inputText, onValueChange = { inputText = it }, label = { Text("Type a value") },
                            singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { submitInput(request, inputText) })
                        )
                    }
                },
                confirmButton = { TextButton(onClick = { submitInput(request, inputText) }) { Text("Send") } },
                dismissButton = { TextButton(onClick = { submitInput(request, "") }) { Text("Cancel input") } },
                containerColor = Panel
            )
        }
        message?.let { Text(it, Modifier.padding(12.dp).background(Panel2, RoundedCornerShape(8.dp)).padding(12.dp), color = TextMain) }
    }
}

@Composable
private fun TopBar(
    page: Page,
    activeFile: String,
    completedCount: Int,
    lessonCount: Int,
    onRun: () -> Unit,
    running: Boolean,
    onFiles: () -> Unit,
    onNew: () -> Unit,
    searchVisible: Boolean,
    searchQuery: String,
    onSearchToggle: () -> Unit,
    onSearchChange: (String) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 66.dp).background(Bg)
            .padding(start = 16.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (page) {
            Page.Editor -> {
                PyPocketLogo(42)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("PyPocket", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("scratch", color = Muted, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        Text("  /  ", color = Border, fontSize = 12.sp)
                        Text(activeFile, color = Muted, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    }
                }
                IconButton(onClick = onNew) { Icon(Icons.Default.NoteAdd, "New file", tint = Muted) }
                IconButton(onClick = onFiles) { Icon(Icons.Default.FolderOpen, "Browse files", tint = Muted) }
                Button(
                    onClick = onRun,
                    enabled = !running,
                    shape = RoundedCornerShape(11.dp),
                    contentPadding = PaddingValues(horizontal = 15.dp, vertical = 9.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Bg)
                ) {
                    Icon(if (running) Icons.Default.HourglassTop else Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(5.dp)); Text(if (running) "Running" else "Run", fontSize = 14.sp)
                }
            }
            Page.Projects -> {
                if (searchVisible) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchChange,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Search files", color = Muted) },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = Muted) }
                    )
                } else Text("Projects", modifier = Modifier.weight(1f), fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                IconButton(onClick = onSearchToggle) {
                    Icon(if (searchVisible) Icons.Default.Close else Icons.Default.Search, "Search files", tint = Muted)
                }
                IconButton(onClick = onNew) { Icon(Icons.Default.Add, "New file", tint = TextMain) }
            }
            Page.Learn, Page.Lesson -> {
                Text("Learn", modifier = Modifier.weight(1f), fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Text("$completedCount / $lessonCount", color = Muted, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.width(6.dp))
                Text("lessons", color = Muted, fontSize = 12.sp)
            }
        }
    }
    HorizontalDivider(color = Border, thickness = 1.dp)
}

@Composable
private fun PyPocketLogo(size: Int) {
    Image(
        painter = painterResource(R.drawable.pypocket_mark),
        contentDescription = "PyPocket logo",
        modifier = Modifier.size(size.dp)
    )
}

@Composable
private fun EditorScreen(
    code: TextFieldValue, onCodeChange: (TextFieldValue) -> Unit, fileName: String,
    tabs: List<String>, onSelectTab: (String) -> Unit, onCloseTab: (String) -> Unit,
    onSave: () -> Unit, onUndo: () -> Unit, onRedo: () -> Unit, onOutdent: () -> Unit,
    onNew: () -> Unit,
    output: String, consoleTab: String, inputHistory: List<String>,
    onConsoleTabChange: (String) -> Unit, onClearOutput: () -> Unit,
    showConsole: Boolean, consoleHeight: Float, running: Boolean,
    onToggleConsole: () -> Unit, onConsoleDrag: (Float) -> Unit, onRun: () -> Unit, onCancel: () -> Unit
) {
    val cursor = code.selection.start.coerceIn(0, code.text.length)
    val lineNumber = code.text.take(cursor).count { it == '\n' } + 1
    val columnNumber = cursor - (code.text.take(cursor).lastIndexOf('\n') + 1) + 1
    Column(Modifier.fillMaxSize().background(Bg)) {
        Row(Modifier.fillMaxWidth().background(Bg).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            tabs.forEach { tab ->
                Column(Modifier.background(if (tab == fileName) Panel else Bg).clickable { onSelectTab(tab) }) {
                    Row(
                        Modifier.padding(start = 12.dp, end = 3.dp, top = 7.dp, bottom = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (tab == fileName) Box(Modifier.size(7.dp).background(Accent, RoundedCornerShape(50)))
                        else Icon(Icons.Default.Code, null, tint = Muted, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(7.dp)); Text(tab, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                            color = if (tab == fileName) TextMain else Muted)
                        IconButton(onClick = { onCloseTab(tab) }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, "Close $tab", tint = Muted, modifier = Modifier.size(13.dp))
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(2.dp).background(if (tab == fileName) Accent else Bg))
                }
            }
            IconButton(onClick = onNew) { Icon(Icons.Default.Add, "New file", tint = Muted) }
        }
        HorizontalDivider(color = Border)
        Row(Modifier.fillMaxWidth().height(44.dp).background(Bg).padding(horizontal = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            ToolIcon("Undo", Icons.Default.Undo, onUndo)
            ToolIcon("Redo", Icons.Default.Redo, onRedo)
            Box(Modifier.width(1.dp).height(22.dp).background(Border))
            ToolIcon("Indent", Icons.Default.FormatIndentIncrease) { onCodeChange(code.insert("    ")) }
            ToolIcon("Outdent", Icons.Default.FormatIndentDecrease, onOutdent)
            ToolIcon("Comment", Icons.Default.Tag) {
                val lineStart = code.text.lastIndexOf('\n', (code.selection.start - 1).coerceAtLeast(0)) + 1
                val updated = code.text.substring(0, lineStart) + "# " + code.text.substring(lineStart)
                onCodeChange(code.copy(text = updated, selection = androidx.compose.ui.text.TextRange(code.selection.start + 2)))
            }
            Box(Modifier.width(1.dp).height(22.dp).background(Border))
            ToolIcon("Save", Icons.Default.Save, onSave)
            Spacer(Modifier.weight(1f))
            Text("Ln $lineNumber, Col $columnNumber", color = Muted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }
        HorizontalDivider(color = Border)
        Row(Modifier.weight(1f).fillMaxWidth().background(Bg)) {
            Column(
                Modifier.width(40.dp).fillMaxHeight().background(Bg).padding(top = 12.dp, end = 7.dp),
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
        Row(Modifier.fillMaxWidth().height(43.dp).background(Bg), verticalAlignment = Alignment.CenterVertically) {
            listOf("Output", "Input", "Errors").forEach { tab ->
                Column(
                    Modifier.fillMaxHeight().clickable { onConsoleTabChange(tab) }.padding(horizontal = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Text(tab, color = if (consoleTab == tab) TextMain else Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.width(58.dp).height(2.dp).background(if (consoleTab == tab) Accent else Bg))
                }
            }
            Spacer(Modifier.weight(1f))
            Text(if (running) "RUNNING" else "Python 3.11", color = if (running) Orange else Muted,
                fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            IconButton(onClick = onClearOutput) { Icon(Icons.Default.DeleteOutline, "Clear console", tint = Muted, modifier = Modifier.size(19.dp)) }
            IconButton(onClick = onToggleConsole, modifier = Modifier.size(37.dp)) {
                Icon(if (showConsole) Icons.Default.ExpandMore else Icons.Default.ExpandLess, "Collapse console", tint = Muted)
            }
        }
        if (showConsole) {
            Box(
                Modifier.fillMaxWidth().height(consoleHeight.dp).background(Bg)
                    .pointerInput(Unit) { detectDragGestures { change, drag -> change.consume(); onConsoleDrag(drag.y) } }
                    .verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                val consoleText = when (consoleTab) {
                    "Input" -> inputHistory.joinToString("\n").ifBlank { "No input requested in this run." }
                    "Errors" -> output.lines().filter {
                        it.contains("Traceback") || it.contains("error", ignoreCase = true) ||
                            it.contains("Exception") || it.startsWith("  ")
                    }.joinToString("\n").ifBlank { "No errors in this run." }
                    else -> output.ifBlank { "Console cleared." }
                }
                Text(consoleText, color = if (consoleTab == "Errors" && consoleText != "No errors in this run.") Error else TextMain,
                    fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 21.sp)
            }
        }
        Row(Modifier.fillMaxWidth().height(48.dp).background(Bg).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("›", color = Accent, fontSize = 19.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.width(8.dp))
            Text(if (running) "Waiting for input or execution…" else "Run to enter input",
                color = Muted, fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
            if (running) {
                OutlinedButton(onClick = onCancel, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp)) {
                    Icon(Icons.Default.Stop, null, modifier = Modifier.size(16.dp), tint = Error); Spacer(Modifier.width(4.dp))
                    Text("Stop", color = Error)
                }
            } else OutlinedButton(
                onClick = onRun, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp),
                shape = RoundedCornerShape(9.dp)
            ) {
                Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Run again")
            }
        }
    }
}

@Composable
private fun ToolIcon(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(39.dp)) {
        Icon(icon, label, modifier = Modifier.size(19.dp), tint = Muted)
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
                    item.startsWith('\"') || item.startsWith('\'') -> Green
                    item.firstOrNull()?.isDigit() == true -> Orange
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
    files: List<String>, activeFile: String, searchQuery: String, totalBytes: Long,
    fileDetails: Map<String, Pair<Long, Long>>, onOpen: (String) -> Unit,
    onRename: (String) -> Unit, onDelete: (String) -> Unit
) {
    val filtered = files.filter { it.contains(searchQuery, ignoreCase = true) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).background(Bg)) {
        Row(
            Modifier.fillMaxWidth().height(46.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("pypocket", color = Muted, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            Text("  /  ", color = Border, fontSize = 12.sp)
            Text("scratch", color = TextMain, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.weight(1f))
            Text(files.size.toString() + " files  ·  " + formatBytes(totalBytes),
                color = Muted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }
        HorizontalDivider(color = Border)
        if (filtered.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(top = 54.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.FolderOpen, null, tint = Muted, modifier = Modifier.size(28.dp))
                Spacer(Modifier.height(12.dp))
                Text(if (searchQuery.isBlank()) "No Python files yet" else "No files found", color = TextMain)
                Text(if (searchQuery.isBlank()) "Tap + to create a file." else "Try another search.", color = Muted, fontSize = 12.sp)
            }
        } else filtered.forEach { filename ->
            var menuOpen by remember(filename) { mutableStateOf(false) }
            Row(
                Modifier.fillMaxWidth().heightIn(min = 72.dp)
                    .background(if (filename == activeFile) Panel2 else Bg)
                    .clickable { onOpen(filename) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.width(3.dp).height(72.dp).background(if (filename == activeFile) Accent else Bg))
                Spacer(Modifier.width(16.dp))
                Icon(Icons.Default.InsertDriveFile, null,
                    tint = if (filename == activeFile) Accent else Muted, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(15.dp))
                val details = fileDetails[filename] ?: (0L to 0L)
                Column(Modifier.weight(1f)) {
                    Text(filename, fontFamily = FontFamily.Monospace, fontSize = 15.sp, color = TextMain)
                    Spacer(Modifier.height(3.dp))
                    Text(formatBytes(details.first) + " · " + relativeTime(details.second), color = Muted, fontSize = 12.sp)
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "File options", tint = Muted) }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, containerColor = Panel2) {
                        DropdownMenuItem(text = { Text("Open") }, onClick = { menuOpen = false; onOpen(filename) })
                        DropdownMenuItem(text = { Text("Rename") }, onClick = { menuOpen = false; onRename(filename) })
                        DropdownMenuItem(
                            text = { Text("Delete", color = Error) },
                            onClick = { menuOpen = false; onDelete(filename) }
                        )
                    }
                }
                Spacer(Modifier.width(7.dp))
            }
            HorizontalDivider(color = Border)
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024f * 1024f))
    bytes >= 1024 -> "%.1f KB".format(bytes / 1024f)
    else -> "$bytes B"
}

private fun relativeTime(timestamp: Long): String {
    val minutes = ((System.currentTimeMillis() - timestamp).coerceAtLeast(0L) / 60_000).toInt()
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        minutes < 24 * 60 -> "${minutes / 60} hr ago"
        else -> "${minutes / (24 * 60)} days ago"
    }
}

@Composable
private fun LearnScreen(completed: List<Int>, onSelect: (Lesson) -> Unit) {
    var expanded by remember { mutableStateOf("Basics") }
    val groups = listOf(
        "Basics" to beginnerLessons.filter { it.id in 1..3 },
        "Control flow" to beginnerLessons.filter { it.id in 4..5 },
        "Collections" to beginnerLessons.filter { it.id == 6 },
        "Functions" to beginnerLessons.filter { it.id == 7 },
        "Projects" to beginnerLessons.filter { it.id == 8 }
    )
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).background(Bg)) {
        groups.forEachIndexed { index, group ->
            val title = group.first
            val items = group.second
            val done = items.count { it.id in completed }
            Column {
                Row(
                    Modifier.fillMaxWidth().height(48.dp).clickable { expanded = if (expanded == title) "" else title }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text((index + 1).toString(), color = if (expanded == title) TextMain else Muted,
                        fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.width(5.dp))
                    Text(title, modifier = Modifier.weight(1f), color = if (expanded == title) TextMain else Muted, fontSize = 14.sp)
                    Text("$done/${items.size}", color = Muted, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(2.dp).background(Border)) {
                    if (items.isNotEmpty() && done > 0) {
                        Box(Modifier.fillMaxWidth(done.toFloat() / items.size).fillMaxHeight().background(Accent))
                    }
                }
                if (expanded == title) {
                    Column(Modifier.fillMaxWidth().padding(start = 34.dp, top = 8.dp, bottom = 12.dp)) {
                        items.forEach { item ->
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 42.dp).clickable { onSelect(item) }
                                    .padding(end = 16.dp, top = 5.dp, bottom = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(lessonNumber(item), color = if (item.id in completed) Green else Muted,
                                    fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                                Spacer(Modifier.width(10.dp))
                                Text(item.title, color = if (item.id in completed) Accent else Muted, fontSize = 13.sp)
                                if (item.id in completed) {
                                    Spacer(Modifier.width(6.dp)); Icon(Icons.Default.Check, null, tint = Green, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
            }
            HorizontalDivider(color = Border, thickness = 1.dp)
        }
    }
}

private fun lessonNumber(item: Lesson): String = when (item.id) {
    in 1..3 -> "1." + item.id
    in 4..5 -> "2." + (item.id - 3)
    6 -> "3.1"
    7 -> "4.1"
    else -> "5.1"
}

@Composable
private fun LessonScreen(
    item: Lesson, complete: Boolean, onBack: () -> Unit,
    onExample: () -> Unit, onComplete: () -> Unit
) {
    var showHint by remember(item.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("LESSON " + lessonNumber(item), color = Accent, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onBack, contentPadding = PaddingValues(horizontal = 0.dp)) {
                Text("Back to lessons", color = Muted, fontSize = 12.sp)
            }
        }
        Text(item.title, fontSize = 23.sp, lineHeight = 29.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        Text(item.explanation, color = Color(0xFFAFB7C4), fontSize = 15.sp, lineHeight = 23.sp)
        Spacer(Modifier.height(15.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = Bg),
            shape = RoundedCornerShape(4.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Border)
        ) {
            Text(item.example, Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(12.dp),
                color = TextMain, fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 22.sp)
        }
        Spacer(Modifier.height(14.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = Panel),
            shape = RoundedCornerShape(4.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Border)
        ) {
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                Text("EXERCISE", color = Muted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(7.dp))
                Text(item.exercise, fontSize = 15.sp, lineHeight = 22.sp)
                if (showHint) {
                    Spacer(Modifier.height(8.dp))
                    Text("Hint: start with the example, then change one part at a time.", color = Muted, fontSize = 13.sp)
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = onExample, shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 13.dp, vertical = 8.dp)) {
                        Text("Open in editor", color = Bg, fontSize = 13.sp)
                    }
                    OutlinedButton(onClick = { showHint = !showHint }, shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(if (showHint) "Hide hint" else "Show hint", fontSize = 13.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onComplete, contentPadding = PaddingValues(horizontal = 0.dp)) {
            Icon(if (complete) Icons.Default.CheckCircle else Icons.Default.Check, null,
                tint = if (complete) Green else Muted, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(7.dp)); Text(if (complete) "Completed" else "Mark lesson complete", color = Muted, fontSize = 12.sp)
        }
    }
}
