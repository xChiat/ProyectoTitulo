package com.example.haptivoice.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.haptivoice.data.SaveGestureResult
import com.example.haptivoice.data.VocabularyRepository
import com.example.haptivoice.model.CustomGesture
import com.example.haptivoice.model.GestureTemplate
import com.example.haptivoice.model.Hand
import com.example.haptivoice.model.PoseSample
import com.example.haptivoice.utils.BluetoothManager
import com.example.haptivoice.utils.CalibrationStore
import com.example.haptivoice.utils.GestureRules
import com.example.haptivoice.utils.ImuNormalizer
import com.example.haptivoice.utils.PoseMath
import com.example.haptivoice.utils.SavePlan
import com.example.haptivoice.utils.SegmenterStatus
import com.example.haptivoice.utils.StaticPoseSegmenter
import com.example.haptivoice.utils.SubscriptionManager
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.util.UUID

// Repeticiones por seña: se graban 5, se puede guardar desde 3.
private const val TARGET_REPETITIONS = 5
private const val MIN_REPETITIONS = 3

/*
 * HU13 pide los dos guantes para grabar. Mientras se prueba con
 * un solo guante queda en false: basta con que estén conectadas
 * las manos marcadas. Poner true para exigir el par completo.
 */
private const val REQUIRE_BOTH_GLOVES = false

// Sobre esta desviación entre repeticiones se avisa.
private const val MAX_CONSISTENT_DEVIATION_DEG = 20f

// =============================================================
// GRABACIÓN DE SEÑAS ESTÁTICAS (ETAPAS 3, 5 Y 6, SOLO MPU)
// =============================================================

/*
 * Para grabar una seña:
 * 1. Escribe la palabra y elige las manos que usa.
 * 2. Presiona "Comenzar grabación".
 * 3. Haz la seña y quédate quieto ~0,5 s: se captura una repetición.
 * 4. Mueve las manos, vuelve a la postura y repite hasta 5 veces.
 * 5. Guarda la seña.
 *
 * Marca "Postura de reposo" para grabar las manos en descanso:
 * el reconocedor la usará para no mostrar nada en ese caso.
 *
 * Se guarda en Room como gesto propio del usuario. Si la palabra
 * ya está en el diccionario base, se guarda como reasignación
 * (solo premium). El plan básico admite 8 gestos propios.
 *
 * Etapa 6 (CU-013, CU-014):
 * - Grabar exige los dos guantes vinculados (HU13).
 * - Antes de grabar se avisa qué pasará con la palabra
 *   (nueva, regrabación, reasignación, requiere premium, tope).
 * - Si la seña se parece demasiado a otra, se avisa.
 * - Eliminar pide confirmación.
 *
 * Etapa 8 (CU-012): con adminSave la misma captura graba un lema
 * del diccionario base. No aplica las reglas del plan ni guarda
 * en Room: entrega la plantilla al administrador, que la sube
 * al borrador en Firestore. adminSave devuelve el mensaje.
 */
