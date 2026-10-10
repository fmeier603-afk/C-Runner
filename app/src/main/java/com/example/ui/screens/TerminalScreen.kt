package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ExecutionMode
import com.example.data.model.PluginItem
import com.example.data.model.TerminalLine
import com.example.ui.components.TerminalView
import com.example.ui.theme.TerminalBorder
import com.example.ui.theme.TerminalSurface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    lines: List<TerminalLine>,
    isRunning: Boolean,
    currentPlugin: PluginItem?,
    availablePlugins: List<PluginItem>,
    onRunPlugin: (PluginItem, String?, ExecutionMode?) -> Unit,
    onSendStdin: (String, Boolean) -> Unit,
    onCloseStdin: () -> Unit,
    onKillProcess: () -> Unit,
    onClearTerminal: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedPlugin by remember(currentPlugin, availablePlugins) {
        mutableStateOf(currentPlugin ?: availablePlugins.firstOrNull())
    }
    var customArgs by remember(selectedPlugin) {
        mutableStateOf(selectedPlugin?.defaultArgs ?: "")
    }
    var selectedMode by remember(selectedPlugin) {
        mutableStateOf(selectedPlugin?.executionMode ?: ExecutionMode.AUTO)
    }

    var isPluginMenuExpanded by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        // Quick Launch / Arguments Control Bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(TerminalSurface)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Plugin Selector Dropdown
                ExposedDropdownMenuBox(
                    expanded = isPluginMenuExpanded,
                    onExpandedChange = { if (!isRunning) isPluginMenuExpanded = !isPluginMenuExpanded },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = selectedPlugin?.name ?: "Select Plugin",
                        onValueChange = {},
                        readOnly = true,
                        enabled = !isRunning,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isPluginMenuExpanded) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = TerminalBorder
                        ),
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                            .testTag("terminal_plugin_selector")
                    )

                    ExposedDropdownMenu(
                        expanded = isPluginMenuExpanded,
                        onDismissRequest = { isPluginMenuExpanded = false }
                    ) {
                        availablePlugins.forEach { plugin ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(plugin.name, fontWeight = FontWeight.Bold)
                                        Text(
                                            "${plugin.binaryFileName} • ${plugin.machineType}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {
                                    selectedPlugin = plugin
                                    customArgs = plugin.defaultArgs
                                    selectedMode = plugin.executionMode
                                    isPluginMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                // Run / Kill Button
                if (isRunning) {
                    Button(
                        onClick = onKillProcess,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("terminal_kill_button")
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Stop")
                    }
                } else {
                    Button(
                        onClick = {
                            selectedPlugin?.let { plugin ->
                                onRunPlugin(plugin, customArgs, selectedMode)
                            }
                        },
                        enabled = selectedPlugin != null,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("terminal_run_button")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Run")
                    }
                }
            }

            // Args Input + Execution Mode chips row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = customArgs,
                    onValueChange = { customArgs = it },
                    placeholder = { Text("CLI Arguments (e.g. --help)", color = Color.Gray, fontSize = 11.sp) },
                    singleLine = true,
                    enabled = !isRunning,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.LightGray,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = TerminalBorder
                    ),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("terminal_args_input")
                )

                // Mode toggle
                SuggestionChip(
                    onClick = {
                        val modes = ExecutionMode.values()
                        val nextIndex = (modes.indexOf(selectedMode) + 1) % modes.size
                        selectedMode = modes[nextIndex]
                    },
                    label = { Text(selectedMode.name, fontSize = 11.sp, fontFamily = FontFamily.Monospace) },
                    modifier = Modifier.testTag("terminal_mode_toggle_chip")
                )
            }
        }

        // Terminal Console Screen
        TerminalView(
            lines = lines,
            isRunning = isRunning,
            currentPluginName = currentPlugin?.name ?: selectedPlugin?.name,
            onSendStdin = onSendStdin,
            onCloseStdin = onCloseStdin,
            onKillProcess = onKillProcess,
            onClearTerminal = onClearTerminal,
            modifier = Modifier.weight(1f)
        )
    }
}
