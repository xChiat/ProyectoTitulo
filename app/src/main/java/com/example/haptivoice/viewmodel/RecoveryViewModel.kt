package com.example.haptivoice.viewmodel

import androidx.lifecycle.ViewModel
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class RecoveryState {
    object Idle : RecoveryState()
    object Loading : RecoveryState()
    object EmailSent : RecoveryState()
    object CodeVerified : RecoveryState()
    object Success : RecoveryState()
    data class Error(val message: String) : RecoveryState()
}

class RecoveryViewModel : ViewModel() {
    private val functions = FirebaseFunctions.getInstance()

    private val _uiState = MutableStateFlow<RecoveryState>(RecoveryState.Idle)
    val uiState: StateFlow<RecoveryState> = _uiState.asStateFlow()

    var savedEmail: String = ""
        private set
    var savedOtp: String = ""
        private set


    fun sendRecoveryEmail(email: String) {
        _uiState.value = RecoveryState.Loading
        savedEmail = email

        val data = hashMapOf("email" to email)

        functions.getHttpsCallable("sendRecoveryOtp")
            .call(data)
            .addOnSuccessListener {
                _uiState.value = RecoveryState.EmailSent
            }
            .addOnFailureListener { e ->
                _uiState.value = RecoveryState.Error(e.message ?: "Error al enviar el correo")
            }
    }

    fun verifyCodeLocally(code: String) {
        _uiState.value = RecoveryState.Loading

        val data = hashMapOf(
            "email" to savedEmail,
            "otp" to code
        )

        functions.getHttpsCallable("verifyRecoveryOtp")
            .call(data)
            .addOnSuccessListener {
                savedOtp = code
                _uiState.value = RecoveryState.CodeVerified
            }
            .addOnFailureListener { e ->
                _uiState.value = RecoveryState.Error(e.message ?: "Error al verificar el código")
            }
    }

    fun resetPassword(newPassword: String) {
        _uiState.value = RecoveryState.Loading

        val data = hashMapOf(
            "email" to savedEmail,
            "otp" to savedOtp,
            "newPassword" to newPassword
        )

        functions.getHttpsCallable("resetPasswordWithOtp")
            .call(data)
            .addOnSuccessListener {
                _uiState.value = RecoveryState.Success
            }
            .addOnFailureListener { e ->
                _uiState.value = RecoveryState.Error(e.message ?: "Error al cambiar contraseña")
            }
    }

    fun resetState() {
        _uiState.value = RecoveryState.Idle
    }
}