@Composable
fun GestureRecordScreen(
    bluetoothManager: BluetoothManager,
    isPremium: Boolean,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    initialGlosa: String = "",
    adminSave: (suspend (glosa: String, template: GestureTemplate) -> String)? = null
) {

    val isAdminMode = adminSave != null

    val context = LocalContext.current

    val calibrationStore = remember {
        CalibrationStore(context.applicationContext)
    }

    val repository = remember {
        VocabularyRepository(context.applicationContext)
    }

    val vocabularyFlow = remember(repository, isPremium) {
        repository.observe(flowOf(isPremium))
    }

    val vocabulary by vocabularyFlow.collectAsState(initial = null)
    val calibrations by calibrationStore.calibrations.collectAsState()
    val isPairReady by bluetoothManager.isPairReady.collectAsState()
    val handsByAddress by bluetoothManager.handsByAddress.collectAsState()
    val connectedHands = handsByAddress.values.toSet()

    val scope = rememberCoroutineScope()

    var glosa by remember { mutableStateOf(initialGlosa) }
    var isRest by remember { mutableStateOf(false) }
    var useLeft by remember { mutableStateOf(true) }
    var useRight by remember { mutableStateOf(true) }

    var isRecording by remember { mutableStateOf(false) }
    var samples by remember { mutableStateOf<List<PoseSample>>(emptyList()) }
    var segmenterStatus by remember { mutableStateOf<SegmenterStatus?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<CustomGesture?>(null) }

    BackHandler(onBack = onNavigateBack)

    // Manos en orden fijo: I primero, luego D.
    val selectedHands =
        Hand.entries.filter { hand ->
            when (hand) {
                Hand.LEFT -> useLeft
                Hand.RIGHT -> useRight
            }
        }

    // Marca sola las manos conectadas mientras no se haya grabado nada.
    LaunchedEffect(connectedHands) {
        if (samples.isEmpty() && !isRecording && connectedHands.isNotEmpty()) {
            useLeft = Hand.LEFT in connectedHands
            useRight = Hand.RIGHT in connectedHands
        }
    }

    val missingSelected = selectedHands.filterNot { it in connectedHands }

    // Guantes listos para grabar.
    val glovesReady =
        if (REQUIRE_BOTH_GLOVES) {
            isPairReady
        } else {
            selectedHands.isNotEmpty() && missingSelected.isEmpty()
        }

    // =========================================================
    // GRABACIÓN: SEGMENTADOR SOBRE LAS TRAMAS EN VIVO
    // =========================================================

    LaunchedEffect(isRecording) {

        if (!isRecording) {
            return@LaunchedEffect
        }

        val segmenter = StaticPoseSegmenter(selectedHands.toSet())

        bluetoothManager.sensorFrames.collect { frame ->

            val normalized =
                ImuNormalizer.normalize(
                    frame,
                    calibrationStore.get(frame.hand)
                )

            val sample = segmenter.process(normalized)

            segmenterStatus = segmenter.status

            if (sample != null) {

                samples = samples + sample

                if (samples.size >= TARGET_REPETITIONS) {
                    isRecording = false
                }
            }
        }
    }

    val repetitions =
        samples.map { it.featureVector(selectedHands) }

    val maxDeviation =
        PoseMath.maxDeviationDeg(repetitions, selectedHands.size)

    // Qué pasará con esta palabra al guardar (mismas reglas que Room).
    val savePlan =
        vocabulary?.let { v ->
            GestureRules.plan(
                glosa = glosa,
                isPremium = isPremium,
                baseLemmas = v.baseLemmas,
                overrides = v.overrides,
                customGestures = v.customGestures,
                freeLimit = SubscriptionManager.FREE_GESTURE_LIMIT
            )
        }

    // Modo administrador: basta con escribir la palabra.
    val canRecordWord =
        if (isAdminMode) glosa.isNotBlank() else savePlan?.canRecord == true

    // Señas del vocabulario que podrían confundirse con la nueva.
    val conflicts =
        vocabulary?.let { v ->
            GestureRules.findConflicts(
                glosa = glosa,
                hands = selectedHands,
                newRepetitions = repetitions,
                vocabulary = v.resolved.recognizable
            )
        }.orEmpty()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {

        Row(verticalAlignment = Alignment.CenterVertically) {

            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Volver"
                )
            }

            Text(
                text = if (isAdminMode) "Seña del diccionario base" else "Grabar señas",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Aviso si falta calibrar alguna mano elegida.
        val uncalibrated =
            selectedHands.filter { it !in calibrations.keys }

        if (uncalibrated.isNotEmpty()) {

            Text(
                text = "Falta calibrar: " +
                        uncalibrated.joinToString { it.label } +
                        ". Calibra en \"Ver sensores en vivo\" antes de grabar.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )

            Spacer(modifier = Modifier.height(8.dp))
        }

        // =====================================================
        // DATOS DE LA SEÑA
        // =====================================================

        OutlinedTextField(
            value = glosa,
            onValueChange = { glosa = it },
            label = { Text("Palabra o frase") },
            placeholder = { Text("Ej. Hola") },
            singleLine = true,
            enabled = !isRecording,
            modifier = Modifier.fillMaxWidth()
        )

        val hint =
            if (isAdminMode) {
                if (glosa.isBlank()) null
                else "Se guardará en el borrador del diccionario base. " +
                        "Si la palabra ya existe, se regraba." to false
            } else {
                planHint(savePlan)
            }

        hint?.let { (text, isError) ->

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }

        if (!glovesReady) {

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = when {
                    REQUIRE_BOTH_GLOVES -> "Conecta los dos guantes para grabar."
                    selectedHands.isEmpty() -> "Marca al menos una mano."
                    else -> "Conecta el guante de la mano " +
                            missingSelected.joinToString(" y ") { it.label.lowercase() } +
                            " o desmárcala."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {

            Checkbox(
                checked = useLeft,
                onCheckedChange = { useLeft = it },
                enabled = !isRecording && samples.isEmpty()
            )

            Text("Mano izquierda")

            Spacer(modifier = Modifier.width(16.dp))

            Checkbox(
                checked = useRight,
                onCheckedChange = { useRight = it },
                enabled = !isRecording && samples.isEmpty()
            )

            Text("Mano derecha")
        }

        Row(verticalAlignment = Alignment.CenterVertically) {

            Checkbox(
                checked = isRest,
                onCheckedChange = { isRest = it },
                enabled = !isRecording
            )

            Text("Postura de reposo (no se muestra al reconocerla)")
        }

        Spacer(modifier = Modifier.height(12.dp))

        // =====================================================
        // CONTROLES DE GRABACIÓN
        // =====================================================

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {

            Button(
                onClick = {
                    message = null
                    isRecording = !isRecording
                },
                enabled =
                    isRecording || (
                            glovesReady &&
                                    canRecordWord &&
                                    samples.size < TARGET_REPETITIONS
                            ),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    when {
                        isRecording -> "Pausar"
                        samples.isEmpty() -> "Comenzar grabación"
                        else -> "Continuar"
                    }
                )
            }

            OutlinedButton(
                onClick = { samples = samples.dropLast(1) },
                enabled = !isRecording && samples.isNotEmpty(),
                modifier = Modifier.weight(1f)
            ) {
                Text("Descartar última")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Repeticiones: ${samples.size} de $TARGET_REPETITIONS",
            style = MaterialTheme.typography.titleMedium
        )

        if (isRecording) {

            Text(
                text = statusText(segmenterStatus),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        samples.forEachIndexed { index, sample ->

            Text(
                text = "Rep ${index + 1}: " +
                        selectedHands.joinToString(" · ") { hand ->
                            val d = sample.directions.getValue(hand)
                            "${hand.code} lateral ${"%.0f".format(PoseMath.rollDeg(d))}°, " +
                                    "frontal ${"%.0f".format(PoseMath.pitchDeg(d))}°"
                        },
                style = MaterialTheme.typography.bodySmall
            )
        }

        if (samples.size >= 2 && maxDeviation > MAX_CONSISTENT_DEVIATION_DEG) {

            Text(
                text = "Una repetición se aleja ${"%.0f".format(maxDeviation)}° del resto. " +
                        "Descarta la última o graba de nuevo con la postura más pareja.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        if (conflicts.isNotEmpty()) {

            Text(
                text = "Se parece a " +
                        conflicts.joinToString { "\"${it.glosa}\" (${"%.0f".format(it.distanceDeg)}°)" } +
                        ". Pueden confundirse: cambia la orientación de la mano " +
                        "o graba de nuevo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {

            Button(
                onClick = {

                    val template =
                        GestureTemplate(
                            id = UUID.randomUUID().toString(),
                            glosa = glosa.trim(),
                            hands = selectedHands,
                            isRest = isRest,
                            repetitions = repetitions,
                            createdAtEpochMillis = System.currentTimeMillis()
                        )

                    val repetitionCount = samples.size

                    scope.launch {

                        // Etapa 8: lema del diccionario base.
                        if (adminSave != null) {

                            message =
                                try {
                                    adminSave(template.glosa, template)
                                } catch (exception: Exception) {
                                    "No se pudo guardar: " +
                                            (exception.localizedMessage ?: "error desconocido")
                                }

                            samples = emptyList()
                            glosa = ""
                            isRest = false

                            return@launch
                        }

                        when (
                            val result = repository.saveRecordedGesture(
                                glosa = template.glosa,
                                template = template,
                                isPremium = isPremium
                            )
                        ) {

                            is SaveGestureResult.Saved -> {

                                message =
                                    when {
                                        result.isOverride ->
                                            "Gesto de \"${result.glosa}\" reasignado " +
                                                    "con $repetitionCount repeticiones."

                                        result.replacedExisting ->
                                            "Seña \"${result.glosa}\" regrabada " +
                                                    "con $repetitionCount repeticiones."

                                        else ->
                                            "Seña \"${result.glosa}\" guardada " +
                                                    "con $repetitionCount repeticiones."
                                    }

                                samples = emptyList()
                                glosa = ""
                                isRest = false
                            }

                            is SaveGestureResult.Rejected -> {
                                message = result.reason
                            }
                        }
                    }
                },
                enabled =
                    !isRecording &&
                            canRecordWord &&
                            samples.size >= MIN_REPETITIONS,
                modifier = Modifier.weight(1f)
            ) {
                Text("Guardar seña")
            }

            OutlinedButton(
                onClick = {
                    isRecording = false
                    samples = emptyList()
                    message = null
                },
                enabled = samples.isNotEmpty() || isRecording,
                modifier = Modifier.weight(1f)
            ) {
                Text("Reiniciar")
            }
        }

        message?.let {

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // =====================================================
        // SEÑAS GUARDADAS (no aplica al administrador)
        // =====================================================

        if (!isAdminMode) {

            Spacer(modifier = Modifier.height(16.dp))

            HorizontalDivider()

            Spacer(modifier = Modifier.height(12.dp))

            val customGestures = vocabulary?.customGestures.orEmpty()
            val blockedIds = vocabulary?.blockedIds.orEmpty()

            Text(
                text = "Mis gestos guardados (${customGestures.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = if (isPremium) {
                    "Plan Premium: sin límite de gestos."
                } else {
                    "Plan gratuito: ${customGestures.count { it.isActive }} de " +
                            "${SubscriptionManager.FREE_GESTURE_LIMIT} gestos."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (customGestures.isEmpty()) {

                Text(
                    text = "Todavía no hay señas grabadas.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            customGestures.forEach { gesture ->

                val isBlocked = gesture.gestureId in blockedIds

                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {

                    Row(
                        modifier = Modifier.padding(start = 16.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        Column(modifier = Modifier.weight(1f)) {

                            Text(
                                text = gesture.glosa +
                                        if (gesture.template.isRest) " (reposo)" else "",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                text = "Manos: " +
                                        gesture.template.hands.joinToString { it.code.toString() } +
                                        " · ${gesture.template.repetitions.size} repeticiones" +
                                        if (isBlocked) " · bloqueado por el plan" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isBlocked) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }

                        IconButton(
                            onClick = { pendingDelete = gesture }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Eliminar ${gesture.glosa}"
                            )
                        }
                    }
                }
            }

        } // fin de "si no es administrador"
    }

    // Confirmación antes de eliminar (HU14).
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
}

// Texto que explica qué pasará con la palabra; true = es un impedimento.
private fun planHint(plan: SavePlan?): Pair<String, Boolean>? =
    when (plan) {

        null, SavePlan.Empty -> null

        SavePlan.NewCustom ->
            "Se guardará como gesto propio." to false

        is SavePlan.ReplaceCustom ->
            "Ya tienes un gesto con esta palabra: se regrabará." to false

        is SavePlan.Reassign ->
            "\"${plan.baseGlosa}\" está en el diccionario base: se guardará " +
                    "tu propio gesto para ella (Premium)." to false

        is SavePlan.NeedsPremium ->
            "\"${plan.baseGlosa}\" ya está en el diccionario base. " +
                    "Cambiar su gesto requiere HaptiVoice Premium." to true

        is SavePlan.LimitReached ->
            "Llegaste al límite de ${plan.limit} gestos del plan gratuito. " +
                    "Elimina uno o activa Premium." to true
    }

private fun statusText(status: SegmenterStatus?): String =
    when (status) {

        null -> "Esperando tramas…"

        is SegmenterStatus.WaitingForHands ->
            "Esperando tramas de: " + status.missing.joinToString { it.label }

        is SegmenterStatus.ImuMissing ->
            "El MPU de la mano ${status.hand.label} manda ceros."

        SegmenterStatus.Moving ->
            "Moviendo… haz la seña y quédate quieto."

        is SegmenterStatus.Holding ->
            "Quieto ${status.heldMillis} ms… se captura a los 400 ms."

        SegmenterStatus.WaitingForMovement ->
            "Capturada. Mueve las manos y vuelve a la postura."
    }