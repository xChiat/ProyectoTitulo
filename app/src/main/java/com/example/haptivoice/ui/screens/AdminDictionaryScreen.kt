package com.example.haptivoice.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.example.haptivoice.data.admin.AdminDictionaryRepository
import com.example.haptivoice.data.admin.AdminLemma
import com.example.haptivoice.data.sync.VocabularySyncScheduler
import com.example.haptivoice.utils.BluetoothManager
import kotlinx.coroutines.launch

// =============================================================
// ADMINISTRACIÓN DEL DICCIONARIO BASE (ETAPA 8, CU-012)
// =============================================================

/*
 * Solo se abre si el usuario tiene el custom claim admin: true
 * (además, las reglas y publishDictionary lo vuelven a revisar).
 *
 * - "Nueva seña" graba un lema con los dos guantes.
 * - El lápiz regraba un lema existente.
 * - El interruptor activa o desactiva sin borrar (RF-26).
 * - "Publicar" crea una versión nueva con los lemas activos;
 *   los teléfonos la descargan al abrir la app con red.
 *
 * Mientras no se publique, los cambios quedan en el borrador
 * y ningún teléfono los ve.
 */
@Composable
fun AdminDictionaryScreen(
    bluetoothManager: BluetoothManager,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val repository = remember { AdminDictionaryRepository() }

    val lemmasFlow = remember(repository) { repository.observeLemmas() }
    val versionFlow = remember(repository) { repository.observePublishedVersion() }

    val lemmas by lemmasFlow.collectAsState(initial = null)
    val published by versionFlow.collectAsState(initial = null)

    // Palabra a grabar: "" = lema nuevo, null = no se está grabando.
    var recordGlosa by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var isPublishing by remember { mutableStateOf(false) }
    var confirmPublish by remember { mutableStateOf(false) }

    BackHandler(onBack = onNavigateBack)

    // =========================================================
    // GRABAR UN LEMA
    // =========================================================

    val glosaToRecord = recordGlosa

    if (glosaToRecord != null) {

        GestureRecordScreen(
            bluetoothManager = bluetoothManager,
            isPremium = true,
            onNavigateBack = { recordGlosa = null },
            modifier = modifier,
            initialGlosa = glosaToRecord,
            adminSave = { glosa, template ->
                repository.saveLemma(glosa, template)
            }
        )

        return
    }

    val allLemmas = lemmas.orEmpty()
    val activeCount = allLemmas.count { it.isActive }

    // Hay cambios en el borrador posteriores a la última publicación.
    val hasDraftChanges =
        allLemmas.any { it.updatedAtMillis > (published?.publishedAtMillis ?: 0L) }

    Column(
        modifier = modifier
            .fillMaxSize()
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
                text = "Administrar diccionario",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = published?.let { "Versión publicada: ${it.versionId}" }
                ?: "Todavía no hay versiones publicadas.",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            text = "Borrador: $activeCount activos de ${allLemmas.size}" +
                    if (hasDraftChanges) " · hay cambios sin publicar" else "",
            style = MaterialTheme.typography.bodySmall,
            color = if (hasDraftChanges) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {

            OutlinedButton(
                onClick = {
                    message = null
                    recordGlosa = ""
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Nueva seña")
            }

            Button(
                onClick = { confirmPublish = true },
                enabled = !isPublishing && lemmas != null,
                modifier = Modifier.weight(1f)
            ) {
                Text(if (isPublishing) "Publicando…" else "Publicar")
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

        Spacer(modifier = Modifier.height(8.dp))

        if (lemmas == null) {

            Text(
                text = "Cargando lemas…",
                style = MaterialTheme.typography.bodyMedium
            )

        } else if (allLemmas.isEmpty()) {

            Text(
                text = "El diccionario base está vacío. Toca \"Nueva seña\" " +
                        "y graba la primera con los dos guantes.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        LazyColumn(
            contentPadding = PaddingValues(vertical = 8.dp),
            modifier = Modifier.fillMaxSize()
        ) {

            items(allLemmas, key = { it.lemmaId }) { lemma ->

                AdminLemmaRow(
                    lemma = lemma,
                    onRecord = {
                        message = null
                        recordGlosa = lemma.glosa
                    },
                    onActiveChange = { active ->
                        scope.launch {
                            message =
                                try {
                                    repository.setActive(lemma.lemmaId, active)
                                    if (active) {
                                        "\"${lemma.glosa}\" activada en el borrador."
                                    } else {
                                        "\"${lemma.glosa}\" desactivada en el borrador."
                                    }
                                } catch (exception: Exception) {
                                    "No se pudo cambiar: " +
                                            (exception.localizedMessage ?: "error desconocido")
                                }
                        }
                    }
                )
            }
        }
    }

    // Confirmación antes de publicar.
    if (confirmPublish) {

        AlertDialog(
            onDismissRequest = { confirmPublish = false },
            title = { Text("Publicar diccionario") },
            text = {
                Text(
                    "Se publicará una versión nueva con $activeCount lemas activos. " +
                            "Todos los teléfonos la descargarán al abrir la app con red."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {

                        confirmPublish = false
                        isPublishing = true

                        scope.launch {

                            message =
                                try {
                                    val (version, count) = repository.publish()

                                    // Este teléfono también descarga la versión nueva.
                                    VocabularySyncScheduler.request(context)

                                    "Versión v$version publicada con $count lemas."
                                } catch (exception: Exception) {
                                    "No se pudo publicar: " +
                                            (exception.localizedMessage ?: "error desconocido")
                                }

                            isPublishing = false
                        }
                    }
                ) {
                    Text("Publicar")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmPublish = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun AdminLemmaRow(
    lemma: AdminLemma,
    onRecord: () -> Unit,
    onActiveChange: (Boolean) -> Unit
) {

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {

        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Column(modifier = Modifier.weight(1f)) {

                Text(
                    text = lemma.glosa +
                            if (lemma.template.isRest) " (reposo)" else "",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = (if (lemma.isActive) "Activo" else "Desactivado") +
                            " · manos " +
                            lemma.template.hands.joinToString { it.code.toString() } +
                            " · ${lemma.template.repetitions.size} repeticiones",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (lemma.isActive) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            }

            IconButton(onClick = onRecord) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Regrabar ${lemma.glosa}"
                )
            }

            Switch(
                checked = lemma.isActive,
                onCheckedChange = onActiveChange
            )
        }
    }
}