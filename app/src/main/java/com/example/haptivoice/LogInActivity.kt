package com.example.haptivoice

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.haptivoice.ui.theme.HaptivoiceTheme

class LogInActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HaptivoiceTheme {
                LoginScreen(
                    onLogin = { email, password ->
                        // TODO: manejar lógica de login
                    },
                    onGoogleSignIn = {
                        // TODO: manejar login con Google
                    },
                    onRegister = {
                        // TODO: navegar a pantalla de registro
                    }
                )
            }
        }
    }
}