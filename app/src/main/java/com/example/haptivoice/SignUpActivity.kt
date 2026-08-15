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

class SignUpActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Cambiamos al nuevo AuthViewModel
        val authViewModel = ViewModelProvider(this)[AuthViewModel::class.java]

        setContent {
            HaptivoiceTheme {
                val authState by authViewModel.authState.collectAsState()
                val context = LocalContext.current

                // 1. Configuramos el Launcher para el inicio de sesión de Google
                val googleSignInLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                    try {
                        val account = task.getResult(ApiException::class.java)
                        account?.idToken?.let { idToken ->
                            // Pasamos el token al ViewModel
                            authViewModel.signInWithGoogle(idToken)
                        }
                    } catch (e: ApiException) {
                        Toast.makeText(context, "Error de Google Sign-In: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }

                // 2. Controlamos la UI basándonos en los estados
                when (val state = authState) {
                    is AuthState.SuccessExistingUser -> {
                        // Usuario completo o ya registrado -> Vamos al Home
                        LaunchedEffect(Unit) {
                            val intent = Intent(this@SignUpActivity, MainActivity::class.java)
                            startActivity(intent)
                            finish()
                        }
                    }
                    is AuthState.SuccessNewUser -> {
                        // Usuario nuevo de Google -> Mostramos la pantalla de datos adicionales
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
                        // Mostramos un Toast con el error y volvemos al estado Idle
                        LaunchedEffect(state.message) {
                            Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                            authViewModel.resetState()
                        }
                        // Mientras se resetea, mostramos el SignUpScreen
                        RenderSignUpScreen(authViewModel, googleSignInLauncher)
                    }
                    else -> {
                        // Estado Idle o Loading -> Mostramos el SignUpScreen
                        RenderSignUpScreen(authViewModel, googleSignInLauncher)
                    }
                }
            }
        }
    }

    // Extraemos la pantalla de registro a una función para no repetir código
    @androidx.compose.runtime.Composable
    private fun RenderSignUpScreen(
        authViewModel: AuthViewModel,
        googleSignInLauncher: androidx.activity.compose.ManagedActivityResultLauncher<Intent, androidx.activity.result.ActivityResult>
    ) {
        val context = LocalContext.current

        SignUpScreen(
            onSignUp = { username, email, password, disability, voiceType ->
                // Llamamos a la función actualizada del ViewModel para correo
                authViewModel.signUpWithEmail(username, email, password, disability, voiceType)
            },
            onGoogleSignUp = {
                // Configuramos las opciones de Google
                val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken("104696983752-f7ra57005ar73oh0sf2nfpi8ved84dt4.apps.googleusercontent.com")
                    .requestEmail()
                    .build()

                val googleClient = GoogleSignIn.getClient(context, gso)

                // Forzamos cerrar sesión previa de Google para asegurar que el usuario elija su cuenta si lo desea
                googleClient.signOut().addOnCompleteListener {
                    googleSignInLauncher.launch(googleClient.signInIntent)
                }
            },
            onLoginClick = {
                val intent = Intent(this@SignUpActivity, LogInActivity::class.java)
                startActivity(intent)
                finish()
            }
        )
    }
}