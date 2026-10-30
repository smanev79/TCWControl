package com.tcwcontrol.app.ui.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import com.tcwcontrol.app.R
import com.tcwcontrol.app.ui.appViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditDeviceScreen(onDone: () -> Unit, onDeleted: () -> Unit) {
    val vm = appViewModel { EditDeviceViewModel(createSavedStateHandle(), it) }
    val form = vm.form
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (vm.isNew) R.string.add_device else R.string.edit_device)) },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.demo_unit), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Switch(checked = form.isDemo, onCheckedChange = vm::setDemo)
            }

            SectionTitle(stringResource(R.string.unit_details))
            TextField(
                value = form.host,
                onValueChange = { v -> vm.update { it.copy(host = v) } },
                label = { Text(stringResource(R.string.host)) },
                placeholder = { Text("192.168.1.2") },
                isError = vm.showErrors && form.hostError,
                supportingText = if (vm.showErrors && form.hostError) {
                    { Text(stringResource(R.string.error_host)) }
                } else null,
                singleLine = true,
                enabled = !form.isDemo,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            TextField(
                value = form.port,
                onValueChange = { v -> vm.update { it.copy(port = v.filter(Char::isDigit).take(5)) } },
                label = { Text(stringResource(R.string.port)) },
                isError = vm.showErrors && form.portError,
                supportingText = if (vm.showErrors && form.portError) {
                    { Text(stringResource(R.string.error_port)) }
                } else null,
                singleLine = true,
                enabled = !form.isDemo,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            TextField(
                value = form.description,
                onValueChange = { v -> vm.update { it.copy(description = v) } },
                label = { Text(stringResource(R.string.description)) },
                placeholder = { Text("Server room") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            TextField(
                value = form.group,
                onValueChange = { v -> vm.update { it.copy(group = v) } },
                label = { Text(stringResource(R.string.group)) },
                placeholder = { Text(stringResource(R.string.group_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            val groups by vm.existingGroups.collectAsStateWithLifecycle()
            if (groups.isNotEmpty()) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    groups.forEach { group ->
                        FilterChip(
                            selected = form.group.trim() == group,
                            onClick = { vm.update { it.copy(group = group) } },
                            label = { Text(group) },
                        )
                    }
                }
            }

            SectionTitle(stringResource(R.string.credentials))
            TextField(
                value = form.username,
                onValueChange = { v -> vm.update { it.copy(username = v) } },
                label = { Text(stringResource(R.string.username)) },
                singleLine = true,
                enabled = !form.isDemo,
                modifier = Modifier.fillMaxWidth(),
            )
            TextField(
                value = form.password,
                onValueChange = { v -> vm.update { it.copy(password = v) } },
                label = { Text(stringResource(R.string.password)) },
                singleLine = true,
                enabled = !form.isDemo,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { vm.save(onDone) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
            ) {
                Text(
                    stringResource(if (vm.isNew) R.string.add else R.string.save),
                    fontWeight = FontWeight.Bold,
                )
            }
            if (!vm.isNew) {
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.delete)) }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.delete(onDeleted)
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}
