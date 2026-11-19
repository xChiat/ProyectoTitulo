package com.example.haptivoice.ui.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import com.example.haptivoice.R
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import com.example.haptivoice.model.AuthState
import com.example.haptivoice.ui.screens.DatosAdicionalesScreen
import com.example.haptivoice.ui.screens.HomeScreen
import com.example.haptivoice.ui.theme.HaptivoiceTheme
import com.example.haptivoice.ui.theme.bg
import com.example.haptivoice.ui.theme.darker_txt
import com.example.haptivoice.ui.theme.icons
import com.example.haptivoice.ui.theme.primary_btn
import com.example.haptivoice.ui.theme.pure_white
import com.example.haptivoice.ui.theme.txt
import com.example.haptivoice.utils.BluetoothManager
import com.example.haptivoice.utils.SessionManager
import com.example.haptivoice.utils.SubscriptionManager
import com.example.haptivoice.utils.TextToSpeechManager
import com.example.haptivoice.viewmodel.AuthViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val auth = FirebaseAuth.getInstance()
        val sessionManager = SessionManager(this)

        val authViewModel = ViewModelProvider(this)[AuthViewModel::class.java]

        val isFirebaseUserLoggedIn = auth.currentUser != null
        val isSessionWithin90Days = sessionManager.isSessionValid()

        if (isFirebaseUserLoggedIn && !isSessionWithin90Days) {
            auth.signOut()
            sessionManager.clearSession()
        }

        val initialSessionState = isFirebaseUserLoggedIn && isSessionWithin90Days

        setContent {
            HaptivoiceTheme {
                val context = LocalContext.current
                val bluetoothManager = remember { BluetoothManager(context.applicationContext) }
                val ttsManager = remember { TextToSpeechManager(context.applicationContext) }
                val subscriptionManager = remember { SubscriptionManager(context.applicationContext) }

//
// =====================================================
// CONEXIÓN DE SUSCRIPCIÓN PREMIUM CON EL MOTOR DE VOZ
// =====================================================
//

                val premiumActive by
                subscriptionManager.isPremium.collectAsState()

                val premiumToken by
                subscriptionManager.purchaseToken.collectAsState()

                LaunchedEffect(premiumActive, premiumToken) {

                    ttsManager.setPremiumAccess(
                        active = premiumActive,
                        purchaseToken = premiumToken
                    )
                }

                DisposableEffect(Unit) {
                    subscriptionManager.start(
                        onError = { msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    )
                    onDispose {
                        subscriptionManager.close()
                        ttsManager.destroy()
                    }
                }

                val authState by authViewModel.authState.collectAsState()
                var hasOldSession by remember { mutableStateOf(initialSessionState) }

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
                        Toast.makeText(context, "Error de Google: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }

                when (val state = authState) {
                    is AuthState.SuccessExistingUser -> {
                        LaunchedEffect(Unit) {
                            sessionManager.saveSession()
                            hasOldSession = true
                            authViewModel.resetState()
                        }
                    }
                    is AuthState.Error -> {
                        LaunchedEffect(state.message) {
                            Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                            authViewModel.resetState()
                        }
                    }
                    else -> {}
                }

                if (authState is AuthState.SuccessNewUser) {
                    val state = authState as AuthState.SuccessNewUser
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
                } else {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        if (hasOldSession) {
                            HomeScreen(
                                modifier = Modifier.padding(innerPadding),
                                bluetoothManager = bluetoothManager,
                                ttsManager = ttsManager,
                                subscriptionManager = subscriptionManager,
                                onLogout = {
                                    auth.signOut()
                                    sessionManager.clearSession()
                                    hasOldSession = false
                                }
                            )
                        } else {
                            AuthPromptScreen(
                                modifier = Modifier.padding(innerPadding),
                                onGoogleSignIn = { webClientId ->
                                    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                                        .requestIdToken(webClientId)
                                        .requestEmail()
                                        .build()

                                    val googleClient = GoogleSignIn.getClient(context, gso)
                                    googleClient.signOut().addOnCompleteListener {
                                        googleSignInLauncher.launch(googleClient.signInIntent)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AuthPromptScreen(
    modifier: Modifier = Modifier,
    onGoogleSignIn: (String) -> Unit = { _ -> }
) {
    val context = LocalContext.current
    val webClientId = stringResource(R.string.default_web_client_id)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(bg)
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.GraphicEq,
            contentDescription = "Logo",
            modifier = Modifier.size(80.dp),
            tint = primary_btn
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.outlinedCardColors(
                containerColor = pure_white
            ),
            border = BorderStroke(
                width = 1.dp,
                color = icons
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Bienvenido a haptivoice",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    color = darker_txt
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Inicia sesión o regístrate para continuar.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = txt
                )

                Spacer(modifier = Modifier.height(48.dp))

                Button(
                    onClick = {
                        val intent = Intent(context, LogInActivity::class.java)
                        context.startActivity(intent)
                        (context as? MainActivity)?.finish()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = primary_btn,
                        contentColor = pure_white
                    )
                ) {
                    Text(text = "Iniciar sesión")
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        val intent = Intent(context, SignUpActivity::class.java)
                        context.startActivity(intent)
                        (context as? MainActivity)?.finish()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = pure_white
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        color = primary_btn
                    )
                ) {
                    Text(
                        text = "Registrarse",
                        color = primary_btn
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    thickness = 1.dp,
                    color = icons
                )

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedButton(
                    onClick = { onGoogleSignIn(webClientId) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = pure_white
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        color = primary_btn
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = primary_btn
                    )
                    Spacer(modifier = Modifier.size(4.dp))
                    Text(
                        text = "Iniciar sesión con Google",
                        color = primary_btn
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AuthPromptScreenPreview() {
    HaptivoiceTheme {
        AuthPromptScreen(onGoogleSignIn = { _ -> })
    }
}