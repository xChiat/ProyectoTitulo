package com.example.haptivoice.ui.screens

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.haptivoice.ui.theme.HaptivoiceTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluetoothConnectScreen(
    onNavigateBack: (Int) -> Unit // Retorna la cantidad de guantes conectados (0, 1 o 2)
) {
    // ESTADO MOCK PARA PRUEBAS: Tu compañero reemplazará esto con su lista real de dispositivos BLE
    var mockDevices by remember {
        mutableStateOf(
            mapOf("Guante Izquierdo (ESP32)" to false, "Guante Derecho (ESP32)" to false)
        )
    }

    // Calculamos cuántos guantes están conectados actualmente
    val connectedGlovesCount = mockDevices.values.count { it }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vincular Guantes") },
                navigationIcon = {
                    IconButton(onClick = { onNavigateBack(connectedGlovesCount) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
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
            Spacer(modifier = Modifier.height(16.dp))

            if (mockDevices.isEmpty()) {
                // VISTA VACÍA: No se encontraron dispositivos
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Volver a intentar",
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No se encontraron dispositivos / guantes",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // VISTA CON DISPOSITIVOS
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(mockDevices.keys.toList()) { deviceName ->
                        val isConnected = mockDevices[deviceName] == true

                        OutlinedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    // Simulamos la conexión/desconexión
                                    val newDevices = mockDevices.toMutableMap()
                                    newDevices[deviceName] = !isConnected
                                    mockDevices = newDevices
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (isConnected) Icons.Default.BluetoothConnected else Icons.Default.Bluetooth,
                                        contentDescription = null,
                                        tint = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = deviceName,
                                        modifier = Modifier.padding(start = 16.dp),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (isConnected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                                if (isConnected) {
                                    Text(
                                        text = "Conectado",
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.bodyMedium
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

@Preview(showBackground = true)
@Composable
fun BluetoothConnectScreenPreview() {
    HaptivoiceTheme {
        BluetoothConnectScreen(onNavigateBack = { /* Acción de navegación simulada */ })
    }
}