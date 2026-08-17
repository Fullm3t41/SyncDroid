package com.syncdroid.app.ui

import android.Manifest
import android.content.Intent
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.syncdroid.app.wifi.WifiConnectionState
import com.syncdroid.app.wifi.WifiNetworkRule
import com.syncdroid.app.wifi.WifiSyncPolicy
import com.syncdroid.app.wifi.WifiSyncPolicyStore
import com.syncdroid.app.wifi.hasWifiRuntimePermission
import com.syncdroid.app.wifi.rememberWifiConnectionState
import com.syncdroid.app.wifi.requiredWifiRuntimePermissions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiRulesScreen(
    onBack: () -> Unit,
    onRulesChanged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val store = remember(context) { WifiSyncPolicyStore(context) }
    var policy by remember { mutableStateOf(store.load()) }
    var permissionRefresh by remember { mutableIntStateOf(0) }
    val connection = rememberWifiConnectionState(permissionRefresh)
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var showLocationServicesDialog by rememberSaveable { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        permissionRefresh++
        if (result.values.any { !it }) message = "Wi-Fi name access was not granted. You can still add a network manually."
    }
    val backgroundPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        permissionRefresh++
        onRulesChanged()
    }
    val locationSettingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        permissionRefresh++
        onRulesChanged()
    }
    val locationServicesEnabled = remember(permissionRefresh) {
        context.getSystemService(LocationManager::class.java).isLocationEnabled
    }
    val hasBackgroundNetworkIdentity = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
        context.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED

    fun updatePolicy(updated: WifiSyncPolicy) {
        val registeredOnly = updated.copy(requireApprovedWifi = true)
        policy = registeredOnly
        store.save(registeredOnly)
        onRulesChanged()
    }

    fun addNetwork(ssid: String) {
        updatePolicy(policy.withNetworkEnabled(ssid))
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Wi-Fi sync switch") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text(
                    "Sync can start on any enabled network below. It pauses when you leave all of them.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                SyncGateStatus(policy, connection)
            }
            if (!hasBackgroundNetworkIdentity) {
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text("Background network detection", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Allow location access all the time so Android can reveal the Wi-Fi name while SyncDroid-Mesh is closed. SyncDroid-Mesh does not read GPS location.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = {
                                    backgroundPermissionLauncher.launch(
                                        Intent(
                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.parse("package:${context.packageName}"),
                                        ),
                                    )
                                },
                            ) { Text("Open app permissions") }
                        }
                    }
                }
            }
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp)) {
                            Icon(Icons.Rounded.Wifi, null, Modifier.padding(10.dp))
                        }
                        Spacer(Modifier.width(13.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Registered Wi-Fi only", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Discovery pauses on every other network",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Approved networks", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "${policy.enabledNetworkCount()} enabled",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            when {
                                !hasWifiRuntimePermission(context) -> permissionLauncher.launch(requiredWifiRuntimePermissions())
                                connection.ssid != null -> addNetwork(connection.ssid)
                                connection.isWifiConnected && !locationServicesEnabled -> showLocationServicesDialog = true
                                else -> message = "The current Wi-Fi name is unavailable. Add it manually instead."
                            }
                        },
                    ) {
                        Icon(Icons.Rounded.Wifi, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add current")
                    }
                }
            }
            message?.let { text ->
                item {
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(16.dp)) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            Spacer(Modifier.width(9.dp))
                            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
            }
            if (policy.networks.isEmpty()) {
                item {
                    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp)) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("No approved networks yet", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "Add the current network or enter an SSID manually.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                items(policy.networks, key = { it.ssid }) { rule ->
                    WifiRuleRow(
                        rule = rule,
                        isCurrent = connection.ssid == rule.ssid,
                        onEnabledChange = { enabled ->
                            updatePolicy(
                                policy.copy(
                                    networks = policy.networks.map {
                                        if (it.ssid == rule.ssid) it.copy(enabled = enabled) else it
                                    },
                                ),
                            )
                        },
                        onDelete = {
                            updatePolicy(policy.copy(networks = policy.networks.filterNot { it.ssid == rule.ssid }))
                        },
                    )
                }
            }
            item {
                Button(onClick = { showAddDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("Add a network manually")
                }
                Spacer(Modifier.height(18.dp))
            }
        }
    }

    if (showAddDialog) {
        AddWifiDialog(
            onDismiss = { showAddDialog = false },
            onAdd = {
                addNetwork(it)
                showAddDialog = false
            },
        )
    }

    if (showLocationServicesDialog) {
        AlertDialog(
            onDismissRequest = { showLocationServicesDialog = false },
            title = { Text("Turn on Location services") },
            text = {
                Text(
                    "SyncDroid-Mesh already has Wi-Fi permission, but Android is hiding the connected network name because Location services are off.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLocationServicesDialog = false
                        locationSettingsLauncher.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    },
                ) { Text("Open Location settings") }
            },
            dismissButton = {
                TextButton(onClick = { showLocationServicesDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SyncGateStatus(policy: WifiSyncPolicy, connection: WifiConnectionState) {
    val allowed = policy.allowsSync(connection.isWifiConnected, connection.ssid)
    val statusText = when {
        allowed -> "Sync switched on"
        else -> "Sync paused"
    }
    val detail = when {
        connection.ssid != null -> "Connected to ${connection.ssid}"
        connection.isWifiConnected && !connection.canReadSsid -> "Wi-Fi connected · permission needed to identify it"
        connection.isWifiConnected -> "Wi-Fi connected · network name unavailable"
        else -> "Not connected to Wi-Fi"
    }
    val color = if (allowed) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error

    Surface(color = color.copy(alpha = 0.12f), shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.fillMaxWidth().padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = color, shape = CircleShape) {
                Icon(
                    if (allowed) Icons.Rounded.Wifi else Icons.Rounded.WifiOff,
                    null,
                    modifier = Modifier.padding(10.dp),
                    tint = MaterialTheme.colorScheme.surface,
                )
            }
            Spacer(Modifier.width(13.dp))
            Column {
                Text(statusText, style = MaterialTheme.typography.titleLarge, color = color)
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun WifiRuleRow(
    rule: WifiNetworkRule,
    isCurrent: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                shape = CircleShape,
            ) {
                Icon(
                    Icons.Rounded.Wifi,
                    null,
                    modifier = Modifier.padding(10.dp),
                    tint = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(rule.ssid, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (isCurrent) "Current network" else if (rule.enabled) "Can start sync" else "Ignored",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = rule.enabled, onCheckedChange = onEnabledChange)
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.Delete, contentDescription = "Delete ${rule.ssid}")
            }
        }
    }
}

@Composable
private fun AddWifiDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var ssid by rememberSaveable { mutableStateOf("") }
    val normalized = ssid.trim().removeSurrounding("\"")
    val valid = normalized.isNotEmpty() && normalized.length <= 32

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add approved Wi-Fi") },
        text = {
            Column {
                OutlinedTextField(
                    value = ssid,
                    onValueChange = { ssid = it },
                    label = { Text("Network name (SSID)") },
                    singleLine = true,
                    isError = ssid.isNotBlank() && !valid,
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    "Enter the name exactly, including capital letters.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(normalized) }, enabled = valid) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
