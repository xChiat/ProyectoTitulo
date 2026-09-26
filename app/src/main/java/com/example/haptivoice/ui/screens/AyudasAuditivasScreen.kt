package com.example.haptivoice.ui.screens

import android.speech.tts.Voice
import android.widget.Toast
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.haptivoice.utils.TextToSpeechManager

private const val SAMPLE_TEXT_ES =
    "Esta es una prueba larga del motor de texto a voz de HaptiVoice. " +
            "El objetivo es escuchar con claridad el timbre, la velocidad y la naturalidad " +
            "de cada voz disponible en el dispositivo. Si esta voz te parece adecuada " +
            "para el perfil femenino, masculino o neutral, copia su nombre exacto " +
            "y asígnalo después en el TextToSpeechManager."

private const val SAMPLE_TEXT_EN =
    "This is a long test of the HaptiVoice text to speech engine. " +
            "The goal is to hear the tone, speed and naturalness of each available voice. " +
            "If this voice fits the female, male or neutral profile, copy its exact name " +
            "and assign it later inside the TextToSpeechManager."

@Composable
fun AyudasAuditivasScreen(
    ttsManager: TextToSpeechManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var language by remember { mutableStateOf(TextToSpeechManager.SupportedLanguage.SPANISH) }
    var normalVoices by remember { mutableStateOf<List<Voice>>(emptyList()) }
    var highVoices by remember { mutableStateOf<List<Voice>>(emptyList()) }

    var isReady by remember { mutableStateOf(ttsManager.isReady()) }
    var allVoices by remember { mutableStateOf<List<Voice>>(emptyList()) }

    fun reloadVoices() {
        allVoices = ttsManager.getAllVoices()
        normalVoices = ttsManager.getVoicesQualityNormal()
        highVoices = ttsManager.getVoicesQualityVeryHigh()
    }

    DisposableEffect(ttsManager) {
        ttsManager.onInitResult = { ok ->
            isReady = ok
            if (ok) reloadVoices()
        }
        if (ttsManager.isReady()) {
            isReady = true
            reloadVoices()
        }
        onDispose { ttsManager.stop() }
    }

    LaunchedEffect(isReady, language) {
        if (isReady) {
            ttsManager.setLanguage(language)
            reloadVoices()
        }
    }

    val sampleText =
        if (language == TextToSpeechManager.SupportedLanguage.SPANISH) {
            SAMPLE_TEXT_ES
        } else {
            SAMPLE_TEXT_EN
        }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 16.dp,
            bottom = 96.dp
        )
    ) {
        item {
            Text(
                text = "Ayudas auditivas (temporal)",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isReady) "TTS listo" else "Inicializando TTS...",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row {
                Button(
                    onClick = {
                        language = TextToSpeechManager.SupportedLanguage.SPANISH
                    }
                ) {
                    Text("Español")
                }
                Spacer(modifier = Modifier.padding(8.dp))
                OutlinedButton(
                    onClick = {
                        language = TextToSpeechManager.SupportedLanguage.ENGLISH
                    }
                ) {
                    Text("English")
                }
                Spacer(modifier = Modifier.padding(8.dp))
                IconButton(
                    onClick = { ttsManager.stop() }
                ) {
                    Icon(Icons.Default.Stop, contentDescription = "Detener")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "TTS voces crudas=${allVoices.size}  NORMAL=${normalVoices.size}  HIGH=${highVoices.size}",
                style = MaterialTheme.typography.labelMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            Text(
                text = "QUALITY_NORMAL",
                style = MaterialTheme.typography.titleMedium
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }

        items(normalVoices, key = { "n-${it.name}" }) { voice ->
            VoiceRow(
                voice = voice,
                onPlay = {
                    ttsManager.setVoice(voice)
                    ttsManager.speak(sampleText)
                },
                onCopy = {
                    clipboard.setText(AnnotatedString(voice.name))
                    Toast.makeText(context, "Copiado: ${voice.name}", Toast.LENGTH_SHORT).show()
                }
            )
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "QUALITY_VERY_HIGH",
                style = MaterialTheme.typography.titleMedium
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }

        items(highVoices, key = { "h-${it.name}" }) { voice ->
            VoiceRow(
                voice = voice,
                onPlay = {
                    ttsManager.setVoice(voice)
                    ttsManager.speak(sampleText)
                },
                onCopy = {
                    clipboard.setText(AnnotatedString(voice.name))
                    Toast.makeText(context, "Copiado: ${voice.name}", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

@Composable
private fun VoiceRow(
    voice: Voice,
    onPlay: () -> Unit,
    onCopy: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = voice.name,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "${voice.locale} | q=${voice.quality} | net=${voice.isNetworkConnectionRequired}",
                style = MaterialTheme.typography.labelSmall
            )
        }
        IconButton(onClick = onPlay) {
            Icon(Icons.Default.VolumeUp, contentDescription = "Reproducir")
        }
        IconButton(onClick = onCopy) {
            Icon(Icons.Default.ContentCopy, contentDescription = "Copiar nombre")
        }
    }
}