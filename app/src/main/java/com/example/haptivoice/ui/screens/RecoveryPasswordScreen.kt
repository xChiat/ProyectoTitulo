package com.example.haptivoice.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.haptivoice.ui.theme.HaptivoiceTheme

@Composable
fun RecoveryPasswordScreen(
    modifier: Modifier = Modifier,
    onNavigateToLogin: () -> Unit = {},
    onPasswordResetComplete: () -> Unit = {}
) {
    // Manejador de estado para cambiar entre las 3 etapas del boceto (1, 2 o 3)
    var currentStep by remember { mutableIntStateOf(1) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
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
                    text = "Restablecer contraseña",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                when (currentStep) {
                    1 -> StepOneRequestEmail(
                        onContinue = { currentStep = 2 },
                        onNavigateToLogin = onNavigateToLogin
                    )
                    2 -> StepTwoVerifyCode(
                        onVerify = { currentStep = 3 },
                        onNavigateToLogin = onNavigateToLogin
                    )
                    3 -> StepThreeNewPassword(
                        onSave = { onPasswordResetComplete() }
                    )
                }
            }
        }
    }
}

// ==========================================
// ETAPA 1: SOLICITAR CORREO
// ==========================================
@Composable
private fun StepOneRequestEmail(
    onContinue: () -> Unit,
    onNavigateToLogin: () -> Unit
) {
    var email by remember { mutableStateOf("") }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Ingresa tu correo electrónico asociado para recibir un código de recuperación.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Correo") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Continuar")
        }

        Spacer(modifier = Modifier.height(16.dp))

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(onClick = onNavigateToLogin) {
            Text("Iniciar sesión o registrarse")
        }
    }
}

// ==========================================
// ETAPA 2: INGRESAR CÓDIGO
// ==========================================
@Composable
private fun StepTwoVerifyCode(
    onVerify: () -> Unit,
    onNavigateToLogin: () -> Unit
) {
    var code by remember { mutableStateOf("") }
    val codeLength = 5 // Según el boceto hay 5 recuadros

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Ingresa el código de 5 dígitos que enviamos a tu correo.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Componente personalizado para los 5 recuadros del boceto
        BasicTextField(
            value = code,
            onValueChange = {
                if (it.length <= codeLength) {
                    code = it
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            decorationBox = {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    repeat(codeLength) { index ->
                        val char = when {
                            index >= code.length -> ""
                            else -> code[index].toString()
                        }

                        Box(
                            modifier = Modifier
                                .width(50.dp)
                                .height(56.dp)
                                .border(
                                    width = 1.dp,
                                    color = if (index == code.length) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    shape = RoundedCornerShape(8.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = char,
                                style = MaterialTheme.typography.headlineSmall
                            )
                        }
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onVerify,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Restablecer contraseña")
        }

        Spacer(modifier = Modifier.height(16.dp))

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(onClick = onNavigateToLogin) {
            Text("Iniciar sesión o registrarse")
        }
    }
}

// ==========================================
// ETAPA 3: NUEVA CONTRASEÑA
// ==========================================
@Composable
private fun StepThreeNewPassword(
    onSave: () -> Unit
) {
    var newPassword by remember { mutableStateOf("") }
    var repeatPassword by remember { mutableStateOf("") }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Crea una nueva contraseña para tu cuenta.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = newPassword,
            onValueChange = { newPassword = it },
            label = { Text("Nueva contraseña") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = repeatPassword,
            onValueChange = { repeatPassword = it },
            label = { Text("Repita la Contraseña") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Guardar nueva contraseña")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun RecoveryPasswordScreenPreview() {
    HaptivoiceTheme {
        RecoveryPasswordScreen()
    }
}