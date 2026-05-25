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

fun findParts(code: String): List<PartInfo> {
    val parts = mutableListOf<PartInfo>()
    val startRegex = Regex("//PART\\s+([A-Z0-9-]+)\\s+START")
    val endRegex = Regex("//PART\\s+[A-Z0-9-]+\\s+END")
    
    var searchFrom = 0
    while (searchFrom < code.length) {
        val startMatch = startRegex.find(code, searchFrom) ?: break
        val endMatch = endRegex.find(code, startMatch.range.last + 1) ?: break
        
        val partName = startMatch.groupValues[1]
        val contentStart = startMatch.range.last + 1
        val contentEnd = endMatch.range.first
        
        parts.add(
            PartInfo(
                name = partName,
                startIndex = startMatch.range.first,
                contentStartIndex = contentStart,
                contentEndIndex = contentEnd,
                endIndex = endMatch.range.last + 1
            )
        )
        
        searchFrom = endMatch.range.last + 1
    }
    
    return parts
}

fun findCurrentPart(code: String, cursorPos: Int): PartInfo? {
    val parts = findParts(code)
    return parts.find { cursorPos in it.contentStartIndex..it.contentEndIndex }
}

@Composable
fun AutoCPScreen() {
    var code by remember { mutableStateOf(TextFieldValue("")) }
    var showReplaceDialog by remember { mutableStateOf(false) }
    var replacementCode by remember { mutableStateOf("") }
    var selectedPart by remember { mutableStateOf<PartInfo?>(null) }
    val context = LocalContext.current

    // Detect current part based on cursor position
    val currentPart = remember(code.selection) {
        findCurrentPart(code.text, code.selection.start)
    }

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
                        // Select All
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
                        // Copy selected text or all text
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
                        val parts = findParts(code.text)
                        if (parts.isEmpty()) {
                            Toast.makeText(context, "No parts detected", Toast.LENGTH_SHORT).show()
                        } else {
                            selectedPart = currentPart ?: parts.first()
                            replacementCode = ""
                            showReplaceDialog = true
                        }
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

    // Replace Dialog
    if (showReplaceDialog) {
        val parts = findParts(code.text)
        
        AlertDialog(
            onDismissRequest = { showReplaceDialog = false },
            containerColor = Color(0xFF2D2D2D),
            titleContentColor = Color.White,
            textContentColor = Color(0xFFCCCCCC),
            title = {
                Text(
                    "Replace Part",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Part selector dropdown
                    if (parts.isNotEmpty()) {
                        var expanded by remember { mutableStateOf(false) }
                        val selectedPartName = selectedPart?.name ?: parts.first().name
                        
                        Text(
                            "Select Part:",
                            color = Color(0xFFCCCCCC),
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        
                        Box {
                            TextButton(
                                onClick = { expanded = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "PART $selectedPartName ▼",
                                    color = Color.White,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 14.sp
                                )
                            }
                            
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                                modifier = Modifier.background(Color(0xFF3D3D3D))
                            ) {
                                parts.forEach { part ->
                                    DropdownMenuItem(
                                        onClick = {
                                            selectedPart = part
                                            expanded = false
                                        },
                                        text = {
                                            Text(
                                                "PART ${part.name}",
                                                color = Color.White,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                    
                    // Replacement code input
                    Text(
                        "Replacement Code:",
                        color = Color(0xFFCCCCCC),
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    
                    OutlinedTextField(
                        value = replacementCode,
                        onValueChange = { replacementCode = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp, max = 300.dp),
                        textStyle = TextStyle(
                            color = Color.White,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp
                        ),
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
                        selectedPart?.let { part ->
                            // Build new code: before part + replacement + after part
                            val before = code.text.substring(0, part.contentStartIndex)
                            val after = code.text.substring(part.contentEndIndex)
                            val newText = before + "\n" + replacementCode + "\n" + after
                            code = TextFieldValue(newText)
                        }
                        showReplaceDialog = false
                        Toast.makeText(context, "Part replaced!", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text(
                        "Replace",
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
