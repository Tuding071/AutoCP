package com.autocp.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.ViewGroup.LayoutParams
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ScrollView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

// ─────────────────────────────────────────────
// Activity
// ─────────────────────────────────────────────

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                AutoCPScreen()
            }
        }
    }
}

// ─────────────────────────────────────────────
// PARTS logic (unchanged)
// ─────────────────────────────────────────────

data class PartInfo(
    val name: String,
    val startIndex: Int,
    val contentStartIndex: Int,
    val contentEndIndex: Int,
    val endIndex: Int
)

private val partStartRegex = Regex("""^//PART\s+(\d+(?:\.\d+)?)\s+START""", RegexOption.MULTILINE)
private val partEndRegex   = Regex("""^//PART\s+\d+(?:\.\d+)?\s+END""",   RegexOption.MULTILINE)

fun findParts(code: String): List<PartInfo> {
    val parts        = mutableListOf<PartInfo>()
    val startMatches = partStartRegex.findAll(code).toList()
    val endMatches   = partEndRegex.findAll(code).toList()

    for (startMatch in startMatches) {
        val partName = startMatch.groupValues[1]
        val endMatch = endMatches.find {
            it.range.first > startMatch.range.last &&
            it.value.contains(Regex("""//PART\s+${Regex.escape(partName)}\s+END"""))
        }
        if (endMatch != null) {
            parts.add(PartInfo(
                name              = partName,
                startIndex        = startMatch.range.first,
                contentStartIndex = startMatch.range.last + 1,
                contentEndIndex   = endMatch.range.first,
                endIndex          = endMatch.range.last + 1
            ))
        }
    }
    return parts
}

fun replaceParts(originalCode: String, replacementCode: String): String {
    val replacementParts = findParts(replacementCode)
    if (replacementParts.isEmpty()) return originalCode

    var result = originalCode
    for (replacementPart in replacementParts) {
        val newContent = replacementCode
            .substring(replacementPart.contentStartIndex, replacementPart.contentEndIndex)
            .trim('\n', '\r')

        val originalParts = findParts(result)
        val targetPart    = originalParts.find { it.name == replacementPart.name }

        if (targetPart != null) {
            val before = result.substring(0, targetPart.contentStartIndex)
            val after  = result.substring(targetPart.contentEndIndex)
            result = before + "\n" + newContent + "\n" + after
        }
    }
    return result
}

// ─────────────────────────────────────────────
// Syntax highlighting
// ─────────────────────────────────────────────

private val COLOR_KEYWORD    = 0xFF569CD6.toInt()
private val COLOR_STRING     = 0xFFCE9178.toInt()
private val COLOR_COMMENT    = 0xFF6A9955.toInt()
private val COLOR_NUMBER     = 0xFFB5CEA8.toInt()
private val COLOR_ANNOTATION = 0xFFDCDCAA.toInt()

private val KEYWORDS = setOf(
    "val", "var", "fun", "class", "object", "interface", "if", "else", "when",
    "for", "while", "do", "return", "import", "package", "override", "private",
    "public", "protected", "internal", "companion", "data", "sealed", "abstract",
    "open", "lateinit", "by", "in", "is", "as", "null", "true", "false", "this",
    "super", "typealias", "enum", "suspend", "inline", "reified", "init",
    "constructor", "get", "set", "it", "try", "catch", "finally", "throw",
    "break", "continue", "where", "out", "crossinline", "noinline", "vararg"
)

private val keywordRegex      = Regex("\\b(${KEYWORDS.joinToString("|")})\\b")
private val numberRegex       = Regex("\\b\\d+\\.?\\d*[fFdDlL]?\\b")
private val annotationRegex   = Regex("@[A-Za-z]\\w*")
private val lineCommentRegex  = Regex("//[^\n]*")
private val blockCommentRegex = Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL)
private val stringRegex       = Regex("\"[^\"\n]*\"|'[^'\n]*'")

// Span position only — no Android objects, safe to build on a background thread
data class SpanInfo(val start: Int, val end: Int, val color: Int)

