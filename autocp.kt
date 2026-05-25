package com.autocp.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
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

data class PartInfo(
    val name: String,           // e.g. "1", "1.1", "5.3"
    val startIndex: Int,
    val contentStartIndex: Int,
    val contentEndIndex: Int,
    val endIndex: Int
)

private val partStartRegex = Regex("""^//PART\s+(\d+(?:\.\d+)?)\s+START""", RegexOption.MULTILINE)
private val partEndRegex = Regex("""^//PART\s+\d+(?:\.\d+)?\s+END""", RegexOption.MULTILINE)

fun findParts(code: String): List<PartInfo> {
    val parts = mutableListOf<PartInfo>()
    
    val startMatches = partStartRegex.findAll(code).toList()
    val endMatches = partEndRegex.findAll(code).toList()
    
    for (startMatch in startMatches) {
        val partName = startMatch.groupValues[1]
        
        val endMatch = endMatches.find { 
            it.range.first > startMatch.range.last && 
            it.value.contains(Regex("""//PART\s+${Regex.escape(partName)}\s+END"""))
        }
        
        if (endMatch != null) {
            parts.add(
                PartInfo(
                    name = partName,
                    startIndex = startMatch.range.first,
                    contentStartIndex = startMatch.range.last + 1,
                    contentEndIndex = endMatch.range.first,
                    endIndex = endMatch.range.last + 1
                )
            )
        }
    }
    
    return parts
}

fun replaceParts(originalCode: String, replacementCode: String): String {
    val replacementParts = findParts(replacementCode)
    if (replacementParts.isEmpty()) return originalCode
    
    var result = originalCode
    
    for (replacementPart in replacementParts) {
        val newContent = replacementCode.substring(
            replacementPart.contentStartIndex,
            replacementPart.contentEndIndex
        ).trim('\n', '\r')
        
        val originalParts = findParts(result)
        val targetPart = originalParts.find { it.name == replacementPart.name }
        
        if (targetPart != null) {
            val before = result.substring(0, targetPart.contentStartIndex)
            val after = result.substring(targetPart.contentEndIndex)
            result = before + "\n" + newContent + "\n" + after
        }
    }
    
    return result
}

@Composable
fun AutoCPScreen() {
    var code by remember { mutableStateOf(TextFieldValue("")) }
    var showReplaceDialog by remember { mutableStateOf(false) }
    var showPartsGuide by remember { mutableStateOf(false) }
    var replacementText by remember { mutableStateOf("") }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E))
            .imePadding()
            .systemBarsPadding()
    ) {
        // Header
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
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TextButton(
                    onClick = {
                        code = code.copy(selection = TextRange(0, code.text.length))
                    },
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
                        val textToCopy = if (code.selection.length > 0) {
                            code.text.substring(code.selection.start, code.selection.end)
                        } else {
                            code.text
                        }
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("code", textToCopy)
                        clipboard.setPrimaryClip(clip)
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
                    onClick = {
                        replacementText = ""
                        showReplaceDialog = true
                    },
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
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF1E1E1E))
                            .padding(12.dp)
                    ) {
                        innerTextField()
                    }
                }
            )
        }
    }

    // PARTS Guide Dialog
    if (showPartsGuide) {
        AlertDialog(
            onDismissRequest = { showPartsGuide = false },
            containerColor = Color(0xFF2D2D2D),
            titleContentColor = Color.White,
            textContentColor = Color(0xFFCCCCCC),
            title = {
                Text(
                    "PARTS System Guide",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "▎What are PARTS?",
                        color = Color(0xFF569CD6),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "PARTS let you organize your code into replaceable blocks. Each part is wrapped with START/END markers. All parts are flat and independent - no nesting.",
                        color = Color(0xFFCCCCCC),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                    
                    Text(
                        text = "▎Part Format",
                        color = Color(0xFF569CD6),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "//PART 0 START\ncode here...\n//PART 0 END\n\n//PART 1 START\nmore code...\n//PART 1 END\n\n//PART 1.1 START\nsub part...\n//PART 1.1 END\n\n//PART 1.2 START\nanother sub...\n//PART 1.2 END\n\n//PART 2 START\neven more...\n//PART 2 END",
                        color = Color(0xFF6A9955),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                    
                    Text(
                        text = "▎Part Numbers",
                        color = Color(0xFF569CD6),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "• Main parts: 0, 1, 2, 3 ... up to 99\n• Sub parts: 1.1, 1.2, 5.1, 5.2 ... (group.sub)\n• All parts are independent flat blocks\n• No nesting needed - each part is self-contained\n• Sub parts are just for organizing related code\n• Dot notation is purely for grouping (1.1 means group 1, sub 1)",
                        color = Color(0xFFCCCCCC),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                    
                    Text(
                        text = "▎Rules",
                        color = Color(0xFF569CD6),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "• PART markers must start at column 0 (no indentation)\n• Each part is a complete, independent block\n• No shared braces or scopes between parts\n• Replace only affects the parts you specify\n• Unspecified parts stay exactly as they are\n• Parts don't need to be in order",
                        color = Color(0xFFCCCCCC),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                    
                    Text(
                        text = "▎How to Replace",
                        color = Color(0xFF569CD6),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "1. Tap 'Replace' button\n2. Paste replacement code with PART markers\n3. Only the parts you include will be replaced\n4. Other parts stay unchanged\n\nExample:\n//PART 1 START\nnew code for part 1\n//PART 1 END\n\n//PART 2.3 START\nupdated sub part\n//PART 2.3 END",
                        color = Color(0xFFCCCCCC),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showPartsGuide = false }
                ) {
                    Text(
                        "Got it",
                        color = Color(0xFF4CAF50),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        )
    }

    // Replace Dialog
    if (showReplaceDialog) {
        AlertDialog(
            onDismissRequest = { showReplaceDialog = false },
            containerColor = Color(0xFF2D2D2D),
            titleContentColor = Color.White,
            textContentColor = Color(0xFFCCCCCC),
            title = {
                Text(
                    "Replace Parts",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Paste replacement code with PART markers:",
                        color = Color(0xFFCCCCCC),
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    
                    OutlinedTextField(
                        value = replacementText,
                        onValueChange = { replacementText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 200.dp, max = 400.dp),
                        textStyle = TextStyle(
                            color = Color.White,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp
                        ),
                        placeholder = {
                            Text(
                                "//PART 1 START\nreplacement codes...\n//PART 1 END\n\n//PART 2.3 START\nmore codes...\n//PART 2.3 END",
                                color = Color(0xFF666666),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF555555),
                            unfocusedBorderColor = Color(0xFF444444),
                            cursorColor = Color.White
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (replacementText.isNotBlank()) {
                            val newCode = replaceParts(code.text, replacementText)
                            code = TextFieldValue(newCode)
                            val partsReplaced = findParts(replacementText).size
                            Toast.makeText(context, "Replaced $partsReplaced part(s)!", Toast.LENGTH_SHORT).show()
                        }
                        showReplaceDialog = false
                    }
                ) {
                    Text(
                        "Replace All",
                        color = Color(0xFF4CAF50),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showReplaceDialog = false }
                ) {
                    Text(
                        "Cancel",
                        color = Color(0xFF888888),
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        )
    }
}
