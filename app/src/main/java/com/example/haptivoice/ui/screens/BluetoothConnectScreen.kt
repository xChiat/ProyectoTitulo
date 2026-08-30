package com.example.haptivoice.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.haptivoice.ui.theme.HaptivoiceTheme
import com.example.haptivoice.utils.BluetoothManager
import androidx.compose.runtime.collectAsState


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

            val permissions = if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            ) {

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

    val scannedDevice by bluetoothManager.scannedDevices.collectAsState()

    val connectedGlovesCount by bluetoothManager.connectedGlovesCount.collectAsState()
    // =========================================================
    // DISPOSITIVOS MOCK TEMPORALES
    // =========================================================

    /*
     * Por ahora mantenemos estos dos dispositivos falsos
     * para poder probar la interfaz sin tener el ESP32.
     *
     * Más adelante serán reemplazados por:
     *
     * bluetoothManager.scannedDevices
     */

    var mockDevices by remember {

        mutableStateOf(
            mapOf(
                "Guante Izquierdo (ESP32)" to false,
                "Guante Derecho (ESP32)" to false
            )
        )
    }


    // Cantidad de guantes conectados actualmente.
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

                            onNavigateBack(connectedGlovesCount)
                        }

                    ) {

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
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
                modifier = Modifier.height(16.dp)
            )


            // =================================================
            // NO HAY DISPOSITIVOS
            // =================================================

            if (scannedDevice.isEmpty()) {


                Column(

                    modifier = Modifier.fillMaxSize(),

                    verticalArrangement =
                        Arrangement.Center,

                    horizontalAlignment =
                        Alignment.CenterHorizontally

                ) {


                    Icon(

                        imageVector = Icons.Default.Refresh,

                        contentDescription =
                            "Volver a intentar",

                        modifier =
                            Modifier.size(64.dp),

                        tint =
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )


                    Spacer(
                        modifier = Modifier.height(16.dp)
                    )


                    Text(

                        text =
                            "No se encontraron dispositivos / guantes",

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


                    items(scannedDevice) { device ->


                        val isConnected =
                            connectedGlovesCount > 0 &&
                            device.name == "HaptiVoice ESP32"


                        OutlinedCard(

                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {

                                    /*
                                     * Actualmente esta conexión
                                     * sigue siendo simulada.
                                     *
                                     * Más adelante esto será reemplazado por:
                                     *
                                     * bluetoothManager.connectToDevice(...)
                                     *
                                     * y:
                                     *
                                     * bluetoothManager.disconnectDevice(...)
                                     */


                                    if (isConnected) {
                                        bluetoothManager.disconnectDevice(
                                            device.address
                                        )
                                    } else {
                                        bluetoothManager.connectToDevice(
                                            device.address
                                        )
                                    }
                                }

                        ) {


                            Row(

                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),

                                verticalAlignment =
                                    Alignment.CenterVertically,

                                horizontalArrangement =
                                    Arrangement.SpaceBetween

                            ) {


                                Row(

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

                                        text = "Conectado",

                                        color =
                                            MaterialTheme.colorScheme.primary,

                                        style =
                                            MaterialTheme.typography.bodyMedium
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