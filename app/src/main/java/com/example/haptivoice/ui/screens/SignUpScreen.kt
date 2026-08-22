package com.example.haptivoice.ui.screens

import androidx.compose.foundation.layout.Arrangement
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.haptivoice.ui.theme.HaptivoiceTheme
import com.example.haptivoice.ui.theme.bg
import com.example.haptivoice.ui.theme.darker_bg
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
fun SignUpScreen(
    modifier: Modifier = Modifier,
    onSignUp: (username: String, email: String, password: String, disability: String, voiceType: String) -> Unit = { _, _, _, _, _ -> },
    onGoogleSignUp: () -> Unit = {},
    onLoginClick: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(bg)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
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
                    .padding(vertical = 20.dp, horizontal = 24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "Registrarse",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    color = darker_txt
                )

                Spacer(modifier = Modifier.height(8.dp))

                var username by remember { mutableStateOf("") }
                var email by remember { mutableStateOf("") }
                var password by remember { mutableStateOf("") }
                var selectedDisability by remember { mutableIntStateOf(0) }
                var selectedVoiceType by remember { mutableIntStateOf(0) }

                var usernameError by remember { mutableStateOf<String?>(null) }
                var emailError by remember { mutableStateOf<String?>(null) }
                var passwordError by remember { mutableStateOf<String?>(null) }

                val disabilityOptions = listOf("Auditiva", "Verbal", "Ninguna")
                val voiceTypeOptions = listOf("Femenina", "Masculino", "Neutral")

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Nombre de usuario") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = usernameError != null,
                    supportingText = {
                        if (usernameError != null) {
                            Text(usernameError!!)
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

                Spacer(modifier = Modifier.height(4.dp))

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

                Spacer(modifier = Modifier.height(4.dp))

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

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "¿Presenta alguna discapacidad?",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(),
                    color = txt
                )

                Spacer(modifier = Modifier.height(8.dp))

                val segmentedColors = SegmentedButtonDefaults.colors(
                    activeContainerColor = primary_btn,
                    activeContentColor = pure_white,
                    activeBorderColor = darker_bg,

                    inactiveContainerColor = pure_white,
                    inactiveContentColor = primary_btn,
                    inactiveBorderColor = primary_btn
                )

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
                            icon = {},
                            colors = segmentedColors,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(label)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Tipo de voz sintética",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(),
                    color = txt
                )

                Spacer(modifier = Modifier.height(8.dp))

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
                            icon = {},
                            colors = segmentedColors,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(label)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        usernameError = null
                        emailError = null
                        passwordError = null

                        val usernameValidation = ValidationUtils.validateUsername(username)
                        if (usernameValidation !is ValidationResult.Valid) {
                            usernameError = when (usernameValidation) {
                                is ValidationResult.Empty -> usernameValidation.message
                                is ValidationResult.Invalid -> usernameValidation.message
                                else -> null
                            }
                        }

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

                        if (usernameError == null && emailError == null && passwordError == null) {
                            Log.d("SignUpScreen-Debug", "se llamo al onSignUp")
                            onSignUp(
                                username,
                                email,
                                password,
                                disabilityOptions[selectedDisability],
                                voiceTypeOptions[selectedVoiceType]
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = primary_btn,
                        contentColor = pure_white
                    )
                ) {
                    Text(text = "Registrarse")
                }

                Spacer(modifier = Modifier.height(12.dp))

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    thickness = 1.dp,
                    color = icons
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = onGoogleSignUp,
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

                    Spacer(modifier = Modifier.size(8.dp))

                    Text(
                        text = "Registrarse con Google",
                        color = primary_btn
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                TextButton(onClick = onLoginClick) {
                    Text(
                        text = "¿Ya tienes cuenta? Inicia sesión",
                        color = primary_btn
                    )
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

