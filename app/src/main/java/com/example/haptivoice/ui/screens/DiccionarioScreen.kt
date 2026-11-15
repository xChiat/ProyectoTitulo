
package com.example.haptivoice.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.haptivoice.ui.theme.HaptivoiceTheme
import com.example.haptivoice.utils.SubscriptionManager

@Composable
fun DiccionarioScreen(
    modifier: Modifier = Modifier,
    isPremium: Boolean = false,

    // Cantidad real de gestos guardados.
    // null significa que el almacenamiento aun no esta integrado.
    savedGestureCount: Int? = null
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("LSCH", "Mis Gestos")

    // Mock data x mientras
    val lschList = listOf("Hola", "Gracias", "Por favor", "Permiso", "Disculpa")
    val misGestosList = listOf("Gesto personalizado 1", "Mi saludo", "Ayuda urgente")

    // =============================================================
    // CONTROL DE BENEFICIOS PREMIUM
    // =============================================================

    /*
     * El plan gratuito permite hasta 3 gestos personalizados.
     * El plan Premium permite gestos ilimitados.
     *
     * Los datos de ejemplo de misGestosList NO se cuentan
     * como gestos realmente guardados.
     *
     * Cuando se implemente el almacenamiento de gestos,
     * HomeScreen debera entregar savedGestureCount con la
     * cantidad real asociada a la cuenta del usuario.
     *
     * Esta validacion de interfaz debera repetirse en el
     * servicio responsable de guardar el gesto.
     */

    val freeGestureLimit = SubscriptionManager.FREE_GESTURE_LIMIT

    val freeLimitReached =
        !isPremium &&
                savedGestureCount != null &&
                savedGestureCount >= freeGestureLimit

    var showFreeLimitDialog by remember {
        mutableStateOf(false)
    }

    // Control de la pantalla para agregar gestos.
    var showAddGestureScreen by remember { mutableStateOf(false) }

    if (showAddGestureScreen) {

        AgregarGestoScreen(
            modifier = modifier,
            isPremium = isPremium,
            onNavigateBack = {
                showAddGestureScreen = false
            }
        )

    } else {

        Scaffold(
            modifier = modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets(0.dp),
            floatingActionButton = {
                if (selectedTabIndex == 1) {
                    ExtendedFloatingActionButton(
                        onClick = {

                            // Solo bloqueamos si conocemos la cantidad
                            // REAL de gestos guardados.
                            if (freeLimitReached) {
                                showFreeLimitDialog = true
                            } else {
                                showAddGestureScreen = true
                            }

                            /* TODO: Lógica para agregar gesto */
                        },
                        icon = {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Agregar gesto"
                            )
                        },
                        text = {
                            Text("Agregar gestos")
                        },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Text(
                    text = "Diccionario de gestos",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.padding(
                        horizontal = 16.dp,
                        vertical = 16.dp
                    )
                )

                TabRow(selectedTabIndex = selectedTabIndex) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = {
                                selectedTabIndex = index
                            },
                            text = {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        )
                    }
                }

                // =====================================================
                // INFORMACION DEL PLAN ACTUAL
                // =====================================================

                if (selectedTabIndex == 1) {

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = 16.dp,
                                vertical = 12.dp
                            )
                    ) {

                        Text(
                            text = if (isPremium) {
                                "HaptiVoice Premium activo"
                            } else {
                                "Plan gratuito"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(
                            modifier = Modifier.height(4.dp)
                        )

                        Text(
                            text = when {

                                isPremium -> {
                                    "Tu plan permite gestos personalizados ilimitados."
                                }

                                savedGestureCount != null -> {
                                    "Gestos guardados: " +
                                            "${savedGestureCount.coerceAtLeast(0)} " +
                                            "de $freeGestureLimit disponibles."
                                }

                                else -> {
                                    "El plan gratuito permite hasta " +
                                            "$freeGestureLimit gestos personalizados."
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(
                            modifier = Modifier.height(4.dp)
                        )

                        Text(
                            text = "Los gestos mostrados actualmente son " +
                                    "ejemplos. El almacenamiento real " +
                                    "se integrará junto con la captura.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 16.dp,
                        bottom = 88.dp
                    )
                ) {
                    val currentList =
                        if (selectedTabIndex == 0) {
                            lschList
                        } else {
                            misGestosList
                        }

                    items(currentList) { word ->
                        DiccionarioItem(
                            word = word,
                            onListen = {
                                /* TODO: Acción de escuchar */
                            },
                            onEdit = {
                                /* TODO: Acción de editar */
                            },
                            onReset = {
                                /* TODO: Acción de restablecer */
                            }
                        )
                    }
                }
            }
        }
    }

    // =============================================================
    // AVISO DEL LIMITE DE GESTOS GRATUITOS
    // =============================================================

    if (showFreeLimitDialog) {

        AlertDialog(
            onDismissRequest = {
                showFreeLimitDialog = false
            },

            title = {
                Text("Límite de gestos alcanzado")
            },

            text = {
                Text(
                    "El plan gratuito permite guardar hasta " +
                            "$freeGestureLimit gestos personalizados.\n\n" +
                            "Con HaptiVoice Premium podrás agregar " +
                            "gestos ilimitados cuando la función " +
                            "de captura esté disponible."
                )
            },

            confirmButton = {
                TextButton(
                    onClick = {
                        showFreeLimitDialog = false
                    }
                ) {
                    Text("Entendido")
                }
            }
        )
    }
}

@Composable
fun DiccionarioItem(
    word: String,
    onListen: () -> Unit,
    onEdit: () -> Unit,
    onReset: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Ícono Escuchar
            IconButton(onClick = onListen) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "Escuchar",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            // Palabra o Expresión (Toma el espacio restante)
            Text(
                text = word,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            )

            // Ícono Editar
            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Editar",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Ícono Restablecer
            IconButton(onClick = onReset) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Restablecer",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// =============================================================
// PANTALLA PARA AGREGAR GESTOS
// =============================================================

@Composable
fun AgregarGestoScreen(
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit,
    isPremium: Boolean = false
) {

    var gestureName by remember { mutableStateOf("") }
    var associatedText by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {

            IconButton(
                onClick = onNavigateBack
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Volver al diccionario"
                )
            }

            Text(
                text = "Agregar gesto",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // =====================================================
        // INFORMACION PREMIUM EN EL FORMULARIO
        // =====================================================

        Text(
            text = if (isPremium) {
                "Plan Premium: gestos ilimitados"
            } else {
                "Plan gratuito: máximo " +
                        "${SubscriptionManager.FREE_GESTURE_LIMIT} gestos"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Datos del nuevo gesto.
        Text(
            text = "Información del gesto",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = gestureName,
            onValueChange = {
                gestureName = it
            },
            label = {
                Text("Nombre del gesto")
            },
            placeholder = {
                Text("Ej. Mi saludo")
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = associatedText,
            onValueChange = {
                associatedText = it
            },
            label = {
                Text("Texto asociado")
            },
            placeholder = {
                Text("Ej. Hola, mucho gusto")
            },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Este texto se asociará al gesto para su futura reproducción.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Captura pendiente de integrar con los guantes.
        Text(
            text = "Captura del gesto",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Text(
                    text = "Captura mediante guantes",
                    style = MaterialTheme.typography.titleSmall
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Aquí se realizará la captura de los movimientos del gesto utilizando los sensores de los guantes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(20.dp))

                OutlinedButton(
                    onClick = {
                        // Pendiente: captura de sensores.
                    },
                    enabled = false,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Capturar gesto")
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Función pendiente de integrar",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // El guardado se habilitará cuando esté implementada la captura.
        Button(
            onClick = {
                // Pendiente: guardar el gesto.

                /*
                 * Al implementar el guardado:
                 * 1. Consultar los gestos reales del usuario.
                 * 2. Verificar el limite del plan gratuito.
                 * 3. Guardar el gesto y la captura.
                 * 4. Actualizar la cantidad de gestos.
                 *
                 * La comprobacion no debe depender
                 * solamente de la interfaz.
                 */
            },
            enabled = false,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Guardar gesto")
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "El guardado estará disponible cuando se implemente la captura de gestos.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview(showBackground = true)
@Composable
fun DiccionarioScreenPreview() {
    HaptivoiceTheme {
        DiccionarioScreen(
            isPremium = false
        )
    }
}

@Preview(showBackground = true)
@Composable
fun AgregarGestoScreenPreview() {
    HaptivoiceTheme {
        AgregarGestoScreen(
            onNavigateBack = {}
        )
    }
}
