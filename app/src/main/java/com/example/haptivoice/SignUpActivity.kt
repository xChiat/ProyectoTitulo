package com.example.haptivoice

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.haptivoice.ui.theme.HaptivoiceTheme

class SignUpActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HaptivoiceTheme {
                SignUpScreen(
                    onSignUp = { username, email, password, disability, voiceType ->
                        // TODO: manejar lógica de registro con los datos proporcionados
                    },
                    onGoogleSignUp = {
                        // TODO: manejar registro con Google
                    },
                    onLoginClick = {
                        val intent = Intent(this@SignUpActivity, LogInActivity::class.java)
                        startActivity(intent)
                        finish()
                    }
                )
            }
        }
    }
}

