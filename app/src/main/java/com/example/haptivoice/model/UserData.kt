package com.example.haptivoice.model

import com.google.firebase.Timestamp

data class UserData(
    val userId: String = "",
    val username: String = "",
    val email: String = "",
    val disability: String = "",
    val voiceType: String = "",
    val createdAt: Timestamp? = null,
    // campos de aceptación
    val termsVersion: String = "",
    val termsAcceptedAt: Timestamp? = null
)

