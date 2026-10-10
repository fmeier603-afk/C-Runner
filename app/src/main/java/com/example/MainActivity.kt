package com.example

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.ExecutionMode
import com.example.ui.MainViewModel
import com.example.ui.components.EditPluginDialog
import com.example.ui.components.ElfDetailDialog
import com.example.ui.screens.FileBrowserScreen
import com.example.ui.screens.GuideScreen
import com.example.ui.screens.PluginsScreen
import com.example.ui.screens.TerminalScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                val viewModel: MainViewModel = viewModel()

                val currentTab by viewModel.currentTab.collectAsState()
                val plugins by viewModel.plugins.collectAsState()
                val history by viewModel.history.collectAsState()
                val isProcessRunning by viewModel.isProcessRunning.collectAsState()
                val currentRunningPlugin by viewModel.currentRunningPlugin.collectAsState()
                val terminalLines by viewModel.terminalLines.collectAsState()

                val currentDirectory by viewModel.currentDirectory.collectAsState()
                val currentDirectoryFiles by viewModel.currentDirectoryFiles.collectAsState()
                val isLoadingFiles by viewModel.isLoadingFiles.collectAsState()

                val selectedElfDialog by viewModel.selectedElfDialog.collectAsState()
                val editingPlugin by viewModel.editingPlugin.collectAsState()

                // File picker launcher using Storage Access Framework (SAF)
                val filePickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri ->
                    uri?.let {
                        viewModel.importFromUri(it)
                    }
                }

                // Handle system back navigation
                BackHandler(enabled = currentTab != 0) {
                    if (currentTab == 1 && currentDirectory.parentFile != null) {
                        viewModel.navigateUp()
                    } else {
                        viewModel.setTab(0)
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = {
                                Text(
                                    text = "Binary Runner",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            actions = {
                                val primaryAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "ARM"
                                SuggestionChip(
                                    onClick = { viewModel.setTab(3) },
                                    label = { Text(primaryAbi, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .testTag("appbar_abi_chip")
                                )
                            },
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                    },
                    bottomBar = {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.testTag("bottom_nav_bar")
                        ) {
                            NavigationBarItem(
                                selected = currentTab == 0,
                                onClick = { viewModel.setTab(0) },
                                icon = {
                                    Icon(Icons.Default.Extension, contentDescription = "Plugins")
                                },
                                label = { Text("Plugins") },
                                modifier = Modifier.testTag("nav_tab_plugins")
                            )

                            NavigationBarItem(
                                selected = currentTab == 1,
                                onClick = { viewModel.setTab(1) },
                                icon = {
                                    Icon(Icons.Default.FolderOpen, contentDescription = "File Browser")
                                },
                                label = { Text("Browser") },
                                modifier = Modifier.testTag("nav_tab_browser")
                            )

                            NavigationBarItem(
                                selected = currentTab == 2,
                                onClick = { viewModel.setTab(2) },
                                icon = {
                                    BadgedBox(
                                        badge = {
                                            if (isProcessRunning) {
                                                Badge { Text("●") }
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Default.Terminal, contentDescription = "Terminal")
                                    }
                                },
                                label = { Text("Terminal") },
                                modifier = Modifier.testTag("nav_tab_terminal")
                            )

                            NavigationBarItem(
                                selected = currentTab == 3,
                                onClick = { viewModel.setTab(3) },
                                icon = {
                                    Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = "Guide & Logs")
                                },
                                label = { Text("Guide") },
                                modifier = Modifier.testTag("nav_tab_guide")
                            )
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (currentTab) {
                            0 -> PluginsScreen(
                                plugins = plugins,
                                isProcessRunning = isProcessRunning,
                                onRunPlugin = { plugin ->
                                    viewModel.runPlugin(plugin)
                                },
                                onEditPlugin = { plugin ->
                                    viewModel.openEditPluginDialog(plugin)
                                },
                                onInspectElf = { file ->
                                    viewModel.openElfDialog(file)
                                },
                                onDeletePlugin = { id ->
                                    viewModel.deletePlugin(id)
                                },
                                onNavigateToBrowser = {
                                    viewModel.setTab(1)
                                },
                                onPickSystemFile = {
                                    filePickerLauncher.launch(arrayOf("*/*"))
                                }
                            )

                            1 -> FileBrowserScreen(
                                currentDirectory = currentDirectory,
                                files = currentDirectoryFiles,
                                isLoading = isLoadingFiles,
                                quickDirectories = viewModel.getQuickDirectories(),
                                onNavigateTo = { dir ->
                                    viewModel.navigateToDirectory(dir)
                                },
                                onNavigateUp = {
                                    viewModel.navigateUp()
                                },
                                onRefresh = {
                                    viewModel.refreshCurrentDirectory()
                                },
                                onSelectFile = { file ->
                                    viewModel.openElfDialog(file)
                                },
                                onImportAsPlugin = { file ->
                                    viewModel.importFromFile(file)
                                },
                                onPickSystemFile = {
                                    filePickerLauncher.launch(arrayOf("*/*"))
                                }
                            )

                            2 -> TerminalScreen(
                                lines = terminalLines,
                                isRunning = isProcessRunning,
                                currentPlugin = currentRunningPlugin,
                                availablePlugins = plugins,
                                onRunPlugin = { plugin, args, mode ->
                                    viewModel.runPlugin(plugin, args, mode)
                                },
                                onSendStdin = { text, appendNewline ->
                                    viewModel.sendStdin(text, appendNewline)
                                },
                                onCloseStdin = {
                                    viewModel.closeStdin()
                                },
                                onKillProcess = {
                                    viewModel.killRunningProcess()
                                },
                                onClearTerminal = {
                                    viewModel.clearTerminal()
                                }
                            )

                            3 -> GuideScreen(
                                history = history,
                                plugins = plugins,
                                onRerun = { plugin, args ->
                                    viewModel.runPlugin(plugin, args)
                                },
                                onClearHistory = {
                                    viewModel.clearHistory()
                                }
                            )
                        }

                        // Dialogs
                        selectedElfDialog?.let { (file, elf) ->
                            ElfDetailDialog(
                                file = file,
                                elfInfo = elf,
                                onDismiss = { viewModel.closeElfDialog() },
                                onImportAsPlugin = { f ->
                                    viewModel.importFromFile(f)
                                    viewModel.closeElfDialog()
                                },
                                onQuickRun = { f ->
                                    val tempPlugin = viewModel.plugins.value.find { it.localExecutablePath == f.absolutePath }
                                    if (tempPlugin != null) {
                                        viewModel.runPlugin(tempPlugin)
                                    } else {
                                        viewModel.importFromFile(f)
                                    }
                                    viewModel.closeElfDialog()
                                }
                            )
                        }

                        editingPlugin?.let { plugin ->
                            EditPluginDialog(
                                plugin = plugin,
                                onDismiss = { viewModel.closeEditPluginDialog() },
                                onSave = { updated ->
                                    viewModel.updatePlugin(updated)
                                    viewModel.closeEditPluginDialog()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