fun computeSpans(text: String): List<SpanInfo> {
    val result   = mutableListOf<SpanInfo>()
    val excluded = mutableListOf<IntRange>()

    // ── Collect excluded ranges ────────────────────────────────────────────
    val lineComments = lineCommentRegex.findAll(text).map { it.range }.toList()
    excluded.addAll(lineComments)

    val blockComments = blockCommentRegex.findAll(text)
        .map { it.range }
        .filter { block -> excluded.none { ex -> block.first >= ex.first && block.last <= ex.last } }
        .toList()
    excluded.addAll(blockComments)

    val strings = stringRegex.findAll(text)
        .map { it.range }
        .filter { s -> excluded.none { ex -> s.first >= ex.first && s.last <= ex.last } }
        .toList()
    excluded.addAll(strings)

    fun IntRange.isExcluded() = excluded.any { ex -> first >= ex.first && last <= ex.last }

    // ── Keywords ──────────────────────────────────────────────────────────
    keywordRegex.findAll(text).forEach { m ->
        if (!m.range.isExcluded())
            result.add(SpanInfo(m.range.first, m.range.last + 1, COLOR_KEYWORD))
    }

    // ── Numbers ───────────────────────────────────────────────────────────
    numberRegex.findAll(text).forEach { m ->
        if (!m.range.isExcluded())
            result.add(SpanInfo(m.range.first, m.range.last + 1, COLOR_NUMBER))
    }

    // ── Annotations ───────────────────────────────────────────────────────
    annotationRegex.findAll(text).forEach { m ->
        if (!m.range.isExcluded())
            result.add(SpanInfo(m.range.first, m.range.last + 1, COLOR_ANNOTATION))
    }

    // ── Strings (override keywords inside them) ────────────────────────────
    strings.forEach { result.add(SpanInfo(it.first, it.last + 1, COLOR_STRING)) }

    // ── Comments (highest priority — applied last) ─────────────────────────
    lineComments.forEach  { result.add(SpanInfo(it.first, it.last + 1, COLOR_COMMENT)) }
    blockComments.forEach { result.add(SpanInfo(it.first, it.last + 1, COLOR_COMMENT)) }

    return result
}

