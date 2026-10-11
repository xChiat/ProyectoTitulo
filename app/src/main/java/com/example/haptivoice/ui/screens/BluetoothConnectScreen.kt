
package com.example.haptivoice.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.haptivoice.ui.theme.HaptivoiceTheme
import com.example.haptivoice.utils.BluetoothManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluetoothConnectScreen(
    bluetoothManager: BluetoothManager,
    onNavigateBack: (Int) -> Unit,
    enableBluetoothActions: Boolean = true
) {

    // =========================================================
    // PERMISOS BLUETOOTH
    // =========================================================

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->

        // Comprueba que todos los permisos solicitados
        // hayan sido aceptados por el usuario.
        val allGranted = permissions.values.all { it }

        if (allGranted) {

            // Una vez concedidos los permisos,
            // comenzamos el escaneo BLE.
            bluetoothManager.startScan()
        }
    }

    // =========================================================
    // ACCIONES AL ENTRAR A LA PANTALLA
    // =========================================================

    LaunchedEffect(Unit) {

        /*
         * Si estamos solamente en el Preview de Android Studio,
         * no intentamos utilizar Bluetooth.
         */
        if (!enableBluetoothActions) {
            return@LaunchedEffect
        }

        /*
         * Si ya tenemos los permisos necesarios,
         * comenzamos inmediatamente el escaneo.
         */
        if (
            bluetoothManager.hasScanPermission() &&
            bluetoothManager.hasConnectPermission()
        ) {

            bluetoothManager.startScan()

        } else {

            /*
             * Android 12 o superior:
             *
             * BLUETOOTH_SCAN
             * BLUETOOTH_CONNECT
             *
             * Android 11 o anterior:
             *
             * ACCESS_FINE_LOCATION
             */

            val permissions =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {

                    arrayOf(
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT
                    )

                } else {

                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION
                    )
                }

            // Mostramos la ventana de permisos de Android.
            permissionLauncher.launch(permissions)
        }
    }

    val scannedDevices by
    bluetoothManager.scannedDevices.collectAsState()

    val connectedGlovesCount by
    bluetoothManager.connectedGlovesCount.collectAsState()

    val connectedDeviceAddresses by
    bluetoothManager.connectedDeviceAddresses.collectAsState()

    // Estado de los guantes silenciados.
    val mutedDeviceAddresses by
    bluetoothManager.mutedDeviceAddresses.collectAsState()

    // =========================================================
    // INTERFAZ
    // =========================================================

    Scaffold(

        topBar = {

            TopAppBar(

                title = {
                    Text("Vincular Guantes")
                },

                navigationIcon = {

                    IconButton(

                        onClick = {

                            /*
                             * Si estamos ejecutando la aplicación real,
                             * detenemos el escaneo al salir.
                             */
                            if (enableBluetoothActions) {
                                bluetoothManager.stopScan()
                            }

                            onNavigateBack(
                                connectedGlovesCount
                            )
                        }

                    ) {

                        Icon(
                            imageVector =
                                Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver"
                        )
                    }
                }
            )
        }

    ) { innerPadding ->

        Column(

            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)

        ) {

            Text(
                text = "Dispositivos disponibles",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            Text(
                text = "Guantes conectados: $connectedGlovesCount / 2",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(
                modifier = Modifier.height(16.dp)
            )

            // =================================================
            // NO HAY DISPOSITIVOS
            // =================================================

            if (scannedDevices.isEmpty()) {

                Column(

                    modifier = Modifier.fillMaxSize(),

                    verticalArrangement =
                        Arrangement.Center,

                    horizontalAlignment =
                        Alignment.CenterHorizontally

                ) {

                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Volver a intentar",
                        modifier = Modifier.size(64.dp),
                        tint =
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(
                        modifier = Modifier.height(16.dp)
                    )

                    Text(
                        text =
                            "No se encontraron dispositivos Bluetooth con nombre",
                        style =
                            MaterialTheme.typography.bodyLarge,
                        color =
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

            } else {

                // =================================================
                // HAY DISPOSITIVOS
                // =================================================

                LazyColumn(
                    verticalArrangement =
                        Arrangement.spacedBy(8.dp)
                ) {

                    items(
                        items = scannedDevices,
                        key = { device ->
                            device.address
                        }
                    ) { device ->

                        /*
                         * Ahora comprobamos la dirección real
                         * del dispositivo conectado.
                         */
                        val isConnected =
                            connectedDeviceAddresses.contains(
                                device.address
                            )

                        // Comprobamos si el guante está silenciado.
                        val isMuted =
                            mutedDeviceAddresses.contains(
                                device.address
                            )

                        OutlinedCard(

                            modifier = Modifier.fillMaxWidth()

                        ) {

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {

                                Row(

                                    modifier = Modifier
                                        .fillMaxWidth(),

                                    verticalAlignment =
                                        Alignment.CenterVertically,

                                    horizontalArrangement =
                                        Arrangement.SpaceBetween

                                ) {

                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment =
                                            Alignment.CenterVertically
                                    ) {

                                        Icon(

                                            imageVector =
                                                if (isConnected) {
                                                    Icons.Default.BluetoothConnected
                                                } else {
                                                    Icons.Default.Bluetooth
                                                },

                                            contentDescription = null,

                                            tint =
                                                if (isConnected) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                }
                                        )

                                        Text(

                                            text = device.name,

                                            modifier =
                                                Modifier.padding(
                                                    start = 16.dp
                                                ),

                                            style =
                                                MaterialTheme.typography.bodyLarge,

                                            fontWeight =
                                                if (isConnected) {
                                                    FontWeight.Bold
                                                } else {
                                                    FontWeight.Normal
                                                }
                                        )
                                    }

                                    if (isConnected) {

                                        Text(
                                            text =
                                                if (isMuted) {
                                                    "Silenciado"
                                                } else {
                                                    "Conectado"
                                                },
                                            color =
                                                MaterialTheme.colorScheme.primary,
                                            style =
                                                MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }

                                Spacer(
                                    modifier = Modifier.height(12.dp)
                                )

                                if (isConnected) {

                                    // Botones independientes para cada guante.
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement =
                                            Arrangement.spacedBy(8.dp)
                                    ) {

                                        OutlinedButton(
                                            onClick = {
                                                bluetoothManager.toggleMute(
                                                    device.address
                                                )
                                            },
                                            modifier = Modifier.weight(1f),
                                            enabled = enableBluetoothActions
                                        ) {

                                            Text(
                                                text =
                                                    if (isMuted) {
                                                        "Desmutear"
                                                    } else {
                                                        "Mutear"
                                                    }
                                            )
                                        }

                                        Button(
                                            onClick = {
                                                bluetoothManager.disconnectDevice(
                                                    device.address
                                                )
                                            },
                                            modifier = Modifier.weight(1f),
                                            enabled = enableBluetoothActions
                                        ) {

                                            Text("Desconectar")
                                        }
                                    }

                                } else {

                                    Button(
                                        onClick = {
                                            bluetoothManager.connectToDevice(
                                                device.address
                                            )
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled =
                                            enableBluetoothActions &&
                                                    connectedGlovesCount <
                                                    BluetoothManager.MAX_CONNECTED_GLOVES
                                    ) {

                                        Text("Conectar")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// =============================================================
// PREVIEW
// =============================================================

@Preview(showBackground = true)
@Composable
fun BluetoothConnectScreenPreview() {

    HaptivoiceTheme {

        val context =
            LocalContext.current

        val bluetoothManager =
            remember {
                BluetoothManager(context)
            }

        BluetoothConnectScreen(
            bluetoothManager = bluetoothManager,
            onNavigateBack = {},
            /*
             * Muy importante:
             *
             * En el Preview no intentamos
             * solicitar permisos ni escanear Bluetooth.
             */
            enableBluetoothActions = false
        )
    }
}
