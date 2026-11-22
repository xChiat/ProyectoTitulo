package com.example.haptivoice.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.haptivoice.data.VocabularyRepository
import com.example.haptivoice.model.CustomGesture
import com.example.haptivoice.model.LemmaOverride
import com.example.haptivoice.utils.BluetoothManager
import com.example.haptivoice.utils.SubscriptionManager
import com.example.haptivoice.utils.TextToSpeechManager
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.OutlinedButton
import com.example.haptivoice.data.admin.AdminRole

// =============================================================
// DICCIONARIO (ETAPA 5: DATOS REALES DESDE ROOM)
// =============================================================

/*
 * Pestaña LSCH: lemas activos del diccionario base (HU11).
 * - Reproducir con TTS (RF-21).
 * - Si el usuario reasignó el gesto, se indica y se puede
 *   restablecer el original (HU14).
 *
 * Pestaña Mis gestos: gestos propios (HU11, HU13, HU14).
 * - Los que exceden el plan gratuito se ven bloqueados y no
 *   se pueden reproducir (HU16, RST-15).
 * - Eliminar exige confirmación.
 * - "Agregar gesto" abre la grabación (requiere los guantes).
 */
@Composable
fun DiccionarioScreen(
    bluetoothManager: BluetoothManager,
    ttsManager: TextToSpeechManager,
    modifier: Modifier = Modifier,
    isPremium: Boolean = false
) {

    val context = LocalContext.current

    val repository = remember {
        VocabularyRepository(context.applicationContext)
    }

    val vocabularyFlow = remember(repository, isPremium) {
        repository.observe(flowOf(isPremium))
    }

    val vocabulary by vocabularyFlow.collectAsState(initial = null)

    val scope = rememberCoroutineScope()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("LSCH", "Mis Gestos")

    var showRecordScreen by remember { mutableStateOf(false) }
    var showFreeLimitDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<CustomGesture?>(null) }
    var pendingRestore by remember { mutableStateOf<Pair<LemmaOverride, String>?>(null) }

    var isAdmin by remember { mutableStateOf(false) }
    var showAdmin by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // Token nuevo: así se ve el rol recién asignado sin cerrar sesión.
        isAdmin = AdminRole.isAdmin(forceRefresh = true)
    }

    if (showAdmin) {

        AdminDictionaryScreen(
            bluetoothManager = bluetoothManager,
            onNavigateBack = { showAdmin = false },
            modifier = modifier
        )

        return
    }

    // =========================================================
    // GRABAR UN GESTO NUEVO
    // =========================================================

    if (showRecordScreen) {

        GestureRecordScreen(
            bluetoothManager = bluetoothManager,
            isPremium = isPremium,
            onNavigateBack = { showRecordScreen = false },
            modifier = modifier
        )

        return
    }

    val freeGestureLimit = SubscriptionManager.FREE_GESTURE_LIMIT

    val baseLemmas =
        vocabulary?.baseLemmas.orEmpty().filter { it.isActive }

    val overrideByLemma =
        vocabulary?.overrides.orEmpty()
            .filter { it.isActive }
            .associateBy { it.lemmaId }

    val customGestures = vocabulary?.customGestures.orEmpty()
    val blockedIds = vocabulary?.blockedIds.orEmpty()

    val activeCustomCount = customGestures.count { it.isActive }

    val freeLimitReached =
        !isPremium && activeCustomCount >= freeGestureLimit

    fun speak(text: String) {

        ttsManager.setLanguage(
            TextToSpeechManager.SupportedLanguage.SPANISH
        )

        ttsManager.speak(text = text)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0.dp),
        floatingActionButton = {
            if (selectedTabIndex == 1) {
                ExtendedFloatingActionButton(
                    onClick = {
                        if (freeLimitReached) {
                            showFreeLimitDialog = true
                        } else {
                            showRecordScreen = true
                        }
                    },
                    icon = {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Agregar gesto"
                        )
                    },
                    text = {
                        Text("Agregar gesto")
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


            // Etapa 8: solo lo ve un administrador.
            if (isAdmin) {

                OutlinedButton(
                    onClick = { showAdmin = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                ) {
                    Text("Administrar diccionario base")
                }
            }

            TabRow(selectedTabIndex = selectedTabIndex) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    )
                }
            }

            // =================================================
            // INFORMACIÓN DEL PLAN
            // =================================================

            if (selectedTabIndex == 1) {

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
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

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = if (isPremium) {
                            "Tu plan permite gestos personalizados ilimitados."
                        } else {
                            "Gestos activos: $activeCustomCount de $freeGestureLimit."
                        },
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

                if (selectedTabIndex == 0) {

                    // =========================================
                    // DICCIONARIO BASE
                    // =========================================

                    if (baseLemmas.isEmpty()) {
                        item {
                            EmptyListText("Aún no hay señas en el diccionario base.")
                        }
                    }

                    items(baseLemmas, key = { it.lemmaId }) { lemma ->

                        val override = overrideByLemma[lemma.lemmaId]

                        DictionaryRow(
                            title = lemma.glosa,
                            subtitle = when {
                                override == null -> null
                                isPremium -> "Usa tu gesto propio."
                                else -> "Tu gesto propio está pausado: requiere Premium."
                            },
                            subtitleIsWarning = override != null && !isPremium,
                            listenEnabled = true,
                            onListen = { speak(lemma.glosa) },
                            actionIcon = if (override != null) Icons.Default.Refresh else null,
                            actionDescription = "Restablecer el gesto original de ${lemma.glosa}",
                            onAction = {
                                if (override != null) {
                                    pendingRestore = override to lemma.glosa
                                }
                            }
                        )
                    }

                } else {

                    // =========================================
                    // MIS GESTOS
                    // =========================================

                    if (customGestures.isEmpty()) {
                        item {
                            EmptyListText(
                                "Todavía no tienes gestos propios. " +
                                        "Conecta los guantes y toca \"Agregar gesto\"."
                            )
                        }
                    }

                    items(customGestures, key = { it.gestureId }) { gesture ->

                        val isBlocked = gesture.gestureId in blockedIds
                        // Etapa 7: el servidor lo desactivó por el tope de 8.
                        val isDisabled = !gesture.isActive
                        val position = customGestures.indexOf(gesture)

                        DictionaryRow(
                            title = gesture.glosa +
                                    if (gesture.template.isRest) " (reposo)" else "",
                            subtitle = when {
                                isDisabled ->
                                    "Desactivado: superó el límite del plan gratuito. " +
                                            "Elimínalo o activa Premium."
                                isBlocked ->
                                    "Bloqueado: supera el límite del plan gratuito."
                                else ->
                                    "${gesture.template.repetitions.size} repeticiones · manos " +
                                            gesture.template.hands.joinToString { it.code.toString() }
                            },
                            subtitleIsWarning = isBlocked || isDisabled,
                            listenEnabled = !isBlocked && !isDisabled && !gesture.template.isRest,
                            onListen = { speak(gesture.glosa) },
                            actionIcon = Icons.Default.Delete,
                            actionDescription = "Eliminar ${gesture.glosa}",
                            onAction = { pendingDelete = gesture }
                        )
                    }
                }
            }
        }
    }

    // =========================================================
    // DIÁLOGOS
    // =========================================================

    if (showFreeLimitDialog) {

        AlertDialog(
            onDismissRequest = { showFreeLimitDialog = false },
            title = { Text("Límite de gestos alcanzado") },
            text = {
                Text(
                    "El plan gratuito permite $freeGestureLimit gestos personalizados " +
                            "activos.\n\nElimina uno o activa HaptiVoice Premium " +
                            "para agregar más."
                )
            },
            confirmButton = {
                TextButton(onClick = { showFreeLimitDialog = false }) {
                    Text("Entendido")
                }
            }
        )
    }

    pendingDelete?.let { gesture ->

        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Eliminar gesto") },
            text = {
                Text(
                    "¿Eliminar \"${gesture.glosa}\"? Dejará de reconocerse " +
                            "y no se puede deshacer."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch { repository.deleteCustomGesture(gesture.gestureId) }
                        pendingDelete = null
                    }
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    pendingRestore?.let { (override, glosa) ->

        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Restablecer gesto") },
            text = {
                Text(
                    "\"$glosa\" volverá a reconocerse con el gesto original " +
                            "del diccionario base. Tu gesto propio se eliminará."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch { repository.deleteOverride(override.overrideId) }
                        pendingRestore = null
                    }
                ) {
                    Text("Restablecer")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestore = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

// =============================================================
// FILA DEL DICCIONARIO
// =============================================================

@Composable
private fun DictionaryRow(
    title: String,
    subtitle: String?,
    subtitleIsWarning: Boolean,
    listenEnabled: Boolean,
    onListen: () -> Unit,
    actionIcon: ImageVector?,
    actionDescription: String,
    onAction: () -> Unit
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

            IconButton(
                onClick = onListen,
                enabled = listenEnabled
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "Escuchar $title",
                    tint = if (listenEnabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            ) {

                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge
                )

                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (subtitleIsWarning) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            if (actionIcon != null) {
                IconButton(onClick = onAction) {
                    Icon(
                        imageVector = actionIcon,
                        contentDescription = actionDescription,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyListText(text: String) {

    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}