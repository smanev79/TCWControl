package com.tcwcontrol.app.ui.devices

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.pluralStringResource
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tcwcontrol.app.R
import com.tcwcontrol.app.ui.WhileVisible
import com.tcwcontrol.app.ui.appViewModel
import com.tcwcontrol.app.ui.theme.AlarmRed
import com.tcwcontrol.app.ui.theme.OfflineGrey
import com.tcwcontrol.app.ui.theme.OnlineGreen
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val QR_SIZE_PX = 800

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceListScreen(
    onOpen: (String) -> Unit,
    onEdit: (String) -> Unit,
    onAdd: () -> Unit,
    onAlarmLog: () -> Unit,
    onSettings: () -> Unit,
) {
    val vm = appViewModel { DeviceListViewModel(it) }
    val rows by vm.rows.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }
    var exportDialog by remember { mutableStateOf(false) }
    var includePasswords by remember { mutableStateOf(false) }
    val query by vm.query.collectAsStateWithLifecycle()
    var qrCode by remember { mutableStateOf<Pair<Bitmap, Int>?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            scope.launch {
                val message = runCatching { vm.exportTo(context.contentResolver, uri, includePasswords) }.fold(
                    { context.getString(R.string.exported, it) },
                    { it.localizedMessage ?: it.javaClass.simpleName },
                )
                snackbar.showSnackbar(message)
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val message = runCatching { vm.importFrom(context.contentResolver, uri) }.fold(
                    { context.getString(R.string.imported, it.added, it.skipped) },
                    { context.getString(R.string.import_failed) },
                )
                snackbar.showSnackbar(message)
            }
        }
    }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val text = result.contents ?: return@rememberLauncherForActivityResult
        scope.launch {
            val message = runCatching { vm.importText(text) }.fold(
                { context.getString(R.string.imported, it.added, it.skipped) },
                { context.getString(R.string.import_failed) },
            )
            snackbar.showSnackbar(message)
        }
    }
    fun showQr() {
        scope.launch {
            val (text, count) = vm.qrText(includePasswords)
            val bitmap = runCatching {
                withContext(Dispatchers.Default) {
                    BarcodeEncoder().encodeBitmap(text, BarcodeFormat.QR_CODE, QR_SIZE_PX, QR_SIZE_PX)
                }
            }.getOrNull()
            if (bitmap == null) snackbar.showSnackbar(context.getString(R.string.qr_too_big)) else qrCode = bitmap to count
        }
    }

    WhileVisible { vm.pollForever() }

    val groupOrder by vm.groupOrder.collectAsStateWithLifecycle()
    var collapsed by rememberSaveable { mutableStateOf(listOf<String>()) }
    var arrangeGroups by remember { mutableStateOf(false) }
    // Order shown while dragging, before it is saved; dropped once the saved list matches it.
    var localOrder by remember { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(rows) {
        if (localOrder == rows.map { it.config.id }) localOrder = null
    }
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val byId = rows.associateBy { it.config.id }
        val moving = byId[from.key as? String] ?: return@rememberReorderableLazyListState
        val target = byId[to.key as? String] ?: return@rememberReorderableLazyListState
        // Devices move within their group; the group is changed in Edit.
        if (moving.config.group.trim() != target.config.group.trim()) return@rememberReorderableLazyListState
        val order = (localOrder ?: rows.map { it.config.id }).toMutableList()
        order.add(order.indexOf(target.config.id), order.removeAt(order.indexOf(moving.config.id)))
        localOrder = order
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.my_devices), fontWeight = FontWeight.SemiBold) },
                actions = {
                    TextButton(onClick = onAdd) { Text(stringResource(R.string.add)) }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, stringResource(R.string.settings))
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, stringResource(R.string.more_options))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.alarm_log)) },
                                onClick = {
                                    menuOpen = false
                                    onAlarmLog()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.arrange_groups)) },
                                enabled = rows.any { it.config.group.isNotBlank() },
                                onClick = {
                                    menuOpen = false
                                    arrangeGroups = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.export_devices)) },
                                enabled = rows.isNotEmpty(),
                                onClick = {
                                    menuOpen = false
                                    exportDialog = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.import_file)) },
                                onClick = {
                                    menuOpen = false
                                    importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.scan_qr)) },
                                onClick = {
                                    menuOpen = false
                                    scanLauncher.launch(
                                        ScanOptions()
                                            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                            .setPrompt(context.getString(R.string.scan_prompt))
                                            .setBeepEnabled(false)
                                            .setOrientationLocked(false),
                                    )
                                },
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (rows.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.no_devices), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            val ordered = localOrder?.let { order ->
                rows.sortedBy { row -> order.indexOf(row.config.id).let { if (it < 0) Int.MAX_VALUE else it } }
            } ?: rows
            val filtered = ordered.filter { it.matches(query) }
            val byGroup = filtered.groupBy { it.config.group.trim() }
            val groups = sortGroups(byGroup.keys, groupOrder).map { it to byGroup.getValue(it) }
            val showHeaders = groups.any { it.first.isNotEmpty() }
            // Dragging works on the full list only; with a search active the order would be ambiguous.
            val canReorder = query.isBlank()
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = vm::setQuery,
                        placeholder = { Text(stringResource(R.string.search)) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { vm.setQuery("") }) {
                                    Icon(Icons.Default.Clear, stringResource(R.string.clear))
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item { ConnectionSummary(rows) }
                if (filtered.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.no_matches),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
                groups.forEach { (group, groupRows) ->
                    val isCollapsed = showHeaders && group in collapsed
                    if (showHeaders) {
                        item(key = "group:$group") {
                            GroupHeader(
                                name = group,
                                rows = groupRows,
                                collapsed = isCollapsed,
                                onToggle = { collapsed = if (isCollapsed) collapsed - group else collapsed + group },
                            )
                        }
                    }
                    if (!isCollapsed) {
                        items(groupRows, key = { it.config.id }) { row ->
                            ReorderableItem(reorderState, key = row.config.id, enabled = canReorder) {
                                DeviceCard(
                                    row,
                                    onClick = { onOpen(row.config.id) },
                                    onEdit = { onEdit(row.config.id) },
                                    dragHandle = if (canReorder && groupRows.size > 1) {
                                        {
                                            Icon(
                                                Icons.Default.Menu,
                                                contentDescription = stringResource(R.string.drag_to_reorder),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier
                                                    .draggableHandle(onDragStopped = { localOrder?.let(vm::saveOrder) })
                                                    .padding(end = 12.dp),
                                            )
                                        }
                                    } else {
                                        null
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (exportDialog) {
        AlertDialog(
            onDismissRequest = { exportDialog = false },
            title = { Text(stringResource(R.string.export_devices)) },
            text = {
                Column {
                    Row(
                        Modifier.fillMaxWidth().toggleable(value = includePasswords, onValueChange = { includePasswords = it }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = includePasswords, onCheckedChange = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.include_passwords))
                    }
                    if (includePasswords) {
                        Text(
                            stringResource(R.string.include_passwords_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = AlarmRed,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        exportDialog = false
                        showQr()
                    }) { Text(stringResource(R.string.show_qr)) }
                    TextButton(onClick = {
                        exportDialog = false
                        exportLauncher.launch("tcw-devices.json")
                    }) { Text(stringResource(R.string.save_file)) }
                }
            },
            dismissButton = { TextButton(onClick = { exportDialog = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (arrangeGroups) {
        val names = sortGroups(rows.map { it.config.group.trim() }.filter { it.isNotEmpty() }, groupOrder)
        GroupsDialog(
            groups = names,
            onReorder = vm::setGroupOrder,
            onRename = vm::renameGroup,
            onDismiss = { arrangeGroups = false },
        )
    }

    qrCode?.let { (bitmap, count) ->
        AlertDialog(
            onDismissRequest = { qrCode = null },
            title = { Text(stringResource(R.string.qr_title, count)) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        bitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.show_qr),
                        modifier = Modifier.size(280.dp),
                    )
                    Text(
                        stringResource(R.string.qr_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            },
            confirmButton = { TextButton(onClick = { qrCode = null }) { Text(stringResource(R.string.done)) } },
        )
    }
}

@Composable
private fun DeviceCard(
    row: DeviceRow,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    dragHandle: (@Composable () -> Unit)? = null,
) {
    val config = row.config
    val model = row.model
    val title = when {
        config.isDemo -> stringResource(R.string.demo_unit)
        model.isNotEmpty() -> model
        else -> stringResource(R.string.unknown_device)
    }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            dragHandle?.invoke()
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                val subtitle = config.description.ifEmpty { "${config.host}:${config.port}" }
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ConnectionBadge(row.connection)
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = onEdit,
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
            ) { Text(stringResource(R.string.edit)) }
        }
    }
}

/** Dot plus a word, so online and offline read at a glance without relying on colour alone. */
@Composable
private fun ConnectionBadge(connection: Connection) {
    val (color, label) = when (connection) {
        Connection.Unknown -> MaterialTheme.colorScheme.onSurfaceVariant to stringResource(R.string.connecting)
        is Connection.Online -> if (connection.status.hasAlarm) {
            AlarmRed to stringResource(R.string.alarm)
        } else {
            OnlineGreen to stringResource(R.string.online)
        }
        is Connection.Offline -> OfflineGrey to stringResource(R.string.offline)
    }
    Row(
        Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (connection == Connection.Unknown) {
            CircularProgressIndicator(Modifier.size(10.dp), strokeWidth = 1.5.dp, color = color)
        } else {
            Box(Modifier.size(10.dp).background(color, CircleShape))
        }
        Text(label, color = color, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ConnectionSummary(rows: List<DeviceRow>) {
    val online = rows.count { it.connection is Connection.Online }
    val offline = rows.count { it.connection is Connection.Offline }
    val alarm = rows.count { (it.connection as? Connection.Online)?.status?.hasAlarm == true }
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.summary_online, online), color = OnlineGreen, fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.summary_offline, offline), color = OfflineGrey, fontWeight = FontWeight.SemiBold)
        if (alarm > 0) Text(stringResource(R.string.summary_alarm, alarm), color = AlarmRed, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun GroupHeader(name: String, rows: List<DeviceRow>, collapsed: Boolean, onToggle: () -> Unit) {
    val alarms = rows.count { (it.connection as? Connection.Online)?.status?.hasAlarm == true }
    val offline = rows.count { it.connection is Connection.Offline }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(start = 4.dp, top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (collapsed) Icons.AutoMirrored.Filled.KeyboardArrowRight else Icons.Default.KeyboardArrowDown,
            contentDescription = stringResource(if (collapsed) R.string.expand else R.string.collapse),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            name.ifEmpty { stringResource(R.string.ungrouped) }.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (alarms > 0) {
            Text(stringResource(R.string.summary_alarm, alarms), color = AlarmRed, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.width(8.dp))
        }
        if (offline > 0) {
            Text(stringResource(R.string.summary_offline, offline), color = OfflineGrey, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            pluralStringResource(R.plurals.device_count, rows.size, rows.size),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Moves groups up and down and renames them. */
@Composable
private fun GroupsDialog(
    groups: List<String>,
    onReorder: (List<String>) -> Unit,
    onRename: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var renaming by remember { mutableStateOf<String?>(null) }
    fun move(index: Int, by: Int) {
        onReorder(groups.toMutableList().apply { add(index + by, removeAt(index)) })
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.arrange_groups)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                groups.forEachIndexed { index, group ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(group, Modifier.weight(1f))
                        IconButton(onClick = { renaming = group }) {
                            Icon(Icons.Default.Edit, stringResource(R.string.rename_group))
                        }
                        IconButton(onClick = { move(index, -1) }, enabled = index > 0) {
                            Icon(Icons.Default.KeyboardArrowUp, stringResource(R.string.move_up))
                        }
                        IconButton(onClick = { move(index, 1) }, enabled = index < groups.lastIndex) {
                            Icon(Icons.Default.KeyboardArrowDown, stringResource(R.string.move_down))
                        }
                    }
                }
                Text(
                    stringResource(R.string.arrange_groups_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
    )

    renaming?.let { old ->
        var name by remember(old) { mutableStateOf(old) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text(stringResource(R.string.rename_group)) },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true)
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        if (name.trim() != old) onRename(old, name.trim())
                        renaming = null
                    },
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
