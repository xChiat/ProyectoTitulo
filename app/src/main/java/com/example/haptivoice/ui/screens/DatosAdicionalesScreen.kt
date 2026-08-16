package com.example.haptivoice.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.haptivoice.ui.theme.HaptivoiceTheme
import com.example.haptivoice.utils.ValidationResult
import com.example.haptivoice.utils.ValidationUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatosAdicionalesScreen(
    modifier: Modifier = Modifier,
    onSave: (username: String, disability: String, voiceType: String) -> Unit = { _, _, _ -> }
) {
    var username by remember { mutableStateOf("") }
    var selectedDisability by remember { mutableIntStateOf(2) } // Default to "Ninguna"
    var selectedVoiceType by remember { mutableIntStateOf(0) }
    var usernameError by remember { mutableStateOf<String?>(null) }

    val disabilityOptions = listOf("Auditiva", "Verbal", "Ninguna")
    val voiceTypeOptions = listOf("Femenina", "Masculino", "Neutral")

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Datos adicionales",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Nombre de usuario
                OutlinedTextField(
                    value = username,
                    onValueChange = { 
                        username = it
                        if (usernameError != null) usernameError = null 
                    },
                    label = { Text("Nombre de usuario") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = usernameError != null,
                    supportingText = {
                        if (usernameError != null) {
                            Text(usernameError!!)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // ¿Presento alguna discapacidad?
                Text(
                    text = "¿Presenta alguna discapacidad?",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Segmented button para discapacidad
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    disabilityOptions.forEachIndexed { index, label ->
                        SegmentedButton(
                            selected = selectedDisability == index,
                            onClick = { selectedDisability = index },
                            shape = RoundedCornerShape(
                                topStart = if (index == 0) 12.dp else 0.dp,
                                bottomStart = if (index == 0) 12.dp else 0.dp,
                                topEnd = if (index == disabilityOptions.size - 1) 12.dp else 0.dp,
                                bottomEnd = if (index == disabilityOptions.size - 1) 12.dp else 0.dp
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(label, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Tipo de voz sintética
                Text(
                    text = "Tipo de voz sintética",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Segmented button para tipo de voz
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    voiceTypeOptions.forEachIndexed { index, label ->
                        SegmentedButton(
                            selected = selectedVoiceType == index,
                            onClick = { selectedVoiceType = index },
                            shape = RoundedCornerShape(
                                topStart = if (index == 0) 12.dp else 0.dp,
                                bottomStart = if (index == 0) 12.dp else 0.dp,
                                topEnd = if (index == voiceTypeOptions.size - 1) 12.dp else 0.dp,
                                bottomEnd = if (index == voiceTypeOptions.size - 1) 12.dp else 0.dp
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(label, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Botón Guardar Datos adicionales
                Button(
                    onClick = {
                        val validation = ValidationUtils.validateUsername(username)
                        if (validation is ValidationResult.Valid) {
                            onSave(
                                username,
                                disabilityOptions[selectedDisability],
                                voiceTypeOptions[selectedVoiceType]
                            )
                        } else {
                            usernameError = when (validation) {
                                is ValidationResult.Empty -> validation.message
                                is ValidationResult.Invalid -> validation.message
                                else -> null
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(text = "Guardar Datos adicionales")
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DatosAdicionalesScreenPreview() {
    HaptivoiceTheme {
        DatosAdicionalesScreen()
    }
}
