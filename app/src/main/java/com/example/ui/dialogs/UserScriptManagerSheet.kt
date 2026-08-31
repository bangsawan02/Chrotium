package com.example.ui.dialogs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.UserScript
import com.example.data.presets.PreinstalledScripts
import com.example.engine.ScriptLogEntry
import com.example.ui.theme.AmoledBlack
import com.example.ui.theme.BorderHighlight
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElectricCyanGlow
import com.example.ui.theme.EnergyGreen
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.ui.theme.WarnYellow

enum class ScriptFilterType(val label: String) {
    ALL("Semua"),
    ACTIVE("Aktif"),
    INACTIVE("Nonaktif"),
    MATCHES_PAGE("Cocok Halaman Ini")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserScriptManagerSheet(
    scripts: List<UserScript>,
    logs: List<ScriptLogEntry>,
    currentUrl: String,
    onDismiss: () -> Unit,
    onToggleScript: (Long, Boolean) -> Unit,
    onToggleAllScripts: ((Boolean) -> Unit)? = null,
    onSaveScript: (UserScript) -> Unit,
    onDuplicateScript: ((UserScript) -> Unit)? = null,
    onDeleteScript: (Long) -> Unit,
    onResetDefaultPresets: (() -> Unit)? = null,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var editingScript by remember { mutableStateOf<UserScript?>(null) }
    var previewScript by remember { mutableStateOf<UserScript?>(null) }
    var showResetDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val tabs = listOf("Skrip Terpasang", "Preset Skrip", "Editor Skrip", "Log Eksekusi")

    // Dialog Pratinjau Kode Skrip
    if (previewScript != null) {
        val target = previewScript!!
        AlertDialog(
            onDismissRequest = { previewScript = null },
            containerColor = DarkSurfaceElevated,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = target.name,
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Versi ${target.version} • ${target.code.lines().size} baris kode",
                            color = TextTertiary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "run-at: ${target.runAt}",
                            color = ElectricCyan,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = DarkBackground,
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = target.code,
                            color = TextPrimary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Userscript Code", target.code)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Kode berhasil disalin ke clipboard", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        tint = AmoledBlack,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Salin Kode", color = AmoledBlack, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { previewScript = null }) {
                    Text("Tutup", color = TextSecondary, fontSize = 12.sp)
                }
            }
        )
    }

    // Dialog Konfirmasi Pulihkan Skrip Preset
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            containerColor = DarkSurface,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = WarnYellow,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Pulihkan Skrip Bawaan?",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Text(
                    text = "Tindakan ini akan mengatur ulang daftar skrip ke preset bawaan peramban (Penghemat Baterai, Pembuka Salin, dll). Skrip kustom Anda mungkin terhapus.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onResetDefaultPresets?.invoke()
                        showResetDialog = false
                        Toast.makeText(context, "Skrip preset berhasil dipulihkan", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WarnYellow),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Pulihkan", color = AmoledBlack, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Batal", color = TextSecondary)
                }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AmoledBlack,
        dragHandle = null,
        modifier = modifier.fillMaxHeight(0.94f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AmoledBlack)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(ElectricCyan.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = "Mesin Userscript Tampermonkey",
                            tint = ElectricCyan,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Pengelola Userscript",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Injeksi JavaScript & manipulasi DOM kustom",
                            color = TextTertiary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("close_script_manager_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Tutup",
                        tint = TextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Tabs Selector
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = DarkSurface,
                contentColor = ElectricCyan,
                edgePadding = 12.dp,
                indicator = { tabPositions ->
                    if (selectedTabIndex < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                            color = ElectricCyan
                        )
                    }
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = {
                            selectedTabIndex = index
                            if (index != 2) {
                                editingScript = null
                            }
                        },
                        text = {
                            Text(
                                text = title,
                                fontSize = 12.sp,
                                fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Medium,
                                color = if (selectedTabIndex == index) ElectricCyan else TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                }
            }

            // Tab Content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                when (selectedTabIndex) {
                    0 -> InstalledScriptsTab(
                        scripts = scripts,
                        currentUrl = currentUrl,
                        onToggle = onToggleScript,
                        onToggleAll = onToggleAllScripts,
                        onEdit = { script ->
                            editingScript = script
                            selectedTabIndex = 2
                        },
                        onDuplicate = { script ->
                            onDuplicateScript?.invoke(script)
                            Toast.makeText(context, "Skrip diduplikasi", Toast.LENGTH_SHORT).show()
                        },
                        onPreview = { script ->
                            previewScript = script
                        },
                        onDelete = onDeleteScript,
                        onResetPresets = { showResetDialog = true },
                        onCreateNew = {
                            editingScript = null
                            selectedTabIndex = 2
                        }
                    )
                    1 -> PresetStoreTab(
                        installedScripts = scripts,
                        onInstall = { preset ->
                            val existing = scripts.find { it.name.trim().equals(preset.name.trim(), ignoreCase = true) }
                            val scriptToInstall = if (existing != null) {
                                preset.copy(id = existing.id, isEnabled = true)
                            } else {
                                preset.copy(id = 0L, isEnabled = true)
                            }
                            onSaveScript(scriptToInstall)
                            Toast.makeText(context, "Skrip \"${preset.name}\" berhasil dipasang", Toast.LENGTH_SHORT).show()
                        }
                    )
                    2 -> ScriptEditorTab(
                        initialScript = editingScript,
                        onSave = { savedScript ->
                            onSaveScript(savedScript)
                            editingScript = null
                            selectedTabIndex = 0
                            Toast.makeText(context, "Skrip \"${savedScript.name}\" berhasil disimpan", Toast.LENGTH_SHORT).show()
                        }
                    )
                    3 -> LiveLogsTab(
                        logs = logs,
                        onClear = onClearLogs
                    )
                }
            }
        }
    }
}

