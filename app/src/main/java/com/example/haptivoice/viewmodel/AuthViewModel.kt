
package com.example.haptivoice.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import com.example.haptivoice.model.AuthState
import com.example.haptivoice.model.UserData
import com.example.haptivoice.utils.TermsConfig

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

    private val _authState =
        MutableStateFlow<AuthState>(AuthState.Idle)

    val authState: StateFlow<AuthState> =
        _authState.asStateFlow()

    // =====================================================
    // 1. REGISTRO CON CORREO Y CONTRASEÑA
    // =====================================================

    fun signUpWithEmail(
        username: String,
        email: String,
        password: String,
        disability: String,
        voiceType: String,
        acceptedTerms: Boolean
    ) {

        // Impedir la creación de la cuenta
        // si no aceptó los términos.

        if (!acceptedTerms) {
            _authState.value = AuthState.Error(
                "Debes aceptar los términos y condiciones."
            )
            return
        }

        viewModelScope.launch {

            _authState.value = AuthState.Loading

            try {

                // Crear usuario en Firebase Authentication.

                val authResult =
                    auth.createUserWithEmailAndPassword(
                        email,
                        password
                    ).await()

                val userId = authResult.user?.uid

                if (userId == null) {

                    _authState.value = AuthState.Error(
                        "Error al crear la cuenta"
                    )

                    return@launch
                }

                // Guardar cuándo aceptó los términos
                // y qué versión aceptó.

                val now = Timestamp.now()

                val userData = UserData(
                    userId = userId,
                    username = username,
                    email = email,
                    disability = disability,
                    voiceType = voiceType,
                    createdAt = now,
                    termsVersion = TermsConfig.VERSION,
                    termsAcceptedAt = now
                )

                firestore.collection("users")
                    .document(userId)
                    .set(userData)
                    .await()

                Log.d(
                    "AuthViewModel",
                    "Usuario registrado con términos aceptados"
                )

                _authState.value =
                    AuthState.SuccessExistingUser(userId)

            } catch (e: Exception) {

                val errorMessage = when {

                    e.message?.contains(
                        "email address is already in use",
                        ignoreCase = true
                    ) == true ->
                        "Este correo electrónico ya está registrado"

                    e.message?.contains(
                        "email-already-in-use",
                        ignoreCase = true
                    ) == true ->
                        "Este correo electrónico ya está registrado"

                    e.message?.contains(
                        "password",
                        ignoreCase = true
                    ) == true ->
                        "La contraseña no cumple con los requisitos de seguridad"

                    e.message?.contains(
                        "network",
                        ignoreCase = true
                    ) == true ->
                        "Error de conexión. Verifica tu conexión a internet"

                    else ->
                        e.localizedMessage
                            ?: "Error desconocido durante el registro"
                }

                Log.e(
                    "AuthViewModel",
                    "Error durante el registro: $errorMessage",
                    e
                )

                _authState.value =
                    AuthState.Error(errorMessage)
            }
        }
    }

    // =====================================================
    // 2. INICIO DE SESIÓN CON GOOGLE
    // =====================================================

    fun signInWithGoogle(idToken: String) {

        _authState.value = AuthState.Loading

        val credential =
            GoogleAuthProvider.getCredential(idToken, null)

        viewModelScope.launch {

            try {

                val result =
                    auth.signInWithCredential(
                        credential
                    ).await()

                val user = result.user

                if (user == null) {

                    _authState.value = AuthState.Error(
                        "No se pudo obtener la cuenta de Google"
                    )

                    return@launch
                }

                // No basta con comprobar isNewUser.
                //
                // Una persona puede haber iniciado sesión
                // con Google anteriormente, pero no haber
                // completado sus datos ni aceptado
                // los términos.
                //
                // Por eso comprobamos si tiene perfil
                // en la colección users de Firestore.

                val profile = firestore
                    .collection("users")
                    .document(user.uid)
                    .get()
                    .await()

                if (profile.exists()) {

                    // Tiene perfil guardado.
                    // Se considera usuario existente.

                    _authState.value =
                        AuthState.SuccessExistingUser(
                            user.uid
                        )

                } else {

                    // No tiene perfil guardado.
                    // Debe completar DatosAdicionalesScreen
                    // y aceptar los términos.

                    _authState.value =
                        AuthState.SuccessNewUser(
                            user.uid,
                            user.email
                        )
                }

            } catch (e: Exception) {

                Log.e(
                    "AuthViewModel",
                    "Error durante el inicio de sesión con Google",
                    e
                )

                _authState.value = AuthState.Error(
                    e.localizedMessage
                        ?: "Error desconocido al iniciar sesión"
                )
            }
        }
    }

    // =====================================================
    // 3. GUARDAR DATOS ADICIONALES
    //    PARA USUARIOS NUEVOS DE GOOGLE
    // =====================================================

    fun saveAdditionalUserData(
        uid: String,
        email: String?,
        username: String,
        disability: String,
        voiceType: String,
        acceptedTerms: Boolean
    ) {

        // Bloquear el guardado si no aceptó
        // los términos y condiciones.

        if (!acceptedTerms) {

            _authState.value = AuthState.Error(
                "Debes aceptar los términos y condiciones."
            )

            return
        }

        viewModelScope.launch {

            _authState.value = AuthState.Loading

            try {

                // Comprobar que el perfil corresponde
                // al usuario autenticado.

                if (auth.currentUser?.uid != uid) {

                    _authState.value = AuthState.Error(
                        "La sesión del usuario no es válida."
                    )

                    return@launch
                }

                // Registrar la aceptación en Firestore.

                val now = Timestamp.now()

                val userData = UserData(
                    userId = uid,
                    username = username,
                    email = email ?: "",
                    disability = disability,
                    voiceType = voiceType,
                    createdAt = now,
                    termsVersion = TermsConfig.VERSION,
                    termsAcceptedAt = now
                )

                firestore.collection("users")
                    .document(uid)
                    .set(userData)
                    .await()

                Log.d(
                    "AuthViewModel",
                    "Perfil de Google guardado con términos aceptados"
                )

                // Registro completado correctamente.

                _authState.value =
                    AuthState.SuccessExistingUser(uid)

            } catch (e: Exception) {

                Log.e(
                    "AuthViewModel",
                    "Error al guardar datos adicionales",
                    e
                )

                _authState.value = AuthState.Error(
                    e.localizedMessage
                        ?: "Error al guardar los datos en Firestore"
                )
            }
        }
    }

    // =====================================================
    // 4. INICIO DE SESIÓN CON CORREO Y CONTRASEÑA
    // =====================================================

    fun signInWithEmail(
        email: String,
        password: String
    ) {

        viewModelScope.launch {

            _authState.value = AuthState.Loading

            try {

                val authResult =
                    auth.signInWithEmailAndPassword(
                        email,
                        password
                    ).await()

                val user = authResult.user

                if (user != null) {

                    _authState.value =
                        AuthState.SuccessExistingUser(
                            user.uid
                        )

                } else {

                    _authState.value = AuthState.Error(
                        "Error al iniciar sesión"
                    )
                }

            } catch (e: Exception) {

                val errorMessage = when {

                    e.message?.contains(
                        "invalid-credential",
                        ignoreCase = true
                    ) == true ->
                        "Credenciales incorrectas"

                    e.message?.contains(
                        "user-not-found",
                        ignoreCase = true
                    ) == true ->
                        "Usuario no registrado"

                    e.message?.contains(
                        "wrong-password",
                        ignoreCase = true
                    ) == true ->
                        "Contraseña incorrecta"

                    e.message?.contains(
                        "network",
                        ignoreCase = true
                    ) == true ->
                        "Error de conexión"

                    else ->
                        e.message
                            ?: "Error desconocido al iniciar sesión"
                }

                _authState.value =
                    AuthState.Error(errorMessage)
            }
        }
    }

    // =====================================================
    // 5. RESTABLECER ESTADO
    // =====================================================

    fun resetState() {
        _authState.value = AuthState.Idle
    }
}