package com.example.haptivoice.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.haptivoice.data.sync.VocabularySync
import com.example.haptivoice.model.UserData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException


sealed class ProfileUiState {
    object Loading : ProfileUiState()
    data class Success(val userData: UserData) : ProfileUiState()
    data class Error(val message: String) : ProfileUiState()

    //Cuenta eliminada correctamente
    object AccountDeleted : ProfileUiState()
}

/*
 * AndroidViewModel para tener el Context de la app: al eliminar
 * la cuenta también se borran los gestos guardados en Room.
 * viewModel() lo crea igual que antes, sin cambios en AjustesScreen.
 */
class ProfileViewModel(
    application: Application
) : AndroidViewModel(application) {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        loadUserData()
    }

    fun loadUserData() {
        viewModelScope.launch {
            _uiState.value = ProfileUiState.Loading
            val uid = auth.currentUser?.uid
            if (uid != null) {
                try {
                    val document = firestore.collection("users").document(uid).get().await()
                    val userData = document.toObject(UserData::class.java)

                    if (userData != null) {
                        _uiState.value = ProfileUiState.Success(userData)
                    } else {
                        _uiState.value = ProfileUiState.Error("No se encontraron los datos del usuario")
                    }
                } catch (e: Exception) {
                    _uiState.value = ProfileUiState.Error(e.localizedMessage ?: "Error al cargar datos")
                }
            } else {
                _uiState.value = ProfileUiState.Error("No hay sesión activa")
            }
        }
    }
    fun updateProfile(newUsername: String, newVoiceType: String) {
        viewModelScope.launch {
            _uiState.value = ProfileUiState.Loading
            val uid = auth.currentUser?.uid ?: return@launch
            try {
                firestore.collection("users").document(uid)
                    .update(
                        mapOf(
                            "username" to newUsername,
                            "voiceType" to newVoiceType
                        )
                    ).await()
                loadUserData()
            } catch (e: Exception) {
                _uiState.value = ProfileUiState.Error(e.localizedMessage ?: "Error al actualizar perfil")
            }
        }
    }
    fun deleteAccount() {
        viewModelScope.launch {
            val user = auth.currentUser
            val uid = user?.uid

            if (user == null || uid == null) {
                _uiState.value = ProfileUiState.Error("No hay sesión activa")
                return@launch
            }

            _uiState.value = ProfileUiState.Loading

            try {
                // HU07 / CU-006: primero los gestos propios y las
                // reasignaciones (Firestore y teléfono), mientras la
                // sesión sigue activa y las reglas permiten borrarlos.
                VocabularySync(getApplication()).deleteAllForUser(uid)

                // Elimina los datos del usuario en Firestore
                firestore.collection("users")
                    .document(uid)
                    .delete()
                    .await()

                // Elimina la cuenta de Firebase Authentication
                user.delete().await()

                _uiState.value = ProfileUiState.AccountDeleted

            } catch (e: FirebaseAuthRecentLoginRequiredException) {
                _uiState.value = ProfileUiState.Error(
                    "Por seguridad, debes volver a iniciar sesión antes de eliminar tu cuenta"
                )

            } catch (e: Exception) {
                _uiState.value = ProfileUiState.Error(
                    e.localizedMessage ?: "Error al eliminar la cuenta"
                )
            }
        }
    }
}