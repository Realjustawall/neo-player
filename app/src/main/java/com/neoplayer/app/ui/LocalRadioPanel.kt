package com.neoplayer.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.neoplayer.app.R
import com.neoplayer.app.radio.LocalRadioMode

@Composable
fun LocalRadioPanel(vm: MainViewModel, close: () -> Unit) {
    val state by vm.localRadio.collectAsState()
    var joinCode by remember { mutableStateOf("") }
    var listenerName by remember { mutableStateOf(Build.MODEL.ifBlank { "NEO" }) }
    val scanPrompt = stringResource(R.string.scan_join_qr)
    BackHandler(onBack = close)

    val bluetoothPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        val allowed = permissions.values.all { it }
        if (allowed) vm.hostLocalRadio()
    }
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { scanned ->
            joinCode = scanned
            vm.joinLocalRadio(scanned, listenerName)
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(close) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.local_radio), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.local_radio_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider()
            when (state.mode) {
                LocalRadioMode.HOSTING -> HostingRadioContent(vm)
                LocalRadioMode.LISTENING -> ListeningRadioContent(vm)
                LocalRadioMode.IDLE -> LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(Icons.Rounded.Radio, null, Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary)
                                Text(stringResource(R.string.host_local_radio), style = MaterialTheme.typography.titleLarge)
                                Text(stringResource(R.string.local_radio_privacy), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Button(onClick = {
                                    if (Build.VERSION.SDK_INT >= 31) {
                                        bluetoothPermission.launch(arrayOf(
                                            Manifest.permission.BLUETOOTH_SCAN,
                                            Manifest.permission.BLUETOOTH_CONNECT,
                                            Manifest.permission.BLUETOOTH_ADVERTISE
                                        ))
                                    } else vm.hostLocalRadio()
                                }) {
                                    Icon(Icons.Rounded.Bluetooth, null)
                                    Text(stringResource(R.string.allow_bluetooth_and_host), Modifier.padding(start = 8.dp))
                                }
                            }
                        }
                    }
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(Icons.Rounded.QrCodeScanner, null, Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary)
                                Text(stringResource(R.string.join_local_radio), style = MaterialTheme.typography.titleLarge)
                                OutlinedTextField(
                                    listenerName,
                                    { listenerName = it.take(60) },
                                    Modifier.fillMaxWidth(),
                                    label = { Text(stringResource(R.string.listener_name)) },
                                    singleLine = true
                                )
                                OutlinedButton({
                                    scanner.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setPrompt(scanPrompt).setBeepEnabled(false))
                                }, Modifier.fillMaxWidth()) {
                                    Icon(Icons.Rounded.QrCodeScanner, null)
                                    Text(stringResource(R.string.scan_join_qr), Modifier.padding(start = 8.dp))
                                }
                                OutlinedTextField(
                                    joinCode,
                                    { joinCode = it },
                                    Modifier.fillMaxWidth(),
                                    label = { Text(stringResource(R.string.join_code)) },
                                    minLines = 2
                                )
                                Button({ vm.joinLocalRadio(joinCode, listenerName) }, Modifier.fillMaxWidth(), enabled = joinCode.isNotBlank()) {
                                    Text(stringResource(R.string.connect))
                                }
                            }
                        }
                    }
                    state.error?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
                }
            }
        }
    }
}

@Composable
private fun HostingRadioContent(vm: MainViewModel) {
    val state by vm.localRadio.collectAsState()
    val qr = remember(state.joinCode) {
        state.joinCode.takeIf(String::isNotBlank)?.let { runCatching { BarcodeEncoder().encodeBitmap(it, BarcodeFormat.QR_CODE, 720, 720) }.getOrNull() }
    }
    LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(stringResource(R.string.now_broadcasting), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(state.trackTitle.ifBlank { stringResource(R.string.no_active_track) }, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(state.trackArtist, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    qr?.let { Image(it.asImageBitmap(), stringResource(R.string.local_radio_qr), Modifier.size(260.dp)) }
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.scan_to_listen), fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.same_network_required), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Bluetooth, null, tint = if (state.bluetoothAdvertising) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(if (state.bluetoothAdvertising) R.string.bluetooth_discoverable else R.string.bluetooth_not_advertising), Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Groups, null)
                Text(stringResource(R.string.connected_listeners, state.listeners.size), Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                if (state.listeners.isNotEmpty()) TextButton(vm::disconnectAllRadioListeners) { Text(stringResource(R.string.disconnect_all)) }
            }
        }
        if (state.listeners.isEmpty()) item { Text(stringResource(R.string.no_listeners), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(state.listeners, key = { it.id }) { listener ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(listener.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton({ vm.disconnectRadioListener(listener.id) }) { Icon(Icons.Rounded.Close, stringResource(R.string.disconnect)) }
            }
        }
        item {
            Button(vm::stopLocalRadio, Modifier.fillMaxWidth()) { Text(stringResource(R.string.stop_local_radio)) }
        }
        state.error?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
    }
}

@Composable
private fun ListeningRadioContent(vm: MainViewModel) {
    val state by vm.localRadio.collectAsState()
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Rounded.Radio, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(if (state.connected) R.string.listening_live else R.string.reconnecting), style = MaterialTheme.typography.headlineSmall)
            Text(state.trackTitle.ifBlank { stringResource(R.string.waiting_for_track) }, fontWeight = FontWeight.Bold)
            Text(state.trackArtist, color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(vm::stopLocalRadio) { Text(stringResource(R.string.leave_radio)) }
        }
    }
}
