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
import android.widget.ScrollView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

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
// PARTS logic  (unchanged)
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
                name             = partName,
                startIndex       = startMatch.range.first,
                contentStartIndex = startMatch.range.last + 1,
                contentEndIndex  = endMatch.range.first,
                endIndex         = endMatch.range.last + 1
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

private val COLOR_KEYWORD    = 0xFF569CD6.toInt()   // blue
private val COLOR_STRING     = 0xFFCE9178.toInt()   // orange
private val COLOR_COMMENT    = 0xFF6A9955.toInt()   // green
private val COLOR_NUMBER     = 0xFFB5CEA8.toInt()   // light green
private val COLOR_ANNOTATION = 0xFFDCDCAA.toInt()   // yellow

private val KEYWORDS = setOf(
    "val", "var", "fun", "class", "object", "interface", "if", "else", "when",
    "for", "while", "do", "return", "import", "package", "override", "private",
    "public", "protected", "internal", "companion", "data", "sealed", "abstract",
    "open", "lateinit", "by", "in", "is", "as", "null", "true", "false", "this",
    "super", "typealias", "enum", "suspend", "inline", "reified", "init",
    "constructor", "get", "set", "it", "try", "catch", "finally", "throw",
    "break", "continue", "where", "out", "crossinline", "noinline", "vararg"
)

private val keywordRegex     = Regex("\\b(${KEYWORDS.joinToString("|")})\\b")
private val numberRegex      = Regex("\\b\\d+\\.?\\d*[fFdDlL]?\\b")
private val annotationRegex  = Regex("@[A-Za-z]\\w*")
private val lineCommentRegex = Regex("//[^\n]*")
private val blockCommentRegex = Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL)
private val stringRegex      = Regex("\"[^\"\n]*\"|'[^'\n]*'")

