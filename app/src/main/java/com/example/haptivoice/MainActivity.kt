package com.example.haptivoice

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.haptivoice.ui.theme.HaptivoiceTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HaptivoiceTheme {
                var hasOldSession by remember { mutableStateOf(false) }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    if (hasOldSession) {
                        HomeScreen(
                            modifier = Modifier.padding(innerPadding),
                        )
                    } else {
                        AuthPromptScreen(
                            modifier = Modifier.padding(innerPadding),
                            onSimulateSession = { hasOldSession = true },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    var isPaired by remember { mutableStateOf(false) }
    var selectedItem by remember { mutableIntStateOf(0) }
    
    val navItems = listOf(
        Triple("Home", Icons.Default.Home, 0),
        Triple("Diccionario", Icons.AutoMirrored.Filled.MenuBook, 1),
        Triple("Ajustes", Icons.Default.Settings, 2)
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                navItems.forEach { (label, icon, index) ->
                    NavigationBarItem(
                        selected = selectedItem == index,
                        onClick = { selectedItem = index },
                        label = { Text(text = label) },
                        icon = { Icon(imageVector = icon, contentDescription = label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!isPaired) {
                Button(
                    onClick = { isPaired = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp)
                        .padding(bottom = 16.dp)
                ) {
                    Text(text = "Vincular Guantes")
                }
            } else {
                // TODO: Contenido cuando los guantes están vinculados
                Text(
                    text = "Guantes vinculados",
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "TODO: Implementar contenido principal de la aplicación aquí.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun AuthPromptScreen(
    modifier: Modifier = Modifier,
    onSimulateSession: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Ícono de la app (placeholder)
        Icon(
            imageVector = Icons.Default.GraphicEq,
            contentDescription = "Logo",
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp)
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
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Inicia sesión o regístrate para continuar.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Botón Iniciar Sesión (Filled)
                Button(
                    onClick = { /* Acción de iniciar sesión */ },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "Iniciar sesión")
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Botón Registrarse (Outlined)
                OutlinedButton(
                    onClick = { /* Acción de registro */ },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "Registrarse")
                }

                Spacer(modifier = Modifier.height(24.dp))

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Botón Iniciar Sesión con Google (Outlined con ícono)
                OutlinedButton(
                    onClick = onSimulateSession, // Usamos esto para simular éxito por ahora
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(text = "Iniciar sesión con Google")
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    HaptivoiceTheme {
        HomeScreen()
    }
}

@Preview(showBackground = true)
@Composable
fun AuthPromptScreenPreview() {
    HaptivoiceTheme {
        AuthPromptScreen()
    }
}