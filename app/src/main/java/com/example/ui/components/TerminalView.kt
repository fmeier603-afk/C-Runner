package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.OutputType
import com.example.data.model.TerminalLine
import com.example.ui.theme.TerminalBg
import com.example.ui.theme.TerminalBorder
import com.example.ui.theme.TerminalPrompt
import com.example.ui.theme.TerminalStderr
import com.example.ui.theme.TerminalStdout
import com.example.ui.theme.TerminalSurface
import com.example.ui.theme.TerminalSystem

@Composable
fun TerminalView(
    lines: List<TerminalLine>,
    isRunning: Boolean,
    currentPluginName: String?,
    onSendStdin: (String, Boolean) -> Unit,
    onCloseStdin: () -> Unit,
    onKillProcess: () -> Unit,
    onClearTerminal: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var autoScroll by remember { mutableStateOf(true) }
    var stdinInput by remember { mutableStateOf("") }

    // Auto-scroll to bottom when new output arrives if autoScroll is enabled
    LaunchedEffect(lines.size, autoScroll) {
        if (autoScroll && lines.isNotEmpty()) {
            listState.animateScrollToItem(lines.size - 1)
        }
    }

    // Pulse animation for RUNNING indicator
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TerminalBg)
            .imePadding()
    ) {
        // Terminal Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(TerminalSurface)
                .border(width = 1.dp, color = TerminalBorder)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Status indicator dot
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(
                            color = if (isRunning) Color(0xFFF59E0B) else Color(0xFF10B981),
                            shape = CircleShape
                        )
                        .alpha(if (isRunning) pulseAlpha else 1.0f)
                )

                Text(
                    text = currentPluginName ?: "Terminal Console",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                if (isRunning) {
                    Text(
                        text = "• RUNNING",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFF59E0B),
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isRunning) {
                    Button(
                        onClick = onKillProcess,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                        contentPadding = ButtonDefaults.TextButtonContentPadding,
                        modifier = Modifier
                            .height(32.dp)
                            .testTag("kill_process_button")
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = "Kill Process", modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Kill", fontSize = 12.sp, color = Color.White)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }

                IconButton(
                    onClick = {
                        val text = lines.joinToString(separator = "") { it.text }
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Terminal Log", text))
                        Toast.makeText(context, "Log copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("copy_terminal_button")
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Copy Log",
                        tint = Color.LightGray,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onClearTerminal,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("clear_terminal_button")
                ) {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = "Clear Terminal",
                        tint = Color.LightGray,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = { autoScroll = !autoScroll },
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("toggle_autoscroll_button")
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "Auto-scroll",
                        tint = if (autoScroll) MaterialTheme.colorScheme.primary else Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Terminal Output Screen
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            if (lines.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "TERMINAL READY",
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF6B7280),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Select a C++ plugin or binary to execute.\nOutput and diagnostics will stream here in real time.",
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF4B5563),
                        fontSize = 12.sp
                    )
                }
            } else {
                SelectionContainer {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("terminal_output_list")
                    ) {
                        items(lines, key = { it.id }) { line ->
                            val color = when (line.type) {
                                OutputType.STDOUT -> TerminalStdout
                                OutputType.STDERR -> TerminalStderr
                                OutputType.SYSTEM -> TerminalSystem
                                OutputType.STDIN -> TerminalPrompt
                            }

                            Text(
                                text = line.text,
                                color = color,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }

        // Quick Input Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(TerminalSurface)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "STDIN:",
                color = Color.Gray,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )

            SuggestionChip(
                onClick = { onSendStdin("\n", false) },
                label = { Text("Enter (\\n)", fontSize = 11.sp) },
                modifier = Modifier.testTag("send_enter_chip")
            )

            SuggestionChip(
                onClick = { onSendStdin("y\n", false) },
                label = { Text("Send 'y'", fontSize = 11.sp) },
                modifier = Modifier.testTag("send_y_chip")
            )

            SuggestionChip(
                onClick = { onSendStdin("n\n", false) },
                label = { Text("Send 'n'", fontSize = 11.sp) },
                modifier = Modifier.testTag("send_n_chip")
            )

            SuggestionChip(
                onClick = onCloseStdin,
                label = { Text("EOF (Ctrl+D)", fontSize = 11.sp) },
                modifier = Modifier.testTag("send_eof_chip")
            )
        }

        // STDIN Input Field Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(TerminalSurface)
                .border(width = 1.dp, color = TerminalBorder)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$ ",
                fontFamily = FontFamily.Monospace,
                color = TerminalStdout,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )

            OutlinedTextField(
                value = stdinInput,
                onValueChange = { stdinInput = it },
                placeholder = {
                    Text(
                        "Input to running process (stdin)...",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (stdinInput.isNotEmpty()) {
                            onSendStdin(stdinInput, true)
                            stdinInput = ""
                        }
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.LightGray,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = TerminalBorder,
                    cursorColor = TerminalStdout
                ),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("stdin_text_field")
            )

            Spacer(modifier = Modifier.width(6.dp))

            FilledTonalIconButton(
                onClick = {
                    if (stdinInput.isNotEmpty()) {
                        onSendStdin(stdinInput, true)
                        stdinInput = ""
                    }
                },
                modifier = Modifier
                    .size(40.dp)
                    .testTag("send_stdin_button")
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send stdin",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
