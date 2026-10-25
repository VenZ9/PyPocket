package dev.pypocket

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Bg = Color(0xFF111318)
private val Panel = Color(0xFF181B22)
private val Panel2 = Color(0xFF20242D)
private val Border = Color(0xFF303541)
private val TextMain = Color(0xFFE5E7EB)
private val Muted = Color(0xFF9299A8)
private val Accent = Color(0xFF79B8FF)
private val Green = Color(0xFF8BD49C)
private val Orange = Color(0xFFE5B567)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PyPocketApp() }
    }
}

private enum class Page(val label: String) {
    Editor("Editor"), Projects("Projects"), Learn("Learn")
}

@Composable
private fun PyPocketApp() {
    var page by remember { mutableStateOf(Page.Editor) }
    var code by remember {
        mutableStateOf("name = input(\\"What is your name? \\")\\nprint(f\\"Hello, {name}!\\")\\n\\nfor number in range(3):\\n    print(number)")
    }
    var console by remember { mutableStateOf("PyPocket console\\nPython runtime is coming in milestone 2.") }
    var showConsole by remember { mutableStateOf(true) }
    var fileName by remember { mutableStateOf("main.py") }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            background = Bg,
            surface = Panel,
            onBackground = TextMain,
            onSurface = TextMain
        )
    ) {
        Column(Modifier.fillMaxSize().background(Bg)) {
            TopBar(onRun = {
                console = "Runtime not connected yet.\\nMilestone 2 will embed Python so scripts execute offline."
                showConsole = true
            })
            when (page) {
                Page.Editor -> EditorScreen(
                    code = code,
                    onCodeChange = { code = it },
                    fileName = fileName,
                    onFileNameChange = { fileName = it },
                    console = console,
                    showConsole = showConsole,
                    onToggleConsole = { showConsole = !showConsole },
                    onRun = {
                        console = "Runtime not connected yet.\\nMilestone 2 will embed Python so scripts execute offline."
                        showConsole = true
                    }
                )
                Page.Projects -> ProjectsScreen(
                    onOpenSample = {
                        fileName = "hello.py"
                        code = "print(\\"Hello from PyPocket!\\")\\n\\nfor i in range(5):\\n    print(i)"
                        page = Page.Editor
                    }
                )
                Page.Learn -> LearnScreen(onTryLesson = {
                    fileName = "lesson_01.py"
                    code = "# Lesson 1: print()\\nprint(\\"Hello, Python!\\")\\n\\n# Try changing this message\\nprint(2 + 3)"
                    page = Page.Editor
                })
            }
            NavigationBar(containerColor = Panel, contentColor = TextMain) {
                Page.entries.forEach { item ->
                    NavigationBarItem(
                        selected = page == item,
                        onClick = { page = item },
                        icon = {
                            Icon(
                                when (item) {
                                    Page.Editor -> Icons.Default.Code
                                    Page.Projects -> Icons.Default.FolderOpen
                                    Page.Learn -> Icons.Default.School
                                },
                                contentDescription = item.label
                            )
                        },
                        label = { Text(item.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Accent,
                            selectedTextColor = Accent,
                            indicatorColor = Panel2,
                            unselectedIconColor = Muted,
                            unselectedTextColor = Muted
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun TopBar(onRun: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Panel).padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(34.dp).background(Color(0xFF26384A), RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Code, contentDescription = null, tint = Accent)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("PyPocket", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            Text("Offline Python IDE", color = Muted, fontSize = 11.sp)
        }
        IconButton(onClick = onRun) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Run code", tint = Green)
        }
        IconButton(onClick = {}) {
            Icon(Icons.Default.MoreVert, contentDescription = "More options", tint = Muted)
        }
    }
    HorizontalDivider(color = Border)
}

@Composable
private fun EditorScreen(
    code: String,
    onCodeChange: (String) -> Unit,
    fileName: String,
    onFileNameChange: (String) -> Unit,
    console: String,
    showConsole: Boolean,
    onToggleConsole: () -> Unit,
    onRun: () -> Unit
) {
    Column(Modifier.fillMaxSize().weight(1f)) {
        Row(
            Modifier.fillMaxWidth().background(Bg).horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                Modifier.background(Panel).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Code, null, tint = Color(0xFF82C995), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(7.dp))
                Text(fileName, fontSize = 13.sp)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Default.Circle, null, tint = Orange, modifier = Modifier.size(6.dp))
            }
            Text("  +  ", color = Muted, fontSize = 18.sp)
        }
        HorizontalDivider(color = Border)
        Row(Modifier.weight(1f).fillMaxWidth()) {
            Column(
                Modifier.width(38.dp).fillMaxHeight().background(Panel).padding(top = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                repeat(maxOf(1, code.count { it == '\n' } + 1)) { index ->
                    Text("${index + 1}", color = Muted, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.height(5.dp))
                }
            }
            BasicTextField(
                value = code,
                onValueChange = onCodeChange,
                modifier = Modifier.fillMaxSize().background(Bg).padding(start = 10.dp, top = 12.dp, end = 8.dp),
                textStyle = TextStyle(
                    color = TextMain,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    lineHeight = 23.sp
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Accent)
            )
        }
        HorizontalDivider(color = Border)
        Row(
            Modifier.fillMaxWidth().background(Panel).clickable { onToggleConsole() }.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Terminal, null, tint = Muted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("OUTPUT", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Muted)
            Spacer(Modifier.weight(1f))
            Icon(if (showConsole) Icons.Default.ExpandMore else Icons.Default.ExpandLess, null, tint = Muted)
        }
        if (showConsole) {
            Column(
                Modifier.fillMaxWidth().heightIn(min = 90.dp, max = 160.dp)
                    .background(Color(0xFF0D0F13)).verticalScroll(rememberScrollState()).padding(12.dp)
            ) {
                Text(console, color = Color(0xFFC6D0DE), fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 19.sp)
            }
        }
        Row(
            Modifier.fillMaxWidth().background(Panel).padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Python 3 · Runtime pending", color = Muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
            Button(
                onClick = onRun,
                contentPadding = PaddingValues(horizontal = 15.dp, vertical = 7.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF244B39), contentColor = Green)
            ) {
                Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(5.dp))
                Text("Run")
            }
        }
    }
}

