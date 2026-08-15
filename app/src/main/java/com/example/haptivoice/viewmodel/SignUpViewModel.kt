package com.example.haptivoice.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.haptivoice.model.UserData
import com.example.haptivoice.utils.ValidationResult
import com.example.haptivoice.utils.ValidationUtils
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class SignUpUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val success: Boolean = false
)

class SignUpViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private val _uiState = MutableStateFlow(SignUpUiState())
    val uiState: StateFlow<SignUpUiState> = _uiState.asStateFlow()

    fun signUp(
        username: String,
        email: String,
        password: String,
        disability: String,
        voiceType: String
    ) {
        // Validar todos los campos
        val validationResult = ValidationUtils.validateAllFields(username, email, password)
        if (validationResult !is ValidationResult.Valid) {
            val errorMessage = when (validationResult) {
                is ValidationResult.Empty -> validationResult.message
                is ValidationResult.Invalid -> validationResult.message
                else -> "Error de validación"
            }
            _uiState.value = SignUpUiState(error = errorMessage)
            return
        }

        _uiState.value = SignUpUiState(isLoading = true)

        viewModelScope.launch {
            try {
                // 1. Verificar que el email no exista en Firestore
                val userExists = firestore.collection("users")
                    .whereEqualTo("email", email)
                    .get()
                    .await()
                    .isEmpty
                    .not()

                if (userExists) {
                    _uiState.value = SignUpUiState(
                        isLoading = false,
                        error = "Ya existe un usuario registrado con este correo"
                    )
                    return@launch
                }

                // 2. Crear usuario en Firebase Auth
                val authResult = auth.createUserWithEmailAndPassword(email, password).await()
                val userId = authResult.user?.uid

                if (userId == null) {
                    _uiState.value = SignUpUiState(
                        isLoading = false,
                        error = "Error al crear la cuenta"
                    )
                    return@launch
                }

                // 3. Guardar datos en Firestore usando la clase UserData
                val userData = UserData(
                    userId = userId,
                    username = username,
                    email = email,
                    disability = disability,
                    voiceType = voiceType,
                    createdAt = Timestamp.now()
                )

                firestore.collection("users").document(userId).set(userData).await()

                _uiState.value = SignUpUiState(success = true)

            } catch (e: Exception) {
                val errorMessage = when {
                    e.message?.contains("email address is already in use") == true ->
                        "Este correo electrónico ya está registrado"
                    e.message?.contains("password") == true ->
                        "La contraseña no cumple con los requisitos de seguridad"
                    e.message?.contains("network") == true ->
                        "Error de conexión. Verifica tu conexión a internet"
                    e.message?.contains("Queries with != filters") == true ->
                        "Error en la consulta. Por favor, intenta de nuevo"
                    else -> e.message ?: "Error desconocido durante el registro"
                }
                _uiState.value = SignUpUiState(isLoading = false, error = errorMessage)
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

