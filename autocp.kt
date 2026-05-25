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
    val name: String,
    val startIndex: Int,
    val contentStartIndex: Int,
    val contentEndIndex: Int,
    val endIndex: Int
)

private val partStartRegex = Regex("""//PART\s+(\d+)(?:-([A-Z]))?\s+START""")
private val partEndRegex = Regex("""//PART\s+\d+(?:-[A-Z])?\s+END""")

fun findParts(code: String): List<PartInfo> {
    val parts = mutableListOf<PartInfo>()
    
    var searchFrom = 0
    while (searchFrom < code.length) {
        val startMatch = partStartRegex.find(code, searchFrom) ?: break
        val endMatch = partEndRegex.find(code, startMatch.range.last + 1) ?: break
        
        val num = startMatch.groupValues[1]
        val sub = startMatch.groupValues.getOrNull(2)?.takeIf { it.isNotEmpty() }
        val partName = if (sub != null) "$num-$sub" else num
        
        parts.add(
            PartInfo(
                name = partName,
                startIndex = startMatch.range.first,
                contentStartIndex = startMatch.range.last + 1,
                contentEndIndex = endMatch.range.first,
                endIndex = endMatch.range.last + 1
            )
        )
        
        searchFrom = endMatch.range.last + 1
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
    var codeText by remember { mutableStateOf("") }
    var selection by remember { mutableStateOf(TextRange.Zero) }
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
                        selection = TextRange(0, codeText.length)
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
                        val textToCopy = if (selection.length > 0) {
                            codeText.substring(selection.start, selection.end)
                        } else {
                            codeText
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

        // Code editor - using String directly instead of TextFieldValue for performance
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            val verticalScrollState = rememberScrollState()
            val horizontalScrollState = rememberScrollState()

            BasicTextField(
                value = TextFieldValue(codeText, selection),
                onValueChange = { newValue ->
                    codeText = newValue.text
                    selection = newValue.selection
                },
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
                        text = "PARTS let you organize your code into replaceable blocks. Each part is wrapped with START/END markers so you can quickly swap out sections without touching the rest of your code.",
                        color = Color(0xFFCCCCCC),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                    
                    Text(
                        text = "▎Main Parts (0-99)",
                        color = Color(0xFF569CD6),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "//PART 0 START\n//PART 1 START\n//PART 2 START\n...\n//PART 99 START",
                        color = Color(0xFF6A9955),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                    Text(
                        text = "Use numbers 0-99 for your main code sections. Example: PART 0 for imports, PART 1 for UI, PART 2 for logic, etc.",
                        color = Color(0xFFCCCCCC),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                    
                    Text(
                        text = "▎Sub Parts (A-Z)",
                        color = Color(0xFF569CD6),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "//PART 5-A START\n//PART 5-B START\n...\n//PART 5-Z START",
                        color = Color(0xFF6A9955),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                    Text(
                        text = "If a part is too long, split it into sub-parts. Add a dash and letter (A-Z) after the part number. Sub parts are independent and can be replaced separately from their parent part.",
                        color = Color(0xFFCCCCCC),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                    
                    Text(
                        text = "▎Full Example",
                        color = Color(0xFF569CD6),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "//PART 0 START\npackage com.example\nimport androidx...\n//PART 0 END\n\n//PART 1 START\n@Composable\nfun MyScreen() {\n    //PART 1-A START\n    Column {\n        Text(\"Hello\")\n    }\n    //PART 1-A END\n    \n    //PART 1-B START\n    Button(onClick = {})\n    //PART 1-B END\n}\n//PART 1 END",
                        color = Color(0xFF6A9955),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                    
                    Text(
                        text = "▎How to Replace",
                        color = Color(0xFF569CD6),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "1. Tap 'Replace' button\n2. Paste replacement code with PART markers\n3. Only the parts you include will be replaced\n4. Other parts stay unchanged\n\nExample replacement:\n//PART 1 START\nnew UI code here\n//PART 1 END\n//PART 3-C START\nupdated sub part\n//PART 3-C END",
                        color = Color(0xFFCCCCCC),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                    
                    Text(
                        text = "▎Tips",
                        color = Color(0xFF569CD6),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "• PART names must match exactly (case sensitive)\n• Sub parts use capital letters A-Z only\n• You can replace main parts and sub parts in one paste\n• The Replace feature auto-detects which parts to update\n• Empty replacement = empty part content",
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
                                "//PART 1 START\ncodes...\n//PART 1 END\n\n//PART 5-B START\nmore codes...\n//PART 5-B END",
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
                            val newCode = replaceParts(codeText, replacementText)
                            codeText = newCode
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
