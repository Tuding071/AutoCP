package com.autocp.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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

// Simple syntax highlighting for common code patterns
fun highlightCode(code: String): androidx.compose.ui.text.AnnotatedString {
    return buildAnnotatedString {
        // Keywords (blue)
        val keywords = listOf(
            "fun", "val", "var", "class", "object", "interface", "package",
            "import", "if", "else", "when", "for", "while", "do", "return",
            "override", "private", "public", "protected", "internal", "data",
            "sealed", "enum", "abstract", "open", "const", "lateinit", "suspend",
            "companion", "true", "false", "null", "this", "super", "in", "is", "as",
            "typealias", "constructor", "init", "where", "by", "out", "reified",
            "int", "String", "Boolean", "Double", "Float", "Long", "Short", "Byte",
            "Char", "Unit", "Any", "Nothing", "List", "Map", "Set", "Array",
            "Int", "Double", "Float", "Long", "Short", "Byte", "Char"
        )
        
        var i = 0
        while (i < code.length) {
            var matched = false
            
            // Check for keywords
            for (keyword in keywords) {
                if (code.startsWith(keyword, i)) {
                    val nextChar = if (i + keyword.length < code.length) code[i + keyword.length] else ' '
                    if (!nextChar.isLetterOrDigit()) {
                        withStyle(SpanStyle(color = Color(0xFF569CD6), fontWeight = FontWeight.Bold)) {
                            append(keyword)
                        }
                        i += keyword.length
                        matched = true
                        break
                    }
                }
            }
            
            if (!matched) {
                // Check for comments (green)
                if (i + 1 < code.length && code[i] == '/' && code[i + 1] == '/') {
                    val end = code.indexOf('\n', i).let { if (it == -1) code.length else it }
                    withStyle(SpanStyle(color = Color(0xFF6A9955))) {
                        append(code.substring(i, end))
                    }
                    i = end
                    matched = true
                }
                
                // Check for strings (orange)
                if (!matched && code[i] == '"') {
                    val end = code.indexOf('"', i + 1).let { if (it == -1) code.length else it + 1 }
                    withStyle(SpanStyle(color = Color(0xFFCE9178))) {
                        append(code.substring(i, end))
                    }
                    i = end
                    matched = true
                }
                
                // Check for numbers (light green)
                if (!matched && code[i].isDigit()) {
                    val start = i
                    while (i < code.length && (code[i].isDigit() || code[i] == '.')) i++
                    withStyle(SpanStyle(color = Color(0xFFB5CEA8))) {
                        append(code.substring(start, i))
                    }
                    matched = true
                }
                
                // Check for annotations (gold)
                if (!matched && code[i] == '@') {
                    val start = i
                    while (i < code.length && code[i].isLetterOrDigit()) i++
                    withStyle(SpanStyle(color = Color(0xFFC8A14C))) {
                        append(code.substring(start, i))
                    }
                    matched = true
                }
                
                // Check for functions/calls (yellow)
                if (!matched && code[i].isLetter()) {
                    val start = i
                    while (i < code.length && code[i].isLetterOrDigit()) i++
                    val word = code.substring(start, i)
                    if (i < code.length && code[i] == '(') {
                        withStyle(SpanStyle(color = Color(0xFFDCDCAA))) {
                            append(word)
                        }
                    } else {
                        append(word)
                    }
                    matched = true
                }
                
                if (!matched) {
                    // Default character (light grey)
                    withStyle(SpanStyle(color = Color(0xFFD4D4D4))) {
                        append(code[i])
                    }
                    i++
                }
            }
        }
    }
}

@Composable
fun AutoCPScreen() {
    var code by remember { mutableStateOf(TextFieldValue("")) }
    val highlightedCode = remember(code.text) { highlightCode(code.text) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E)) // dark editor background
            .systemBarsPadding() // respect system bars
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF2D2D2D)) // dark header
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = "Auto Copy/Paste",
                color = Color(0xFFCCCCCC),
                fontSize = 16.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }

        // Code editor
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            val verticalScrollState = rememberScrollState()
            val horizontalScrollState = rememberScrollState()

            BasicTextField(
                value = code,
                onValueChange = { code = it },
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(verticalScrollState)
                    .horizontalScroll(horizontalScrollState),
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                ),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF1E1E1E)) // same as editor bg
                            .padding(12.dp)
                    ) {
                        if (code.text.isEmpty()) {
                            Text(
                                text = "// Paste or type code here...",
                                color = Color(0xFF6A9955), // comment green
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        innerTextField()
                    }
                },
                visualTransformation = { annotatedString ->
                    androidx.compose.ui.text.input.TransformedText(
                        highlightedCode,
                        androidx.compose.ui.text.input.OffsetMapping.Identity
                    )
                }
            )
        }
    }
}
