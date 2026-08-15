package com.example.haptivoice

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import com.example.haptivoice.model.AuthState
import com.example.haptivoice.ui.theme.HaptivoiceTheme
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
                        Toast.makeText(context, "Error de Google Sign-In: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
                when (val state = authState) {
                    is AuthState.SuccessExistingUser -> {
                        LaunchedEffect(Unit) {
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
                        // Idle o Loading -> Mostramos el formulario de Login
                        RenderLoginScreen(authViewModel, googleSignInLauncher)
                    }
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun RenderLoginScreen(
        authViewModel: AuthViewModel,
        googleSignInLauncher: androidx.activity.compose.ManagedActivityResultLauncher<Intent, androidx.activity.result.ActivityResult>
    ) {
        val context = LocalContext.current

        LoginScreen(
            onLogin = { email, password ->
                authViewModel.signInWithEmail(email, password)
            },
            onGoogleSignIn = {
                val webClientId = "104696983752-f7ra57005ar73oh0sf2nfpi8ved84dt4.apps.googleusercontent.com"

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
                // Navegar a la pantalla de registro
                val intent = Intent(this@LogInActivity, SignUpActivity::class.java)
                startActivity(intent)
                finish()
            }
        )
    }
}