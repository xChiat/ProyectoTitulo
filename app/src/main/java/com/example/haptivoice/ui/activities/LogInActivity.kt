package com.example.haptivoice.ui.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import com.example.haptivoice.R
import com.example.haptivoice.model.AuthState
import com.example.haptivoice.ui.screens.DatosAdicionalesScreen
import com.example.haptivoice.ui.screens.LoginScreen
import com.example.haptivoice.ui.theme.HaptivoiceTheme
import com.example.haptivoice.utils.SessionManager
import com.example.haptivoice.viewmodel.AuthViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

class LogInActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val authViewModel = ViewModelProvider(this)[AuthViewModel::class.java]

        setContent {
            HaptivoiceTheme {
                val authState by authViewModel.authState.collectAsState()
                val context = LocalContext.current

                val googleSignInLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                    try {
                        val account = task.getResult(ApiException::class.java)
                        account?.idToken?.let { idToken ->
                            authViewModel.signInWithGoogle(idToken)
                        }
                    } catch (e: ApiException) {
                        Toast.makeText(
                            context,
                            "Error de Google Sign-In: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                when (val state = authState) {
                    is AuthState.SuccessExistingUser -> {
                        LaunchedEffect(Unit) {
                            val sessionManager =
                                SessionManager(this@LogInActivity)

                            sessionManager.saveSession()

                            val intent = Intent(this@LogInActivity, MainActivity::class.java)
                            startActivity(intent)
                            finish()
                        }
                    }

                    is AuthState.SuccessNewUser -> {
                        // Si por algún motivo este aweonao entra con Google desde el Login y es NUEVO,
                        // lo mando devuelta a llenar sus datos obligatorios >:(
                        DatosAdicionalesScreen(
                            onSave = { username, disability, voiceType ->
                                authViewModel.saveAdditionalUserData(
                                    uid = state.uid,
                                    email = state.email,
                                    username = username,
                                    disability = disability,
                                    voiceType = voiceType
                                )
                            }
                        )
                    }

                    is AuthState.Error -> {
                        LaunchedEffect(state.message) {
                            Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                            authViewModel.resetState()
                        }
                        RenderLoginScreen(authViewModel, googleSignInLauncher)
                    }

                    else -> {
                        RenderLoginScreen(authViewModel, googleSignInLauncher)
                    }
                }
            }
        }
    }

    @Composable
    private fun RenderLoginScreen(
        authViewModel: AuthViewModel,
        googleSignInLauncher: ManagedActivityResultLauncher<Intent, ActivityResult>
    ) {
        val context = LocalContext.current
        val webClientId = stringResource(R.string.default_web_client_id)

        LoginScreen(
            onLogin = { email, password ->
                authViewModel.signInWithEmail(email, password)
            },
            onGoogleSignIn = {
                val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(webClientId)
                    .requestEmail()
                    .build()

                val googleClient = GoogleSignIn.getClient(context, gso)

                // Hacemos signOut previo para obligar a seleccionar cuenta
                googleClient.signOut().addOnCompleteListener {
                    googleSignInLauncher.launch(googleClient.signInIntent)
                }
            },
            onRegister = {
                val intent = Intent(this@LogInActivity, SignUpActivity::class.java)
                startActivity(intent)
                finish()
            },
            onPasswordRecovery = {
                val intent = Intent(this@LogInActivity, RecoveryActivity::class.java)
                startActivity(intent)
                finish()
            }
        )
    }
}