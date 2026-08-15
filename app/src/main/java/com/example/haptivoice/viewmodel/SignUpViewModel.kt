package com.example.haptivoice.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.haptivoice.model.UserData
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
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
    val uiState = _uiState

    fun signUp(
        username: String,
        email: String,
        password: String,
        disability: String,
        voiceType: String
    ) {
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
                    Log.d("SignUpViewModel", "El correo electrónico ya está registrado en Firestore")
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

                Log.d("SignUpViewModel", "Datos del usuario guardados en Firestore")

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
                Log.d("SignUpViewModel", "Error durante el registro: $errorMessage")
                _uiState.value = SignUpUiState(isLoading = false, error = errorMessage)
            }
        }
    }
}