@Composable
private fun ProjectsScreen(onOpenSample: () -> Unit) {
    Column(Modifier.fillMaxSize().weight(1f).verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text("Explorer", fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
        Text("Your local Python workspace", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Folder, null, tint = Accent)
            Spacer(Modifier.width(9.dp))
            Text("My Projects", fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = Panel),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().clickable { onOpenSample() }
        ) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.InsertDriveFile, null, tint = Green)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("hello.py", fontWeight = FontWeight.Medium)
                    Text("Sample Python file · preview", color = Muted, fontSize = 12.sp)
                }
                Icon(Icons.Default.ChevronRight, null, tint = Muted)
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Milestone 3 adds real folder and file management.", color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun LearnScreen(onTryLesson: () -> Unit) {
    Column(Modifier.fillMaxSize().weight(1f).verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text("Learn Python", fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
        Text("Small lessons. Real code. No internet needed.", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("0%", "Progress", Modifier.weight(1f))
            StatCard("12", "Lessons planned", Modifier.weight(1f))
        }
        Spacer(Modifier.height(22.dp))
        Text("START HERE", color = Muted, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
        Spacer(Modifier.height(10.dp))
        LessonCard("01", "Your first Python program", "print(), strings, and running code", onTryLesson)
        LessonCard("02", "Variables and values", "Names, numbers, and text", {}, enabled = false)
        LessonCard("03", "Make decisions", "if, elif, and else", {}, enabled = false)
        LessonCard("04", "Repeat work", "for loops and while loops", {}, enabled = false)
    }
}

@Composable
private fun StatCard(value: String, label: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(value, color = Accent, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Text(label, color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun LessonCard(
    number: String,
    title: String,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 9.dp).clickable(enabled = enabled, onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp).background(Panel2, RoundedCornerShape(9.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(number, color = if (enabled) Accent else Muted, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Medium, color = if (enabled) TextMain else Muted)
                Spacer(Modifier.height(3.dp))
                Text(description, fontSize = 12.sp, color = Muted)
            }
            if (enabled) Icon(Icons.Default.ChevronRight, null, tint = Muted)
            else Icon(Icons.Default.Lock, null, tint = Muted, modifier = Modifier.size(16.dp))
        }
    }
}
