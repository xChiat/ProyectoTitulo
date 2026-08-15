package com.example.haptivoice.model

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class SuccessNewUser(val uid: String, val email: String?) : AuthState()
    data class SuccessExistingUser(val uid: String) : AuthState()
    data class Error(val message: String) : AuthState()
}