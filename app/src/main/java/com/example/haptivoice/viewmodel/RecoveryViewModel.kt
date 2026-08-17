package com.example.haptivoice.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Date

sealed class RecoveryState {
    object Idle : RecoveryState()
    object Loading : RecoveryState()
    object EmailSent : RecoveryState() // Avanza al paso 2
    object CodeVerified : RecoveryState() // Avanza al paso 3
    object Success : RecoveryState() // Contraseña cambiada
    data class Error(val message: String) : RecoveryState()
}

class RecoveryViewModel : ViewModel() {
    private val functions = FirebaseFunctions.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

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

        viewModelScope.launch {
            try {
                val doc = firestore.collection("password_resets").document(savedEmail).get().await()
                if (doc.exists()) {
                    val correctOtp = doc.getString("otp")
                    val expiresAt = doc.getTimestamp("expiresAt")?.toDate()

                    if (correctOtp == code) {
                        if (expiresAt != null && Date().before(expiresAt)) {
                            savedOtp = code // Guardamos el código para enviarlo en el paso 3
                            _uiState.value = RecoveryState.CodeVerified
                        } else {
                            _uiState.value = RecoveryState.Error("El código ha expirado")
                        }
                    } else {
                        _uiState.value = RecoveryState.Error("Código incorrecto")
                    }
                } else {
                    _uiState.value = RecoveryState.Error("No hay solicitud pendiente")
                }
            } catch (e: Exception) {
                _uiState.value = RecoveryState.Error("Error al verificar: ${e.message}")
            }
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