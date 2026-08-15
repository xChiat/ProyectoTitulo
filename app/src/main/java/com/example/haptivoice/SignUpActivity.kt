package com.example.haptivoice

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.ViewModelProvider
import com.example.haptivoice.ui.theme.HaptivoiceTheme
import com.example.haptivoice.viewmodel.SignUpViewModel

class SignUpActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val signUpViewModel = ViewModelProvider(this)[SignUpViewModel::class.java]
        setContent {
            HaptivoiceTheme {
                val uiState = signUpViewModel.uiState.collectAsState()

                // Observar el estado de éxito y navegar
                LaunchedEffect(uiState.value.success) {
                    if (uiState.value.success) {
                        val intent = Intent(this@SignUpActivity, MainActivity::class.java)
                        startActivity(intent)
                        finish()
                    }
                }

                SignUpScreen(
                    onSignUp = { username, email, password, disability, voiceType ->
                        signUpViewModel.signUp(username, email, password, disability, voiceType)
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



