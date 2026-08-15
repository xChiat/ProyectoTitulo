package com.example.haptivoice.utils

import android.util.Patterns
import org.junit.Assert.assertEquals
import org.junit.Test

class ValidationUtilsTest {

    @Test
    fun testValidEmail() {
        val result = ValidationUtils.validateEmail("test@example.com")
        assertEquals(true, result is ValidationResult.Valid)
    }

    @Test
    fun testInvalidEmail() {
        val result = ValidationUtils.validateEmail("invalid-email")
        assert(result is ValidationResult.Invalid)
    }

    @Test
    fun testEmptyEmail() {
        val result = ValidationUtils.validateEmail("")
        assert(result is ValidationResult.Empty)
    }

    @Test
    fun testValidPassword() {
        val result = ValidationUtils.validatePassword("SecurePass123")
        assertEquals(true, result is ValidationResult.Valid)
    }

    @Test
    fun testShortPassword() {
        val result = ValidationUtils.validatePassword("Pass123")
        assert(result is ValidationResult.Invalid)
    }

    @Test
    fun testEmptyPassword() {
        val result = ValidationUtils.validatePassword("")
        assert(result is ValidationResult.Empty)
    }

    @Test
    fun testValidUsername() {
        val result = ValidationUtils.validateUsername("john_doe")
        assertEquals(true, result is ValidationResult.Valid)
    }

    @Test
    fun testShortUsername() {
        val result = ValidationUtils.validateUsername("ab")
        assert(result is ValidationResult.Invalid)
    }

    @Test
    fun testEmptyUsername() {
        val result = ValidationUtils.validateUsername("")
        assert(result is ValidationResult.Empty)
    }

    @Test
    fun testAllFieldsValid() {
        val result = ValidationUtils.validateAllFields(
            username = "john_doe",
            email = "john@example.com",
            password = "SecurePass123"
        )
        assertEquals(true, result is ValidationResult.Valid)
    }

    @Test
    fun testAllFieldsInvalidEmail() {
        val result = ValidationUtils.validateAllFields(
            username = "john_doe",
            email = "invalid-email",
            password = "SecurePass123"
        )
        assert(result is ValidationResult.Invalid)
    }

    @Test
    fun testAllFieldsEmptyUsername() {
        val result = ValidationUtils.validateAllFields(
            username = "",
            email = "john@example.com",
            password = "SecurePass123"
        )
        assert(result is ValidationResult.Empty)
    }
}

