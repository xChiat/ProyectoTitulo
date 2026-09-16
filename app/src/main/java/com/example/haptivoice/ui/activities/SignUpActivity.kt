package com.example.haptivoice.ui.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.example.haptivoice.R
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
import androidx.lifecycle.ViewModelProvider
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
                                SessionManager(this@SignUpActivity)

                            sessionManager.saveSession()

                            val intent = Intent(this@SignUpActivity, MainActivity::class.java)
                            startActivity(intent)
                            finish()
                        }
                    }

                    is AuthState.SuccessNewUser -> {
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
                        RenderSignUpScreen(authViewModel, googleSignInLauncher)
                    }

                    else -> {
                        RenderSignUpScreen(authViewModel, googleSignInLauncher)
                    }
                }
            }
        }
    }

    @Composable
    private fun RenderSignUpScreen(
        authViewModel: AuthViewModel,
        googleSignInLauncher: ManagedActivityResultLauncher<Intent, ActivityResult>
    ) {
        val context = LocalContext.current

        SignUpScreen(
            onSignUp = { username, email, password, disability, voiceType ->
                authViewModel.signUpWithEmail(username, email, password, disability, voiceType)
            },
            onGoogleSignUp = {
                val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(context.getString(R.string.default_web_client_id))
                    .requestEmail()
                    .build()

                val googleClient = GoogleSignIn.getClient(context, gso)

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