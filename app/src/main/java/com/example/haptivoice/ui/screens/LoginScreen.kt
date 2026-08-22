package com.example.haptivoice.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.example.haptivoice.ui.theme.HaptivoiceTheme
import com.example.haptivoice.ui.theme.bg
import com.example.haptivoice.ui.theme.darker_txt
import com.example.haptivoice.ui.theme.icons
import com.example.haptivoice.ui.theme.primary_btn
import com.example.haptivoice.ui.theme.pure_white
import com.example.haptivoice.ui.theme.secondary_btn
import com.example.haptivoice.ui.theme.txt
import com.example.haptivoice.ui.theme.unfocused_field
import com.example.haptivoice.utils.ValidationResult
import com.example.haptivoice.utils.ValidationUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    modifier: Modifier = Modifier,
    onLogin: (email: String, password: String) -> Unit = { _, _ -> },
    onGoogleSignIn: () -> Unit = {},
    onRegister: () -> Unit = {},
    onPasswordRecovery: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(bg)
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.outlinedCardColors(
                containerColor = pure_white
            ),
            border = BorderStroke(
                width = 1.dp,
                color = icons
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Iniciar sesión",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    color = darker_txt
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Introduce tus credenciales para continuar",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = txt
                )

                Spacer(modifier = Modifier.height(16.dp))

                var email by remember { mutableStateOf("") }
                var password by remember { mutableStateOf("") }
                var emailError by remember { mutableStateOf<String?>(null) }
                var passwordError by remember { mutableStateOf<String?>(null) }

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Correo electrónico") },
                    leadingIcon = { Icon(Icons.Default.MailOutline, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = emailError != null,
                    supportingText = {
                        if (emailError != null) {
                            Text(emailError!!)
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = primary_btn,
                        unfocusedBorderColor = unfocused_field,
                        errorBorderColor = secondary_btn,

                        focusedLabelColor = primary_btn,
                        unfocusedLabelColor = unfocused_field,
                        errorLabelColor = secondary_btn,

                        focusedLeadingIconColor = primary_btn,
                        unfocusedLeadingIconColor = unfocused_field,
                        errorLeadingIconColor = secondary_btn,

                        focusedSupportingTextColor = primary_btn,
                        unfocusedSupportingTextColor = unfocused_field,
                        errorSupportingTextColor = secondary_btn
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Contraseña") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    isError = passwordError != null,
                    supportingText = {
                        if (passwordError != null) {
                            Text(passwordError!!)
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = primary_btn,
                        unfocusedBorderColor = unfocused_field,
                        errorBorderColor = secondary_btn,

                        focusedLabelColor = primary_btn,
                        unfocusedLabelColor = unfocused_field,
                        errorLabelColor = secondary_btn,

                        focusedLeadingIconColor = primary_btn,
                        unfocusedLeadingIconColor = unfocused_field,
                        errorLeadingIconColor = secondary_btn,

                        focusedSupportingTextColor = primary_btn,
                        unfocusedSupportingTextColor = unfocused_field,
                        errorSupportingTextColor = secondary_btn
                    )
                )

                TextButton(onClick = onPasswordRecovery) {
                    Text(
                        text = "¿Olvidaste tu contraseña?",
                        color = primary_btn
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = {
                        // Limpiar errores previos
                        emailError = null
                        passwordError = null

                        // Validar cada campo individualmente
                        val emailValidation = ValidationUtils.validateEmail(email)
                        if (emailValidation !is ValidationResult.Valid) {
                            emailError = when (emailValidation) {
                                is ValidationResult.Empty -> emailValidation.message
                                is ValidationResult.Invalid -> emailValidation.message
                                else -> null
                            }
                        }

                        val passwordValidation = ValidationUtils.validatePassword(password)
                        if (passwordValidation !is ValidationResult.Valid) {
                            passwordError = when (passwordValidation) {
                                is ValidationResult.Empty -> passwordValidation.message
                                is ValidationResult.Invalid -> passwordValidation.message
                                else -> null
                            }
                        }

                        // Si todas las validaciones son válidas, ejecutar el login
                        if (emailError == null && passwordError == null) {
                            onLogin(email, password)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = primary_btn,
                        contentColor = pure_white
                    )
                ) {
                    Text(text = "Iniciar sesión")
                }

                Spacer(modifier = Modifier.height(16.dp))

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    thickness = 1.dp,
                    color = icons
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(
                    onClick = onGoogleSignIn,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = pure_white
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        color = primary_btn
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = primary_btn
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Iniciar sesión con Google",
                        color = primary_btn
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(onClick = onRegister) {
                    Text(
                        text = "¿No tienes cuenta? Regístrate",
                        color = primary_btn
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LoginScreenPreview() {
    HaptivoiceTheme {
        LoginScreen()
    }
}