@Composable
fun InstalledScriptsTab(
    scripts: List<UserScript>,
    currentUrl: String,
    onToggle: (Long, Boolean) -> Unit,
    onToggleAll: ((Boolean) -> Unit)?,
    onEdit: (UserScript) -> Unit,
    onDuplicate: (UserScript) -> Unit,
    onPreview: (UserScript) -> Unit,
    onDelete: (Long) -> Unit,
    onResetPresets: () -> Unit,
    onCreateNew: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(ScriptFilterType.ALL) }
    var scriptToDelete by remember { mutableStateOf<UserScript?>(null) }

    // Dialog Konfirmasi Hapus
    if (scriptToDelete != null) {
        val target = scriptToDelete!!
        AlertDialog(
            onDismissRequest = { scriptToDelete = null },
            containerColor = DarkSurface,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = ErrorRed,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Hapus Userscript?",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            text = {
                Text(
                    text = "Apakah Anda yakin ingin menghapus skrip \"${target.name}\"? Tindakan ini tidak dapat dibatalkan.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete(target.id)
                        scriptToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Hapus", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { scriptToDelete = null }) {
                    Text("Batal", color = TextSecondary)
                }
            }
        )
    }

    // Filter and search logic
    val filteredScripts = remember(scripts, searchQuery, selectedFilter, currentUrl) {
        scripts.filter { script ->
            val matchesQuery = if (searchQuery.isBlank()) {
                true
            } else {
                script.name.contains(searchQuery, ignoreCase = true) ||
                        script.description.contains(searchQuery, ignoreCase = true) ||
                        script.matchPatterns.contains(searchQuery, ignoreCase = true) ||
                        script.author.contains(searchQuery, ignoreCase = true)
            }

            val matchesFilter = when (selectedFilter) {
                ScriptFilterType.ALL -> true
                ScriptFilterType.ACTIVE -> script.isEnabled
                ScriptFilterType.INACTIVE -> !script.isEnabled
                ScriptFilterType.MATCHES_PAGE -> script.matchesUrl(currentUrl)
            }

            matchesQuery && matchesFilter
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = {
                Text(
                    text = "Cari skrip, URL pattern, atau pembuat...",
                    color = TextTertiary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Cari",
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { searchQuery = "" },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Hapus Pencarian",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            },
            singleLine = true,
            maxLines = 1,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = DarkSurface,
                unfocusedContainerColor = DarkSurface,
                focusedBorderColor = ElectricCyan,
                unfocusedBorderColor = BorderSubtle,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("search_scripts_input")
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Filter chips row
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(ScriptFilterType.values()) { filter ->
                val count = when (filter) {
                    ScriptFilterType.ALL -> scripts.size
                    ScriptFilterType.ACTIVE -> scripts.count { it.isEnabled }
                    ScriptFilterType.INACTIVE -> scripts.count { !it.isEnabled }
                    ScriptFilterType.MATCHES_PAGE -> scripts.count { it.matchesUrl(currentUrl) }
                }

                val isSelected = selectedFilter == filter

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) ElectricCyan.copy(alpha = 0.2f) else DarkSurface,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isSelected) ElectricCyan else BorderSubtle
                    ),
                    modifier = Modifier.clickable { selectedFilter = filter }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = filter.label,
                            color = if (isSelected) ElectricCyan else TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Surface(
                            shape = CircleShape,
                            color = if (isSelected) ElectricCyan else DarkSurfaceVariant
                        ) {
                            Text(
                                text = "$count",
                                color = if (isSelected) AmoledBlack else TextTertiary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Batch Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (onToggleAll != null) {
                    OutlinedButton(
                        onClick = { onToggleAll(true) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = DarkSurface,
                            contentColor = EnergyGreen
                        ),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Aktifkan Semua", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = { onToggleAll(false) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = DarkSurface,
                            contentColor = TextSecondary
                        ),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Matikan Semua", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            IconButton(
                onClick = onResetPresets,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("reset_presets_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Restore,
                    contentDescription = "Pulihkan Skrip Bawaan",
                    tint = TextTertiary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (filteredScripts.isEmpty()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Code,
                    contentDescription = null,
                    tint = TextTertiary,
                    modifier = Modifier.size(44.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = if (scripts.isEmpty()) "Belum Ada Userscript Terpasang" else "Tidak Ada Skrip yang Sesuai Filter",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (scripts.isEmpty())
                        "Gunakan tab 'Preset Skrip' untuk memasang skrip siap pakai atau buat skrip kustom."
                    else
                        "Coba bersihkan kotak pencarian atau ubah filter di atas.",
                    color = TextTertiary,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                if (scripts.isEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = onCreateNew,
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = AmoledBlack,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Buat Skrip Baru", color = AmoledBlack, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("installed_scripts_list"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredScripts, key = { it.id }) { script ->
                    val matchesCurrent = script.matchesUrl(currentUrl)

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (matchesCurrent && script.isEnabled) ElectricCyan.copy(alpha = 0.65f) else BorderSubtle
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("script_item_${script.id}")
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            // Header Row: Status, Name, and Toggle Switch
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = script.name,
                                            color = if (script.isEnabled) TextPrimary else TextTertiary,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (matchesCurrent && script.isEnabled) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = ElectricCyan.copy(alpha = 0.15f),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.3f))
                                            ) {
                                                Text(
                                                    text = "COCOK HALAMAN",
                                                    color = ElectricCyan,
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Black,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(2.dp))

                                    Text(
                                        text = "v${script.version} • Oleh ${script.author} • run-at: ${script.runAt}",
                                        color = TextTertiary,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Switch(
                                    checked = script.isEnabled,
                                    onCheckedChange = { onToggle(script.id, it) },
                                    modifier = Modifier.testTag("toggle_script_${script.id}"),
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = AmoledBlack,
                                        checkedTrackColor = ElectricCyan,
                                        uncheckedThumbColor = TextSecondary,
                                        uncheckedTrackColor = DarkSurfaceVariant
                                    )
                                )
                            }

                            if (script.description.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = script.description,
                                    color = if (script.isEnabled) TextSecondary else TextTertiary,
                                    fontSize = 12.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Pattern Pill & Stats
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = DarkSurfaceVariant,
                                    modifier = Modifier.weight(1f, fill = false)
                                ) {
                                    Text(
                                        text = "Target: ${script.matchPatterns}",
                                        color = TextTertiary,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }

                                if (script.executionCount > 0) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${script.executionCount}x dieksekusi",
                                        color = EnergyGreen,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Action Toolbar
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.End
                            ) {
                                IconButton(
                                    onClick = { onPreview(script) },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .testTag("preview_script_${script.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Visibility,
                                        contentDescription = "Pratinjau Kode",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { onDuplicate(script) },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .testTag("duplicate_script_${script.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Duplikasi Skrip",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { onEdit(script) },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .testTag("edit_script_${script.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit Skrip",
                                        tint = ElectricCyan,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { scriptToDelete = script },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .testTag("delete_script_${script.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Hapus Skrip",
                                        tint = ErrorRed.copy(alpha = 0.85f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PresetStoreTab(
    installedScripts: List<UserScript>,
    onInstall: (UserScript) -> Unit
) {
    val presets = PreinstalledScripts.getDefaultScripts()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("preset_scripts_list"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Preset Userscript Siap Pakai",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Pilih skrip optimal untuk efisiensi baterai, tampilan gelap sejati, dan kemudahan jelajah.",
                    color = TextTertiary,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        items(presets) { preset ->
            val isInstalled = installedScripts.any { it.name == preset.name }

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = preset.name,
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = preset.description,
                                color = TextSecondary,
                                fontSize = 12.sp,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = { onInstall(preset) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isInstalled) DarkSurfaceVariant else EnergyGreen,
                                contentColor = if (isInstalled) TextSecondary else AmoledBlack
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .testTag("install_preset_${preset.name}")
                                .height(36.dp)
                        ) {
                            Text(
                                text = if (isInstalled) "Pasang Ulang" else "Pasang",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = DarkSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Target: ${preset.matchPatterns} • run-at: ${preset.runAt}",
                            color = TextTertiary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ScriptEditorTab(
    initialScript: UserScript?,
    onSave: (UserScript) -> Unit
) {
    var codeText by remember(initialScript) {
        mutableStateOf(
            initialScript?.code ?: """
// ==UserScript==
// @name         Skrip Kustom Baru
// @namespace    https://crotium.app
// @version      1.0
// @description  Userscript JavaScript kustom untuk crotium Browser
// @author       Pengguna
// @match        *://*/*
// @run-at       document-idle
// @grant        GM_addStyle
// @grant        GM_log
// ==/UserScript==

(function() {
    'use strict';
    console.log('[crotium] Skrip aktif di:', window.location.href);
    
    // Tulis kode JavaScript / manipulasi DOM Anda di bawah:
    
})();
            """.trimIndent()
        )
    }

    val metadata = remember(codeText) {
        UserScript.parseMetadata(codeText)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Quick Template Bar
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = DarkSurfaceVariant,
                    modifier = Modifier.clickable {
                        codeText = """
// ==UserScript==
// @name         Injeksi CSS Mode Gelap
// @namespace    https://crotium.app
// @version      1.0
// @description  Mengubah latar belakang menjadi hitam pekat
// @match        *://*/*
// @run-at       document-start
// @grant        GM_addStyle
// ==/UserScript==

(function() {
    'use strict';
    const css = `
        body { background-color: #000000 !important; color: #E2E8F0 !important; }
        a { color: #38BDF8 !important; }
    `;
    if (typeof GM_addStyle === 'function') {
        GM_addStyle(css);
    } else {
        const s = document.createElement('style');
        s.textContent = css;
        (document.head || document.documentElement).appendChild(s);
    }
})();
                        """.trimIndent()
                    }
                ) {
                    Text(
                        text = "+ Template CSS",
                        color = ElectricCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                }
            }

            item {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = DarkSurfaceVariant,
                    modifier = Modifier.clickable {
                        codeText = """
// ==UserScript==
// @name         Bypass Klik Kanan & Seleksi Teks
// @namespace    https://crotium.app
// @version      1.0
// @description  Mengaktifkan kembali seleksi teks yang diblokir
// @match        *://*/*
// @run-at       document-idle
// @grant        none
// ==/UserScript==

(function() {
    'use strict';
    ['copy', 'cut', 'paste', 'selectstart', 'contextmenu'].forEach(ev => {
        document.addEventListener(ev, e => e.stopPropagation(), true);
    });
})();
                        """.trimIndent()
                    }
                ) {
                    Text(
                        text = "+ Template Event Bypass",
                        color = ElectricCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                }
            }

            item {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = DarkSurfaceVariant,
                    modifier = Modifier.clickable {
                        codeText = """
// ==UserScript==
// @name         Penghemat CPU & Timer Loop
// @namespace    https://crotium.app
// @version      1.0
// @description  Membatasi frekuensi setInterval untuk hemat baterai
// @match        *://*/*
// @run-at       document-start
// @grant        none
// ==/UserScript==

(function() {
    'use strict';
    const rawInterval = window.setInterval;
    window.setInterval = function(fn, delay, ...args) {
        return rawInterval(fn, Math.max(delay || 0, 100), ...args);
    };
})();
                        """.trimIndent()
                    }
                ) {
                    Text(
                        text = "+ Template Penghemat Timer",
                        color = ElectricCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Metadata Summary Card
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = DarkSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = metadata.name.ifBlank { "Skrip Tanpa Judul" },
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Target: ${metadata.matchPatterns} • ${metadata.runAt}",
                        color = ElectricCyan,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = {
                        val scriptToSave = UserScript(
                            id = initialScript?.id ?: 0L,
                            name = metadata.name.ifBlank { "Skrip Kustom" },
                            description = metadata.description,
                            author = metadata.author,
                            version = metadata.version,
                            matchPatterns = metadata.matchPatterns,
                            runAt = metadata.runAt,
                            code = codeText,
                            isEnabled = true
                        )
                        onSave(scriptToSave)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EnergyGreen),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("save_script_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = AmoledBlack,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (initialScript != null) "Perbarui" else "Simpan",
                        color = AmoledBlack,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Code Editor Box
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = DarkBackground,
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            BasicTextField(
                value = codeText,
                onValueChange = { codeText = it },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp)
                    .testTag("userscript_code_editor"),
                textStyle = TextStyle(
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 18.sp
                ),
                cursorBrush = SolidColor(EnergyGreen)
            )
        }
    }
}

@Composable
fun LiveLogsTab(
    logs: List<ScriptLogEntry>,
    onClear: () -> Unit
) {
    var filterLevel by remember { mutableStateOf("ALL") }

    val filteredLogs = remember(logs, filterLevel) {
        if (filterLevel == "ALL") logs else logs.filter { it.level.equals(filterLevel, ignoreCase = true) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${filteredLogs.size} Log Eksekusi",
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Button(
                onClick = onClear,
                colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.testTag("clear_logs_button")
            ) {
                Text(
                    text = "Bersihkan Log",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (filteredLogs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Belum ada log eksekusi. Output dari GM_log & konsol skrip akan tampil di sini.",
                    color = TextTertiary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("script_logs_list"),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(filteredLogs) { log ->
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = DarkSurface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = log.timestamp,
                                color = TextTertiary,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            val levelColor = when (log.level) {
                                "ERROR" -> ErrorRed
                                "SUCCESS" -> EnergyGreen
                                "NOTIFY" -> WarnYellow
                                else -> ElectricCyan
                            }
                            Text(
                                text = "[${log.level}]",
                                color = levelColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${log.scriptName}: ${log.message}",
                                color = TextPrimary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}
