package com.example.haptivoice.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.haptivoice.ui.theme.HaptivoiceTheme
import com.example.haptivoice.utils.BluetoothManager
import com.example.haptivoice.utils.SubscriptionManager
import com.example.haptivoice.utils.TextToSpeechManager

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    bluetoothManager: BluetoothManager,
    ttsManager: TextToSpeechManager,
    subscriptionManager: SubscriptionManager,
    onLogout: () -> Unit = {},
) {
    // Variables de estado
    val context = LocalContext.current

// Cantidad real de guantes conectados desde BluetoothManager
    val connectedGlovesCount by
    bluetoothManager.connectedGlovesCount.collectAsState()

    // Variables para la navegación y la tarjeta del ESP32
    var showBluetoothScreen by remember { mutableStateOf(false) }

    // El mensaje ahora se actualiza desde BluetoothManager
    val esp32Message by bluetoothManager.incomingMessage.collectAsState()
    val isPremium by subscriptionManager.isPremium.collectAsState()
    val offers by subscriptionManager.offers.collectAsState()

    var selectedItem by remember { mutableIntStateOf(0) }

    val navItems = listOf(
        Triple("Home", Icons.Default.Home, 0),
        Triple("Ayudas", Icons.Default.Hearing, 1),
        Triple("Diccionario", Icons.AutoMirrored.Filled.MenuBook, 2),
        Triple("Ajustes", Icons.Default.Settings, 3)
    )

    val monthlyPrice = offers
        .firstOrNull { it.basePlanId == SubscriptionManager.BASE_PLAN_MONTHLY }
        ?.formattedPrice
        .orEmpty()

    // Si se activó la pantalla de Bluetooth, la mostramos y ocultamos el Scaffold principal
    if (showBluetoothScreen) {
        BluetoothConnectScreen(
            bluetoothManager = bluetoothManager,
            onNavigateBack = {
                showBluetoothScreen = false // Volvemos al HomeScreen
            }
        )
        return // Interrumpe el renderizado del código de abajo mientras estemos en Bluetooth
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                windowInsets = WindowInsets(0.dp)
            ) {
                navItems.forEach { (label, icon, index) ->
                    NavigationBarItem(
                        selected = selectedItem == index,
                        onClick = { selectedItem = index },
                        label = { Text(text = label) },
                        icon = { Icon(imageVector = icon, contentDescription = label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        when (selectedItem) {

            0 -> {

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp)
                            .padding(bottom = 16.dp)
                    ) {

                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {

                            Text(
                                text = if (isPremium) {
                                    "Premium activo"
                                } else {
                                    "Plan gratuito"
                                },
                                style = MaterialTheme.typography.titleMedium
                            )

                            Spacer(
                                modifier = Modifier.height(4.dp)
                            )

                            Text(
                                text = if (isPremium) {
                                    "Traducción, gestos ilimitados y escritura a voz disponibles. Las voces cloud se activan cuando esté lista la API."
                                } else {
                                    "La voz del sistema sigue disponible. Premium desbloquea traducción, gestos ilimitados y escritura a voz."
                                },
                                style = MaterialTheme.typography.bodySmall
                            )

                            if (!isPremium) {

                                Spacer(
                                    modifier = Modifier.height(12.dp)
                                )

                                Button(
                                    onClick = {

                                        val activity =
                                            context as? Activity

                                        if (activity == null) {

                                            Toast.makeText(
                                                context,
                                                "No se pudo abrir el pago",
                                                Toast.LENGTH_SHORT
                                            ).show()

                                            return@Button
                                        }

                                        subscriptionManager.purchase(
                                            activity = activity,
                                            basePlanId =
                                                SubscriptionManager.BASE_PLAN_MONTHLY,
                                            onError = { msg ->

                                                Toast.makeText(
                                                    context,
                                                    msg,
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {

                                    Text(
                                        text =
                                            if (monthlyPrice.isBlank()) {
                                                "suscribirse"
                                            } else {
                                                "suscribirse · $monthlyPrice"
                                            }
                                    )
                                }
                            }
                        }
                    }

                    // Mientras falte al menos un guante,
                    // el botón para vincular seguirá disponible.
                    if (
                        connectedGlovesCount <
                        BluetoothManager.MAX_CONNECTED_GLOVES
                    ) {

                        Button(
                            onClick = {
                                showBluetoothScreen = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 32.dp)
                                .padding(bottom = 16.dp)
                        ) {

                            Text(
                                text =
                                    if (connectedGlovesCount == 0) {
                                        "Vincular Guantes"
                                    } else {
                                        "Vincular otro Guante"
                                    }
                            )
                        }
                    }

                    // Si existe al menos un guante conectado,
                    // mostramos su estado y los mensajes recibidos.
                    if (connectedGlovesCount > 0) {

                        Text(
                            text =
                                "Guantes vinculados: $connectedGlovesCount",
                            style =
                                MaterialTheme.typography.headlineSmall,
                            color =
                                MaterialTheme.colorScheme.primary
                        )

                        Spacer(
                            modifier = Modifier.height(16.dp)
                        )

                        OutlinedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 32.dp)
                                .padding(bottom = 32.dp)
                        ) {

                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment =
                                    Alignment.CenterHorizontally
                            ) {

                                Text(
                                    text =
                                        "Mensaje entrante del ESP32:",
                                    style =
                                        MaterialTheme.typography.labelMedium,
                                    color =
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(
                                    modifier = Modifier.height(8.dp)
                                )

                                Text(
                                    text = esp32Message,
                                    style =
                                        MaterialTheme.typography.bodyLarge,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }

            1 -> {

                AyudasAuditivasScreen(
                    ttsManager = ttsManager,
                    modifier =
                        Modifier.padding(innerPadding)
                )
            }

            2 -> {

                DiccionarioScreen(
                    modifier =
                        Modifier.padding(innerPadding)
                )
            }

            3 -> {

                AjustesScreen(
                    modifier =
                        Modifier.padding(innerPadding),
                    onLogout = onLogout
                )
            }
        }
    }
}
@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    HaptivoiceTheme {
        // Manager usado solo para mostrar el Preview
        val context = LocalContext.current
        val bluetoothManager = remember { BluetoothManager(context.applicationContext) }
        val ttsManager = remember { TextToSpeechManager(context.applicationContext) }
        val subscriptionManager = remember { SubscriptionManager(context.applicationContext) }

        HomeScreen(
            bluetoothManager = bluetoothManager,
            ttsManager = ttsManager,
            subscriptionManager = subscriptionManager
        )
    }
}