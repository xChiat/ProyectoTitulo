package com.example.haptivoice.utils

import android.util.Patterns

object ValidationUtils {

    fun validateEmail(email: String): ValidationResult {
        return when {
            email.isEmpty() -> ValidationResult.Empty("El correo electrónico es requerido")
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() ->
                ValidationResult.Invalid("Correo electrónico inválido")
            else -> ValidationResult.Valid
        }
    }

    fun validatePassword(password: String): ValidationResult {
        return when {
            password.isEmpty() -> ValidationResult.Empty("La contraseña es requerida")
            password.length < 8 -> ValidationResult.Invalid("La contraseña debe tener al menos 8 caracteres")
            else -> ValidationResult.Valid
        }
    }

    fun validateUsername(username: String): ValidationResult {
        return when {
            username.isEmpty() -> ValidationResult.Empty("El nombre de usuario es requerido")
            username.length < 3 -> ValidationResult.Invalid("El nombre de usuario debe tener al menos 3 caracteres")
            else -> ValidationResult.Valid
        }
    }

    fun validateAllFields(
        username: String,
        email: String,
        password: String
    ): ValidationResult {
        return when {
            username.isEmpty() -> ValidationResult.Empty("El nombre de usuario es requerido")
            email.isEmpty() -> ValidationResult.Empty("El correo electrónico es requerido")
            password.isEmpty() -> ValidationResult.Empty("La contraseña es requerida")
            username.length < 3 -> ValidationResult.Invalid("El nombre de usuario debe tener al menos 3 caracteres")
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() ->
                ValidationResult.Invalid("Correo electrónico inválido")
            password.length < 8 -> ValidationResult.Invalid("La contraseña debe tener al menos 8 caracteres")
            else -> ValidationResult.Valid
        }
    }
}

sealed class ValidationResult {
    data object Valid : ValidationResult()
    data class Empty(val message: String) : ValidationResult()
    data class Invalid(val message: String) : ValidationResult()
}

