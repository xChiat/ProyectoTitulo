package com.example.haptivoice.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.haptivoice.model.AuthState
import com.example.haptivoice.model.UserData
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AuthViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    // Usamos AuthState para manejar tanto el login de Google como el de Correo/Contraseña
    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    // =======================================================================
    // 1. REGISTRO CON CORREO Y CONTRASEÑA
    // =======================================================================
    fun signUpWithEmail(
        username: String,
        email: String,
        password: String,
        disability: String,
        voiceType: String
    ) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                // Verificar que el email no exista en Firestore
                val userExists = firestore.collection("users")
                    .whereEqualTo("email", email)
                    .get()
                    .await()
                    .isEmpty
                    .not()

                if (userExists) {
                    Log.d("AuthViewModel", "El correo electrónico ya está registrado en Firestore")
                    _authState.value = AuthState.Error("Ya existe un usuario registrado con este correo")
                    return@launch
                }

                // Crear usuario en Firebase Auth
                val authResult = auth.createUserWithEmailAndPassword(email, password).await()
                val userId = authResult.user?.uid

                if (userId == null) {
                    _authState.value = AuthState.Error("Error al crear la cuenta")
                    return@launch
                }

                // Guardar datos en Firestore usando la clase UserData
                val userData = UserData(
                    userId = userId,
                    username = username,
                    email = email,
                    disability = disability,
                    voiceType = voiceType,
                    createdAt = Timestamp.now()
                )

                firestore.collection("users").document(userId).set(userData).await()
                Log.d("AuthViewModel", "Datos del usuario guardados en Firestore")

                // Si el registro con correo es exitoso, ya tiene todos los datos y es un usuario "Existente"/Completo
                _authState.value = AuthState.SuccessExistingUser(userId)

            } catch (e: Exception) {
                val errorMessage = when {
                    e.message?.contains("email address is already in use") == true ->
                        "Este correo electrónico ya está registrado"
                    e.message?.contains("password") == true ->
                        "La contraseña no cumple con los requisitos de seguridad"
                    e.message?.contains("network") == true ->
                        "Error de conexión. Verifica tu conexión a internet"
                    else -> e.message ?: "Error desconocido durante el registro"
                }
                Log.d("AuthViewModel", "Error durante el registro: $errorMessage")
                _authState.value = AuthState.Error(errorMessage)
            }
        }
    }

    // =======================================================================
    // 2. INICIO DE SESIÓN CON GOOGLE
    // =======================================================================
    fun signInWithGoogle(idToken: String) {
        _authState.value = AuthState.Loading

        val credential = GoogleAuthProvider.getCredential(idToken, null)

        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val result = task.result
                    val user = result?.user

                    // Aquí verificamos si es la primera vez que inicia sesión con Google
                    val isNewUser = result?.additionalUserInfo?.isNewUser == true

                    if (user != null) {
                        if (isNewUser) {
                            // Es nuevo: Mandamos el estado para redirigir a DatosAdicionalesScreen
                            _authState.value = AuthState.SuccessNewUser(user.uid, user.email)
                        } else {
                            // Ya existía: Redirigir directamente a la pantalla principal
                            _authState.value = AuthState.SuccessExistingUser(user.uid)
                        }
                    }
                } else {
                    _authState.value = AuthState.Error(
                        task.exception?.localizedMessage ?: "Error desconocido al iniciar sesión"
                    )
                }
            }
    }

    // =======================================================================
    // 3. GUARDAR DATOS ADICIONALES (Para usuarios nuevos de Google)
    // =======================================================================
    fun saveAdditionalUserData(
        uid: String,
        email: String?,
        username: String,
        disability: String,
        voiceType: String
    ) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading

            try {
                // Reutilizamos tu modelo UserData
                val userData = UserData(
                    userId = uid,
                    username = username,
                    email = email ?: "",
                    disability = disability,
                    voiceType = voiceType,
                    createdAt = Timestamp.now()
                )

                firestore.collection("users").document(uid).set(userData).await()

                // Una vez guardado, el registro se completa y lo tratamos como usuario registrado
                _authState.value = AuthState.SuccessExistingUser(uid)

            } catch (e: Exception) {
                _authState.value = AuthState.Error(
                    e.localizedMessage ?: "Error al guardar los datos en Firestore"
                )
            }
        }
    }

    fun resetState() {
        _authState.value = AuthState.Idle
    }
}