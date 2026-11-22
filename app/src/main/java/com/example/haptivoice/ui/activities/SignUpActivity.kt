
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
import com.example.haptivoice.ui.screens.SignUpScreen
import com.example.haptivoice.ui.theme.HaptivoiceTheme
import com.example.haptivoice.utils.SessionManager
import com.example.haptivoice.viewmodel.AuthViewModel

import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

class SignUpActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        val authViewModel = ViewModelProvider(this)[
            AuthViewModel::class.java
        ]

        setContent {

            HaptivoiceTheme {

                val authState by authViewModel.authState.collectAsState()
                val context = LocalContext.current

                // =========================================
                // INICIO DE SESIÓN CON GOOGLE
                // =========================================

                val googleSignInLauncher =
                    rememberLauncherForActivityResult(
                        contract = ActivityResultContracts
                            .StartActivityForResult()
                    ) { result ->

                        val task =
                            GoogleSignIn.getSignedInAccountFromIntent(
                                result.data
                            )

                        try {
                            val account = task.getResult(
                                ApiException::class.java
                            )

                            account?.idToken?.let { idToken ->
                                authViewModel.signInWithGoogle(
                                    idToken
                                )
                            }

                        } catch (e: ApiException) {

                            Toast.makeText(
                                context,
                                "Error de Google Sign-In: ${e.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                // =========================================
                // ESTADOS DE AUTENTICACIÓN
                // =========================================

                when (val state = authState) {

                    // =====================================
                    // USUARIO EXISTENTE
                    // =====================================

                    is AuthState.SuccessExistingUser -> {

                        LaunchedEffect(Unit) {

                            val sessionManager =
                                SessionManager(
                                    this@SignUpActivity
                                )

                            sessionManager.saveSession()

                            val intent = Intent(
                                this@SignUpActivity,
                                MainActivity::class.java
                            )

                            startActivity(intent)
                            finish()
                        }
                    }

                    // =====================================
                    // NUEVO USUARIO DE GOOGLE
                    // =====================================
                    // Debe aceptar los términos antes
                    // de completar sus datos.
                    // =====================================

                    is AuthState.SuccessNewUser -> {

                        DatosAdicionalesScreen(

                            onSave = {
                                    username,
                                    disability,
                                    voiceType,
                                    acceptedTerms ->

                                authViewModel.saveAdditionalUserData(
                                    uid = state.uid,
                                    email = state.email,
                                    username = username,
                                    disability = disability,
                                    voiceType = voiceType,
                                    acceptedTerms = acceptedTerms
                                )
                            }
                        )
                    }

                    // =====================================
                    // ERROR DE AUTENTICACIÓN
                    // =====================================

                    is AuthState.Error -> {

                        LaunchedEffect(state.message) {

                            Toast.makeText(
                                context,
                                state.message,
                                Toast.LENGTH_LONG
                            ).show()

                            authViewModel.resetState()
                        }

                        RenderSignUpScreen(
                            authViewModel,
                            googleSignInLauncher
                        )
                    }

                    // =====================================
                    // PANTALLA NORMAL DE REGISTRO
                    // =====================================

                    else -> {

                        RenderSignUpScreen(
                            authViewModel,
                            googleSignInLauncher
                        )
                    }
                }
            }
        }
    }

    // =============================================
    // FORMULARIO DE REGISTRO
    // =============================================

    @Composable
    private fun RenderSignUpScreen(
        authViewModel: AuthViewModel,
        googleSignInLauncher:
        ManagedActivityResultLauncher<Intent, ActivityResult>
    ) {

        val context = LocalContext.current

        val webClientId = stringResource(
            R.string.default_web_client_id
        )

        SignUpScreen(

            // =====================================
            // REGISTRO CON CORREO Y CONTRASEÑA
            // =====================================
            // NUEVO:
            // Recibimos acceptedTerms y lo enviamos
            // al AuthViewModel.
            // =====================================

            onSignUp = {
                    username,
                    email,
                    password,
                    disability,
                    voiceType,
                    acceptedTerms ->

                authViewModel.signUpWithEmail(
                    username = username,
                    email = email,
                    password = password,
                    disability = disability,
                    voiceType = voiceType,
                    acceptedTerms = acceptedTerms
                )
            },

            // =====================================
            // REGISTRO CON GOOGLE
            // =====================================

            onGoogleSignUp = {

                val gso = GoogleSignInOptions.Builder(
                    GoogleSignInOptions.DEFAULT_SIGN_IN
                )
                    .requestIdToken(webClientId)
                    .requestEmail()
                    .build()

                val googleClient = GoogleSignIn.getClient(
                    context,
                    gso
                )

                googleClient.signOut()
                    .addOnCompleteListener {

                        googleSignInLauncher.launch(
                            googleClient.signInIntent
                        )
                    }
            },

            // =====================================
            // IR A INICIAR SESIÓN
            // =====================================

            onLoginClick = {

                val intent = Intent(
                    this@SignUpActivity,
                    LogInActivity::class.java
                )

                startActivity(intent)
                finish()
            }
        )
    }
}