// Called on main thread only — touches the Editable
fun applySpans(editable: Editable, spans: List<SpanInfo>) {
    editable.getSpans(0, editable.length, ForegroundColorSpan::class.java)
        .forEach { editable.removeSpan(it) }

    val len = editable.length
    spans.forEach { (start, end, color) ->
        if (end <= len) {   // guard against stale spans on shorter text
            editable.setSpan(
                ForegroundColorSpan(color),
                start, end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }
}

// ─────────────────────────────────────────────
// Helper function to paste from clipboard
// ─────────────────────────────────────────────

private fun pasteFromClipboard(context: Context, onPasted: (String) -> Unit) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = clipboard.primaryClip
    if (clip != null && clip.itemCount > 0) {
        val pastedText = clip.getItemAt(0).text.toString()
        onPasted(pastedText)
        Toast.makeText(context, "Pasted!", Toast.LENGTH_SHORT).show()
    } else {
        Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
    }
}

// ─────────────────────────────────────────────
// Screen
// ─────────────────────────────────────────────

@Composable
fun AutoCPScreen() {
    val context = LocalContext.current
    val editTextRef       = remember { mutableStateOf<EditText?>(null) }
    var showReplaceDialog by remember { mutableStateOf(false) }
    var showPartsGuide    by remember { mutableStateOf(false) }
    var replacementText   by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E))
            .systemBarsPadding()
    ) {

        // ── Header ────────────────────────────────────────────────────────
        Surface(
            color = Color(0xFF2D2D2D),
            shadowElevation = 4.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Title - centered
                Text(
                    text = "Auto Copy/Paste",
                    color = Color(0xFFCCCCCC),
                    fontSize = 18.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 4.dp)
                )
                
                // Buttons row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { showPartsGuide = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            "Parts?",
                            color = Color(0xFF569CD6),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                    
                    TextButton(
                        onClick = {
                            editTextRef.value?.setText("")
                            Toast.makeText(context, "Cleared!", Toast.LENGTH_SHORT).show()
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            "Delete",
                            color = Color(0xFFFF6B6B),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                    
                    TextButton(
                        onClick = { editTextRef.value?.selectAll() },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            "Select All",
                            color = Color(0xFFCCCCCC),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center
                        )
                    }
                    
                    TextButton(
                        onClick = {
                            editTextRef.value?.let { et ->
                                val selStart = et.selectionStart
                                val selEnd   = et.selectionEnd
                                val textToCopy = if (selEnd > selStart) {
                                    et.text.substring(selStart, selEnd)
                                } else {
                                    et.text.toString()
                                }
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("code", textToCopy))
                                Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            "Copy",
                            color = Color(0xFFCCCCCC),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center
                        )
                    }
                    
                    TextButton(
                        onClick = {
                            pasteFromClipboard(context) { pastedText ->
                                editTextRef.value?.setText(pastedText)
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            "Paste",
                            color = Color(0xFF4CAF50),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                    
                    TextButton(
                        onClick = { replacementText = ""; showReplaceDialog = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            "Replace",
                            color = Color(0xFFCCCCCC),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // ── Native code editor (read-only but with proper text display) ──
        AndroidView(
            factory = { ctx ->
                val handler             = Handler(Looper.getMainLooper())
                var highlightRunnable: Runnable? = null
                val screenWidth         = ctx.resources.displayMetrics.widthPixels

                val editText = EditText(ctx).apply {
                    // ── Appearance ─────────────────────────────────────────
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    setTextColor(android.graphics.Color.WHITE)
                    setHintTextColor(0xFF555555.toInt())
                    hint     = "Tap 'Paste' to paste code from clipboard..."
                    typeface = Typeface.MONOSPACE
                    textSize = 14f
                    gravity  = Gravity.TOP or Gravity.START
                    setPadding(32, 32, 32, 32)

                    // ── Keep it editable for proper text formatting but disable keyboard ──
                    isFocusable = true
                    isFocusableInTouchMode = true
                    isClickable = true
                    isLongClickable = true
                    showSoftInputOnFocus = false  // This prevents keyboard
                    
                    // Disable text input but allow programmatic changes
                    keyListener = null  // This prevents keyboard input
                    
                    // ── Size ────────────────────────────────────────────────
                    minWidth = screenWidth
                    minLines = 30
                    layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)

                    // ── Scroll bars ─────────────────────────────────────────
                    isHorizontalScrollBarEnabled = false
                    isVerticalScrollBarEnabled   = false

                    // ── Syntax highlighting ──────────────────────────────
                    addTextChangedListener(object : TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                        override fun afterTextChanged(s: Editable?) {
                            highlightRunnable?.let { handler.removeCallbacks(it) }
                            val r = Runnable {
                                val snapshot = text?.toString() ?: return@Runnable
                                Thread {
                                    val spans = computeSpans(snapshot)
                                    handler.post {
                                        val editable = text ?: return@post
                                        if (editable.length == snapshot.length) {
                                            applySpans(editable, spans)
                                        }
                                    }
                                }.start()
                            }
                            highlightRunnable = r
                            handler.postDelayed(r, 500L)
                        }
                    })

                    editTextRef.value = this
                }

                // HorizontalScrollView — handles left/right swiping
                val hsv = HorizontalScrollView(ctx).apply {
                    isHorizontalScrollBarEnabled = true
                    isVerticalScrollBarEnabled   = false
                    isFillViewport               = false
                    layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
                    addView(editText)
                }

                // ScrollView — handles up/down swiping
                ScrollView(ctx).apply {
                    isVerticalScrollBarEnabled   = true
                    isHorizontalScrollBarEnabled = false
                    isFillViewport               = false
                    layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
                    addView(hsv)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
    }

    // ── PARTS Guide Dialog ────────────────────────────────────────────────────
    if (showPartsGuide) {
        Dialog(
            onDismissRequest = { showPartsGuide = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.85f),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF2D2D2D)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Dialog header with Copy button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "PARTS System Guide",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.White
                        )
                        
                        TextButton(
                            onClick = {
                                val guideText = """
                                    PARTS System Guide
                                    
                                    What are PARTS?
                                    PARTS let you organize your code into replaceable blocks. Each part is wrapped with START/END markers. All parts are flat and independent - no nesting.
                                    
                                    Part Format:
                                    //PART 0 START
                                    code here...
                                    //PART 0 END
                                    
                                    //PART 1 START
                                    more code...
                                    //PART 1 END
                                    
                                    //PART 1.1 START
                                    sub part...
                                    //PART 1.1 END
                                    
                                    Part Numbers:
                                    • Main parts: 0, 1, 2, 3 ... up to 99
                                    • Sub parts: 1.1, 1.2, 5.1, 5.2 ... (group.sub)
                                    • All parts are independent flat blocks
                                    • No nesting needed
                                    • Sub parts are just for organizing related code
                                    
                                    Rules:
                                    • PART markers must start at column 0
                                    • Each part is a complete, independent block
                                    • No shared braces or scopes between parts
                                    • Replace only affects the parts you specify
                                    • Unspecified parts stay exactly as they are
                                    
                                    How to Replace:
                                    1. Paste your code using 'Paste' button
                                    2. Tap 'Replace' button
                                    3. Paste replacement code with PART markers
                                    4. Only the parts you include will be replaced
                                """.trimIndent()
                                
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("PARTS Guide", guideText))
                                Toast.makeText(context, "Guide copied!", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text(
                                "Copy",
                                color = Color(0xFF569CD6),
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    
                    Divider(color = Color(0xFF444444))
                    
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("▎What are PARTS?", color = Color(0xFF569CD6), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("PARTS let you organize your code into replaceable blocks. Each part is wrapped with START/END markers. All parts are flat and independent - no nesting.", color = Color(0xFFCCCCCC), fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 18.sp)

                        Text("▎Part Format", color = Color(0xFF569CD6), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("//PART 0 START\ncode here...\n//PART 0 END\n\n//PART 1 START\nmore code...\n//PART 1 END\n\n//PART 1.1 START\nsub part...\n//PART 1.1 END\n\n//PART 1.2 START\nanother sub...\n//PART 1.2 END\n\n//PART 2 START\neven more...\n//PART 2 END", color = Color(0xFF6A9955), fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 16.sp)

                        Text("▎Part Numbers", color = Color(0xFF569CD6), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("• Main parts: 0, 1, 2, 3 ... up to 99\n• Sub parts: 1.1, 1.2, 5.1, 5.2 ... (group.sub)\n• All parts are independent flat blocks\n• No nesting needed - each part is self-contained\n• Sub parts are just for organizing related code\n• Dot notation is purely for grouping (1.1 means group 1, sub 1)", color = Color(0xFFCCCCCC), fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 18.sp)

                        Text("▎Rules", color = Color(0xFF569CD6), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("• PART markers must start at column 0 (no indentation)\n• Each part is a complete, independent block\n• No shared braces or scopes between parts\n• Replace only affects the parts you specify\n• Unspecified parts stay exactly as they are\n• Parts don't need to be in order", color = Color(0xFFCCCCCC), fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 18.sp)

                        Text("▎How to Replace", color = Color(0xFF569CD6), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("1. Paste your code using 'Paste' button\n2. Tap 'Replace' button\n3. Paste replacement code with PART markers\n4. Only the parts you include will be replaced\n5. Other parts stay unchanged\n\nExample:\n//PART 1 START\nnew code for part 1\n//PART 1 END\n\n//PART 2.3 START\nupdated sub part\n//PART 2.3 END", color = Color(0xFFCCCCCC), fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 18.sp)
                    }
                    
                    Divider(color = Color(0xFF444444))
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        TextButton(onClick = { showPartsGuide = false }) {
                            Text("Got it", color = Color(0xFF4CAF50), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }

    // ── Replace Dialog ────────────────────────────────────────────────────
    if (showReplaceDialog) {
        Dialog(
            onDismissRequest = { showReplaceDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.8f),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF2D2D2D)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Dialog header with Paste button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Replace Parts",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.White
                        )
                        
                        TextButton(
                            onClick = {
                                pasteFromClipboard(context) { pastedText ->
                                    replacementText = pastedText
                                }
                            }
                        ) {
                            Text(
                                "Paste",
                                color = Color(0xFF4CAF50),
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    
                    Divider(color = Color(0xFF444444))
                    
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(16.dp)
                    ) {
                        Text(
                            "Paste replacement code with PART markers:",
                            color      = Color(0xFFCCCCCC),
                            fontSize   = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        
                        OutlinedTextField(
                            value         = replacementText,
                            onValueChange = { replacementText = it },
                            modifier      = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color      = Color.White,
                                fontFamily = FontFamily.Monospace,
                                fontSize   = 14.sp
                            ),
                            placeholder = {
                                Text(
                                    "//PART 1 START\nreplacement codes...\n//PART 1 END\n\n//PART 2.3 START\nmore codes...\n//PART 2.3 END",
                                    color      = Color(0xFF666666),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize   = 14.sp
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor   = Color(0xFF555555),
                                unfocusedBorderColor = Color(0xFF444444),
                                cursorColor          = Color.White
                            )
                        )
                    }
                    
                    Divider(color = Color(0xFF444444))
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showReplaceDialog = false }) {
                            Text("Cancel", color = Color(0xFF888888), fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                        }
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        TextButton(
                            onClick = {
                                if (replacementText.isNotBlank()) {
                                    val et          = editTextRef.value
                                    val currentCode = et?.text?.toString() ?: ""
                                    val newCode     = replaceParts(currentCode, replacementText)
                                    et?.setText(newCode)
                                    val partsReplaced = findParts(replacementText).size
                                    Toast.makeText(context, "Replaced $partsReplaced part(s)!", Toast.LENGTH_SHORT).show()
                                }
                                showReplaceDialog = false
                            }
                        ) {
                            Text("Replace All", color = Color(0xFF4CAF50), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}
