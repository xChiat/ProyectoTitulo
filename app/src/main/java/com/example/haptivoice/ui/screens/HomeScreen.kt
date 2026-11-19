
package com.example.haptivoice.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.RadioButton
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

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
    var showBluetoothScreen by remember {
        mutableStateOf(false)
    }

    // El mensaje ahora se actualiza desde BluetoothManager
    val esp32Message by bluetoothManager.incomingMessage.collectAsState()

    // =========================================================
    // ESTADO DE GOOGLE PLAY BILLING Y PREMIUM
    // =========================================================

    // Estado actual de la suscripción.
    val isPremium by subscriptionManager.isPremium.collectAsState()

    // Verificamos si Google Play Billing está conectado.
    val billingReady by subscriptionManager.billingReady.collectAsState()

    // Ofertas reales obtenidas desde Google Play.
    val offers by subscriptionManager.offers.collectAsState()

    var selectedItem by remember {
        mutableIntStateOf(0)
    }

    val navItems = listOf(
        Triple("Home", Icons.Default.Home, 0),
        Triple("Ayudas", Icons.Default.Hearing, 1),
        Triple("Diccionario", Icons.AutoMirrored.Filled.MenuBook, 2),
        Triple("Ajustes", Icons.Default.Settings, 3)
    )

    // =========================================================
    // PLANES PREMIUM MENSUAL Y ANUAL
    // =========================================================

    /*
     * Ambos planes pertenecen a una sola suscripción:
     *
     * ID de suscripción: haptivoice_premium
     *
     * Plan mensual:
     * ID del plan base: monthly
     *
     * Plan anual:
     * ID del plan base: yearly
     *
     * Los precios se consultan directamente desde
     * Google Play Billing.
     *
     * No se definen precios manualmente en la aplicación.
     *
     * Mientras Google Play no entregue el plan solicitado,
     * no se permitirá iniciar esa compra.
     */

    val monthlyOffer = offers.firstOrNull {
        it.basePlanId == SubscriptionManager.BASE_PLAN_MONTHLY
    }

    val yearlyOffer = offers.firstOrNull {
        it.basePlanId == SubscriptionManager.BASE_PLAN_YEARLY
    }

    // Precio real entregado por Google Play.
    val monthlyPrice = monthlyOffer?.formattedPrice.orEmpty()
    val yearlyPrice = yearlyOffer?.formattedPrice.orEmpty()

    // =========================================================
    // PLAN SELECCIONADO POR EL USUARIO
    // =========================================================

    /*
     * El plan mensual se selecciona inicialmente.
     *
     * El usuario puede cambiar al plan anual
     * antes de iniciar la compra.
     *
     * El ID seleccionado se envía directamente
     * al SubscriptionManager.
     */

    var selectedBasePlanId by remember {
        mutableStateOf(SubscriptionManager.BASE_PLAN_MONTHLY)
    }

    val selectedOffer = when (selectedBasePlanId) {
        SubscriptionManager.BASE_PLAN_MONTHLY -> monthlyOffer
        SubscriptionManager.BASE_PLAN_YEARLY -> yearlyOffer
        else -> null
    }

    val selectedPrice = selectedOffer?.formattedPrice.orEmpty()

    val selectedPeriod = when (selectedBasePlanId) {
        SubscriptionManager.BASE_PLAN_YEARLY -> "año"
        else -> "mes"
    }

    // Solo se permite intentar la compra si Billing está listo
    // y Google Play devolvió exactamente el plan solicitado.
    val canPurchasePremium =
        billingReady && selectedOffer != null && !isPremium

    // Si se activó la pantalla de Bluetooth, la mostramos
    // y ocultamos el Scaffold principal.
    if (showBluetoothScreen) {
        BluetoothConnectScreen(
            bluetoothManager = bluetoothManager,
            onNavigateBack = {
                showBluetoothScreen = false // Volvemos al HomeScreen
            }
        )

        return // Interrumpe el renderizado mientras estemos en Bluetooth
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
                        onClick = {
                            selectedItem = index
                        },
                        label = {
                            Text(text = label)
                        },
                        icon = {
                            Icon(
                                imageVector = icon,
                                contentDescription = label
                            )
                        }
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
                        .padding(innerPadding)
                    .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    // =========================================================
                    // TARJETA DE SUSCRIPCIÓN PREMIUM
                    // =========================================================

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
                                    modifier = Modifier.height(16.dp)
                                )

                                // =========================================
                                // SELECCIÓN DEL PLAN PREMIUM
                                // =========================================

                                /*
                                 * El usuario puede seleccionar:
                                 *
                                 * monthly = plan mensual
                                 * yearly = plan anual
                                 *
                                 * Los precios son los que devuelve
                                 * Google Play Console.
                                 */

                                Text(
                                    text = "Selecciona tu plan Premium",
                                    style = MaterialTheme.typography.titleSmall
                                )

                                Spacer(
                                    modifier = Modifier.height(8.dp)
                                )

                                // =========================================
                                // OPCIÓN: PREMIUM MENSUAL
                                // =========================================

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(
                                            enabled = billingReady &&
                                                    monthlyOffer != null
                                        ) {
                                            selectedBasePlanId =
                                                SubscriptionManager.BASE_PLAN_MONTHLY
                                        }
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {

                                    RadioButton(
                                        selected =
                                            selectedBasePlanId ==
                                                    SubscriptionManager.BASE_PLAN_MONTHLY,
                                        onClick = null,
                                        enabled = billingReady &&
                                                monthlyOffer != null
                                    )

                                    Column(
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = "Premium Mensual",
                                            style = MaterialTheme.typography.bodyMedium
                                        )

                                        Text(
                                            text = if (monthlyOffer != null) {
                                                if (monthlyPrice.isNotBlank()) {
                                                    "$monthlyPrice / mes"
                                                } else {
                                                    "Precio por consultar"
                                                }
                                            } else {
                                                "No disponible"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color =
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                // =========================================
                                // OPCIÓN: PREMIUM ANUAL
                                // =========================================

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(
                                            enabled = billingReady &&
                                                    yearlyOffer != null
                                        ) {
                                            selectedBasePlanId =
                                                SubscriptionManager.BASE_PLAN_YEARLY
                                        }
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {

                                    RadioButton(
                                        selected =
                                            selectedBasePlanId ==
                                                    SubscriptionManager.BASE_PLAN_YEARLY,
                                        onClick = null,
                                        enabled = billingReady &&
                                                yearlyOffer != null
                                    )

                                    Column(
                                        modifier = Modifier.weight(1f)
                                    ) {

                                        Text(
                                            text = "Premium Anual",
                                            style = MaterialTheme.typography.bodyMedium
                                        )

                                        Text(
                                            text = if (yearlyOffer != null) {
                                                if (yearlyPrice.isNotBlank()) {
                                                    "$yearlyPrice / año"
                                                } else {
                                                    "Precio por consultar"
                                                }
                                            } else {
                                                "No disponible"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color =
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Spacer(
                                    modifier = Modifier.height(12.dp)
                                )

                                // =========================================
                                // BOTÓN SUSCRIBIRSE
                                // =========================================

                                /*
                                 * Solo se habilita cuando Google Play Billing
                                 * está conectado y el plan seleccionado
                                 * está disponible en Play Console.
                                 *
                                 * No se activa Premium manualmente.
                                 * El resultado de la compra será procesado
                                 * por SubscriptionManager.
                                 *
                                 * Se utiliza exactamente el ID seleccionado
                                 * y nunca se sustituye por otro plan.
                                 */

                                Button(
                                    onClick = {

                                        if (!canPurchasePremium) {
                                            return@Button
                                        }

                                        val activity = context as? Activity

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
                                            basePlanId = selectedBasePlanId,
                                            onError = { msg ->

                                                Toast.makeText(
                                                    context,
                                                    msg,
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        )
                                    },

                                    enabled = canPurchasePremium,

                                    modifier = Modifier.fillMaxWidth()
                                ) {

                                    Text(
                                        text = when {

                                            !billingReady -> {
                                                "Conectando con Google Play..."
                                            }

                                            selectedOffer == null -> {
                                                "Plan no disponible"
                                            }

                                            selectedPrice.isBlank() -> {
                                                "Suscribirse al plan seleccionado"
                                            }

                                            else -> {
                                                "Suscribirse · $selectedPrice/$selectedPeriod"
                                            }
                                        }
                                    )
                                }

                                // =========================================
                                // MENSAJE DE DISPONIBILIDAD
                                // =========================================

                                /*
                                 * Mientras no se configure el producto
                                 * Premium o alguno de sus planes básicos
                                 * en Google Play Console, informamos que
                                 * el plan solicitado no está disponible.
                                 */

                                if (!canPurchasePremium) {

                                    Spacer(
                                        modifier = Modifier.height(8.dp)
                                    )

                                    Text(
                                        text = when {

                                            !billingReady -> {
                                                "Google Play Billing todavía no está conectado."
                                            }

                                            selectedOffer == null -> {
                                                "El plan seleccionado todavía no está disponible en Google Play."
                                            }

                                            else -> {
                                                "No es posible iniciar la compra en este momento."
                                            }
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color =
                                            MaterialTheme.colorScheme.onSurfaceVariant
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
                    modifier = Modifier.padding(innerPadding),
                    isPremium = isPremium
                )
            }

            2 -> {

                DiccionarioScreen(
                    modifier = Modifier.padding(innerPadding),
                    isPremium = isPremium
                )
            }

            3 -> {

                AjustesScreen(
                    modifier = Modifier.padding(innerPadding),
                    subscriptionManager = subscriptionManager,
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

        val bluetoothManager = remember {
            BluetoothManager(context.applicationContext)
        }

        val ttsManager = remember {
            TextToSpeechManager(context.applicationContext)
        }

        val subscriptionManager = remember {
            SubscriptionManager(context.applicationContext)
        }

        HomeScreen(
            bluetoothManager = bluetoothManager,
            ttsManager = ttsManager,
            subscriptionManager = subscriptionManager
        )
    }
}
