package com.example.haptivoice

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import com.example.haptivoice.ui.screens.RecoveryPasswordScreen
import com.example.haptivoice.ui.theme.HaptivoiceTheme
import com.example.haptivoice.utils.SessionManager
import com.example.haptivoice.viewmodel.RecoveryState
import com.example.haptivoice.viewmodel.RecoveryViewModel
import com.google.firebase.auth.FirebaseAuth

class RecoveryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val recoveryViewModel = ViewModelProvider(this)[RecoveryViewModel::class.java]
        val auth = FirebaseAuth.getInstance()
        val sessionManager = SessionManager(this)

        setContent {
            HaptivoiceTheme {
                val uiState by recoveryViewModel.uiState.collectAsState()
                val context = LocalContext.current

                LaunchedEffect(uiState) {
                    when (uiState) {
                        is RecoveryState.Error -> {
                            val errorMessage = (uiState as RecoveryState.Error).message
                            Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                            recoveryViewModel.resetState()
                        }
                        is RecoveryState.Success -> {
                            Toast.makeText(context, "Contraseña cambiada exitosamente", Toast.LENGTH_LONG).show()
                            auth.signOut()
                            sessionManager.clearSession()
                            val intent = Intent(this@RecoveryActivity, LogInActivity::class.java)
                            startActivity(intent)
                            finish()
                        }
                        else -> {}
                    }
                }

                val currentStep = when (uiState) {
                    is RecoveryState.Idle, is RecoveryState.Loading -> 1
                    is RecoveryState.EmailSent -> 2
                    is RecoveryState.CodeVerified -> 3
                    else -> 1
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    RecoveryPasswordScreen(
                        modifier = Modifier.padding(innerPadding),
                        currentStep = currentStep,
                        isLoading = uiState is RecoveryState.Loading,
                        onSendEmail = { email ->
                            recoveryViewModel.sendRecoveryEmail(email)
                        },
                        onVerifyCode = { code ->
                            recoveryViewModel.verifyCodeLocally(code)
                        },
                        onResetPassword = { newPassword ->
                            recoveryViewModel.resetPassword(newPassword)
                        },
                        onNavigateToLogin = {
                            val intent = Intent(this@RecoveryActivity, LogInActivity::class.java)
                            startActivity(intent)
                            finish()
                        }
                    )
                }
            }
        }
    }
}