fun applyHighlighting(editable: Editable) {
    val text = editable.toString()

    // Remove all existing color spans in one pass
    editable.getSpans(0, editable.length, ForegroundColorSpan::class.java)
        .forEach { editable.removeSpan(it) }

    // ── Collect excluded ranges (strings + comments) ──────────────────────
    // We apply strings/comments LAST so they visually override keywords,
    // and we track their ranges to skip useless keyword spans inside them.

    val excluded = mutableListOf<IntRange>()

    // Single-line comments
    val lineComments = lineCommentRegex.findAll(text).map { it.range }.toList()
    excluded.addAll(lineComments)

    // Block comments  (skip ranges already covered by line comments)
    val blockComments = blockCommentRegex.findAll(text)
        .map { it.range }
        .filter { block -> excluded.none { ex -> block.first >= ex.first && block.last <= ex.last } }
        .toList()
    excluded.addAll(blockComments)

    // Strings  (skip ranges already inside comments)
    val strings = stringRegex.findAll(text)
        .map { it.range }
        .filter { s -> excluded.none { ex -> s.first >= ex.first && s.last <= ex.last } }
        .toList()
    excluded.addAll(strings)

    fun IntRange.isExcluded(): Boolean =
        excluded.any { ex -> first >= ex.first && last <= ex.last }

    fun setSpan(color: Int, range: IntRange) =
        editable.setSpan(
            ForegroundColorSpan(color),
            range.first, range.last + 1,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

    // ── Keywords ──────────────────────────────────────────────────────────
    keywordRegex.findAll(text).forEach { m ->
        if (!m.range.isExcluded()) setSpan(COLOR_KEYWORD, m.range)
    }

    // ── Numbers ───────────────────────────────────────────────────────────
    numberRegex.findAll(text).forEach { m ->
        if (!m.range.isExcluded()) setSpan(COLOR_NUMBER, m.range)
    }

    // ── Annotations ───────────────────────────────────────────────────────
    annotationRegex.findAll(text).forEach { m ->
        if (!m.range.isExcluded()) setSpan(COLOR_ANNOTATION, m.range)
    }

    // ── Strings (override keywords inside them) ───────────────────────────
    strings.forEach      { setSpan(COLOR_STRING, it) }

    // ── Comments (highest priority – applied last) ─────────────────────────
    lineComments.forEach { setSpan(COLOR_COMMENT, it) }
    blockComments.forEach { setSpan(COLOR_COMMENT, it) }
}

// ─────────────────────────────────────────────
// Screen
// ─────────────────────────────────────────────

@Composable
fun AutoCPScreen() {
    val context = LocalContext.current

    // Single ref to the native EditText — avoids triggering recomposition
    val editTextRef = remember { mutableStateOf<EditText?>(null) }

    var showReplaceDialog by remember { mutableStateOf(false) }
    var showPartsGuide    by remember { mutableStateOf(false) }
    var replacementText   by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E))
            .imePadding()
            .systemBarsPadding()
    ) {

        // ── Header ────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF2D2D2D))
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(
                onClick = { showPartsGuide = true },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    "PARTS",
                    color = Color(0xFF569CD6),
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "Auto Copy/Paste",
                color = Color(0xFFCCCCCC),
                fontSize = 16.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = { editTextRef.value?.selectAll() },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        "Select All",
                        color = Color(0xFFCCCCCC),
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                TextButton(
                    onClick = {
                        val et = editTextRef.value ?: return@TextButton
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
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        "Copy",
                        color = Color(0xFFCCCCCC),
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                TextButton(
                    onClick = { replacementText = ""; showReplaceDialog = true },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        "Replace",
                        color = Color(0xFFCCCCCC),
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // ── Native code editor ────────────────────────────────────────────
        //
        // Architecture:
        //   AndroidView → ScrollView (vertical scroll)
        //                   └── EditText (horizontal scroll via setHorizontallyScrolling)
        //
        // Why this beats BasicTextField + external scroll modifiers:
        //  • Zero Compose recomposition on keystrokes — EditText owns its state
        //  • Native selection/drag handles — no touch-event interception conflict
        //  • Keyboard avoidance via imePadding() on the Column above works naturally
        //  • Syntax highlighting via SpannableString spans on the Editable directly
        //  • 500ms debounce so highlighting never fires while actively typing

        AndroidView(
            factory = { ctx ->
                val handler = Handler(Looper.getMainLooper())
                var highlightRunnable: Runnable? = null

                val editText = EditText(ctx).apply {
                    // Appearance
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    setTextColor(android.graphics.Color.WHITE)
                    setHintTextColor(0xFF555555.toInt())
                    hint = "Paste your code here..."
                    typeface = Typeface.MONOSPACE
                    textSize = 14f
                    gravity = Gravity.TOP or Gravity.START
                    setPadding(32, 32, 32, 32)

                    // Scroll behaviour
                    // isSingleLine = false must come FIRST — internally it calls
                    // setHorizontallyScrolling(false), which would override our setting.
                    // Setting it before lets setHorizontallyScrolling(true) win.
                    isSingleLine = false                // multi-line editing
                    setHorizontallyScrolling(true)      // no wrap; lines extend horizontally

                    // Scrollbars: horizontal on the EditText, vertical on the ScrollView
                    isHorizontalScrollBarEnabled = true
                    isVerticalScrollBarEnabled   = false

                    // Fill width of parent ScrollView; height grows with content
                    layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)

                    // Debounced syntax highlighting
                    addTextChangedListener(object : TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                        override fun afterTextChanged(s: Editable?) {
                            highlightRunnable?.let { handler.removeCallbacks(it) }
                            val r = Runnable {
                                val editable = text ?: return@Runnable
                                applyHighlighting(editable)
                            }
                            highlightRunnable = r
                            handler.postDelayed(r, 500L)
                        }
                    })

                    // Store ref for use by header buttons and dialogs
                    editTextRef.value = this
                }

                ScrollView(ctx).apply {
                    isVerticalScrollBarEnabled   = true
                    isHorizontalScrollBarEnabled = false
                    layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
                    addView(editText)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)     // fills all remaining height in the Column
        )
    }

    // ── PARTS Guide Dialog (unchanged) ────────────────────────────────────
    if (showPartsGuide) {
        AlertDialog(
            onDismissRequest = { showPartsGuide = false },
            containerColor   = Color(0xFF2D2D2D),
            titleContentColor = Color.White,
            textContentColor  = Color(0xFFCCCCCC),
            title = {
                Text(
                    "PARTS System Guide",
                    fontFamily  = FontFamily.Monospace,
                    fontWeight  = FontWeight.Bold,
                    fontSize    = 18.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
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
                    Text("1. Tap 'Replace' button\n2. Paste replacement code with PART markers\n3. Only the parts you include will be replaced\n4. Other parts stay unchanged\n\nExample:\n//PART 1 START\nnew code for part 1\n//PART 1 END\n\n//PART 2.3 START\nupdated sub part\n//PART 2.3 END", color = Color(0xFFCCCCCC), fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 18.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { showPartsGuide = false }) {
                    Text("Got it", color = Color(0xFF4CAF50), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // ── Replace Dialog ────────────────────────────────────────────────────
    if (showReplaceDialog) {
        AlertDialog(
            onDismissRequest = { showReplaceDialog = false },
            containerColor   = Color(0xFF2D2D2D),
            titleContentColor = Color.White,
            textContentColor  = Color(0xFFCCCCCC),
            title = {
                Text("Replace Parts", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Paste replacement code with PART markers:",
                        color      = Color(0xFFCCCCCC),
                        fontSize   = 13.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    OutlinedTextField(
                        value       = replacementText,
                        onValueChange = { replacementText = it },
                        modifier    = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 200.dp, max = 400.dp),
                        textStyle   = androidx.compose.ui.text.TextStyle(
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
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (replacementText.isNotBlank()) {
                            val et          = editTextRef.value
                            val currentCode = et?.text?.toString() ?: ""
                            val newCode     = replaceParts(currentCode, replacementText)
                            // Set text on the native EditText; TextWatcher will
                            // schedule a highlight pass 500ms after this call.
                            et?.setText(newCode)
                            et?.setSelection(0)
                            val partsReplaced = findParts(replacementText).size
                            Toast.makeText(context, "Replaced $partsReplaced part(s)!", Toast.LENGTH_SHORT).show()
                        }
                        showReplaceDialog = false
                    }
                ) {
                    Text("Replace All", color = Color(0xFF4CAF50), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showReplaceDialog = false }) {
                    Text("Cancel", color = Color(0xFF888888), fontFamily = FontFamily.Monospace)
                }
            }
        )
    }
}
