package com.example.haptivoice.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun RecoveryPasswordScreen(
    modifier: Modifier = Modifier,
    currentStep: Int,
    isLoading: Boolean,
    onSendEmail: (String) -> Unit,
    onVerifyCode: (String) -> Unit,
    onResetPassword: (String) -> Unit,
    onNavigateToLogin: () -> Unit
) {
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
                        isLoading = isLoading,
                        onContinue = onSendEmail,
                        onNavigateToLogin = onNavigateToLogin
                    )
                    2 -> StepTwoVerifyCode(
                        isLoading = isLoading,
                        onVerify = onVerifyCode,
                        onNavigateToLogin = onNavigateToLogin
                    )
                    3 -> StepThreeNewPassword(
                        isLoading = isLoading,
                        onSave = onResetPassword
                    )
                }
            }
        }
    }
}

@Composable
private fun StepOneRequestEmail(
    isLoading: Boolean,
    onContinue: (String) -> Unit,
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
            enabled = !isLoading,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { onContinue(email) },
            enabled = email.isNotBlank() && !isLoading,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
            else Text("Continuar")
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(modifier = Modifier.height(8.dp))

        TextButton(onClick = onNavigateToLogin, enabled = !isLoading) {
            Text("Iniciar sesión o registrarse")
        }
    }
}

@Composable
private fun StepTwoVerifyCode(
    isLoading: Boolean,
    onVerify: (String) -> Unit,
    onNavigateToLogin: () -> Unit
) {
    var code by remember { mutableStateOf("") }
    val codeLength = 5

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Ingresa el código de 5 dígitos que enviamos a tu correo.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        BasicTextField(
            value = code,
            onValueChange = { if (it.length <= codeLength) code = it },
            enabled = !isLoading,
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
                            Text(text = char, style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { onVerify(code) },
            enabled = code.length == codeLength && !isLoading,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
            else Text("Restablecer contraseña")
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(modifier = Modifier.height(8.dp))

        TextButton(onClick = onNavigateToLogin, enabled = !isLoading) {
            Text("Iniciar sesión o registrarse")
        }
    }
}

@Composable
private fun StepThreeNewPassword(
    isLoading: Boolean,
    onSave: (String) -> Unit
) {
    var newPassword by remember { mutableStateOf("") }
    var repeatPassword by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

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
            onValueChange = {
                newPassword = it
                errorText = null
            },
            label = { Text("Nueva contraseña") },
            singleLine = true,
            enabled = !isLoading,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = repeatPassword,
            onValueChange = {
                repeatPassword = it
                errorText = null
            },
            label = { Text("Repita la Contraseña") },
            singleLine = true,
            enabled = !isLoading,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
            isError = errorText != null,
            supportingText = {
                if (errorText != null) {
                    Text(text = errorText!!, color = MaterialTheme.colorScheme.error)
                }
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (newPassword == repeatPassword && newPassword.length >= 8) {
                    onSave(newPassword)
                } else if (newPassword.length < 8) {
                    errorText = "La contraseña debe tener al menos 8 caracteres"
                } else {
                    errorText = "Las contraseñas no coinciden"
                }
            },
            enabled = newPassword.isNotBlank() && repeatPassword.isNotBlank() && !isLoading,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
            else Text("Guardar nueva contraseña")
        }
    }
}