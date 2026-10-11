package com.example.haptivoice.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.haptivoice.utils.SpeechToTextManager
import com.example.haptivoice.utils.TextToSpeechManager

@Composable
fun AyudasAuditivasScreen(
    ttsManager: TextToSpeechManager,
    modifier: Modifier = Modifier
) {

    val context = LocalContext.current

    // Texto escrito para reproducir
    var writtenText by remember { mutableStateOf("") }

    // Texto obtenido desde el micrófono
    var recognizedText by remember { mutableStateOf("") }

    var isListening by remember { mutableStateOf(false) }
    var recognitionMessage by remember { mutableStateOf("") }

    val speechManager = remember(context) {
        SpeechToTextManager(context.applicationContext)
    }

    // Funciones para iniciar y detener el reconocimiento
    fun startRecognition() {
        recognitionMessage = "Escuchando..."
        isListening = true

        speechManager.startListening(
            language = "es-CL",
            onResult = { text ->
                recognizedText = text
                recognitionMessage = ""
                isListening = false
            },
            onError = { error ->
                recognitionMessage = error
                isListening = false
            }
        )
    }

    fun stopRecognition() {
        speechManager.stopListening()
        isListening = false
        recognitionMessage = "Procesando reconocimiento..."
    }

    // Solicitar permiso del micrófono
    val microphonePermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                startRecognition()
            } else {
                recognitionMessage = "Se necesita permiso para utilizar el micrófono"
            }
        }

    // Liberar el reconocedor al salir de la pantalla
    DisposableEffect(speechManager) {
        onDispose {
            speechManager.destroy()
            ttsManager.stop()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            text = "Ayudas Auditivas",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(24.dp))

        // =====================================================
        // TEXTO A VOZ
        // =====================================================

        Text(
            text = "Texto a voz",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = writtenText,
            onValueChange = { writtenText = it },
            label = { Text("Escribe un mensaje") },
            placeholder = { Text("Escribe aquí lo que quieres reproducir...") },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 140.dp),
            minLines = 4
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                ttsManager.speak(writtenText)
            },
            enabled = writtenText.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.VolumeUp,
                contentDescription = null
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text("Reproducir texto")
        }

        Spacer(modifier = Modifier.height(32.dp))

        HorizontalDivider()

        Spacer(modifier = Modifier.height(32.dp))

        // =====================================================
        // VOZ A TEXTO
        // =====================================================

        Text(
            text = "Voz a texto",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                if (isListening) {
                    stopRecognition()
                } else {

                    val hasPermission =
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED

                    if (hasPermission) {
                        startRecognition()
                    } else {
                        microphonePermissionLauncher.launch(
                            Manifest.permission.RECORD_AUDIO
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {

            Icon(
                imageVector =
                    if (isListening) {
                        Icons.Default.Stop
                    } else {
                        Icons.Default.Mic
                    },
                contentDescription = null
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                if (isListening) {
                    "Detener reconocimiento"
                } else {
                    "Iniciar reconocimiento"
                }
            )
        }

        if (recognitionMessage.isNotBlank()) {

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = recognitionMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        OutlinedCard(
            modifier = Modifier.fillMaxWidth()
        ) {

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {

                Text(
                    text = "Texto reconocido",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = recognizedText.ifBlank {
                        "Aquí aparecerá el texto reconocido desde el micrófono."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}