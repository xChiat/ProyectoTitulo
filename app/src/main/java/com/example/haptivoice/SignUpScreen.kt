package com.example.haptivoice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.haptivoice.ui.theme.HaptivoiceTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignUpScreen(
    modifier: Modifier = Modifier,
    onSignUp: (username: String, email: String, password: String, disability: String, voiceType: String) -> Unit = { _, _, _, _, _ -> },
    onGoogleSignUp: () -> Unit = {},
    onLoginClick: () -> Unit = {}
) {
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
                    text = "Registrarse",
                    style = MaterialTheme.typography.headlineSmall
                )

                Spacer(modifier = Modifier.height(24.dp))

                var username by remember { mutableStateOf("") }
                var email by remember { mutableStateOf("") }
                var password by remember { mutableStateOf("") }
                var selectedDisability by remember { mutableIntStateOf(0) }
                var selectedVoiceType by remember { mutableIntStateOf(0) }

                val disabilityOptions = listOf("Auditiva", "Verbal", "Ninguna")
                val voiceTypeOptions = listOf("Femenina", "Masculino", "Neutral")

                // Nombre de usuario
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Nombre de usuario") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Correo
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Correo electrónico") },
                    leadingIcon = { Icon(Icons.Default.MailOutline, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Contraseña
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Contraseña") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                )

                Spacer(modifier = Modifier.height(24.dp))

                // ¿Presento alguna discapacidad?
                Text(
                    text = "¿Presento alguna discapacidad?",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth()
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
                            Text(label)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Tipo de voz sintética
                Text(
                    text = "Tipo de voz sintética",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth()
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
                            Text(label)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Botón Registrarse
                Button(
                    onClick = {
                        onSignUp(
                            username,
                            email,
                            password,
                            disabilityOptions[selectedDisability],
                            voiceTypeOptions[selectedVoiceType]
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(text = "Registrarse")
                }

                Spacer(modifier = Modifier.height(16.dp))

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Botón Ingresar con Google
                OutlinedButton(
                    onClick = onGoogleSignUp,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(text = "Registrarse con Google")
                }

                Spacer(modifier = Modifier.height(8.dp))

                // TextButton para login
                TextButton(onClick = onLoginClick) {
                    Text(text = "¿Ya tienes cuenta? Inicia sesión")
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SignUpScreenPreview() {
    HaptivoiceTheme {
        SignUpScreen()
    }
}

