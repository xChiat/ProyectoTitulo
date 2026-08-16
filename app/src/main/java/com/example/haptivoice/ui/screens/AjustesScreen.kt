package com.example.haptivoice.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.haptivoice.ui.theme.HaptivoiceTheme

@Composable
fun AjustesScreen(
    modifier: Modifier = Modifier,
    onLogout: () -> Unit = {} // <-- 1. Añadimos la función callback
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Ajustes",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // ==========================================
        // SECCIÓN: PERFIL DE USUARIO
        // ==========================================
        SectionTitle(title = "Perfil de Usuario")

        SettingsItem(
            icon = Icons.Default.Person,
            title = "Nombre de usuario",
            subtitle = "usuario_demo",
            onClick = { /* TODO: Lógica para cambiar nombre de usuario */ }
        )
        SettingsItem(
            icon = Icons.Default.Email,
            title = "Correo electrónico",
            subtitle = "correo@ejemplo.com",
            onClick = { /* TODO: Lógica para ver/cambiar correo */ }
        )
        SettingsItem(
            icon = Icons.Default.RecordVoiceOver, // Usa Icons.Default.VolumeUp si no tienes RecordVoiceOver
            title = "Preferencia de voz",
            subtitle = "Femenina",
            onClick = { /* TODO: Lógica para cambiar tipo de voz */ }
        )
        SettingsItem(
            icon = Icons.Default.Star,
            title = "Plan actual",
            subtitle = "Básico (Free-Tier)",
            onClick = { /* Muestra detalles del plan actual */ }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        // ==========================================
        // SECCIÓN: FACTURACIÓN Y PLANES
        // ==========================================
        SectionTitle(title = "Facturación")

        SettingsItem(
            icon = Icons.Default.CreditCard,
            title = "Administrar suscripción",
            subtitle = "Actualiza tu plan de pago (Próximamente)",
            onClick = { /* TODO: Navegar a pantalla de pagos */ }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        // ==========================================
        // SECCIÓN: SEGURIDAD
        // ==========================================
        SectionTitle(title = "Seguridad")

        SettingsItem(
            icon = Icons.Default.Lock,
            title = "Cambiar contraseña",
            onClick = { /* TODO: Flujo de cambio de contraseña */ }
        )
        SettingsItem(
            icon = Icons.Default.DeleteForever,
            title = "Eliminar cuenta",
            subtitle = "Esta acción es irreversible",
            iconTint = MaterialTheme.colorScheme.error,
            textColor = MaterialTheme.colorScheme.error,
            onClick = { /* TODO: Flujo de eliminación de cuenta */ }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        // ==========================================
        // SECCIÓN: INFORMACIÓN
        // ==========================================
        SectionTitle(title = "Información")

        SettingsItem(
            icon = Icons.Default.Description,
            title = "Términos de servicio",
            onClick = { /* TODO: Mostrar términos y condiciones */ }
        )

        Spacer(modifier = Modifier.height(32.dp))

        // ==========================================
        // BOTÓN: CERRAR SESIÓN
        // ==========================================
        OutlinedButton(
            onClick = onLogout,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                contentDescription = "Cerrar sesión"
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "Cerrar sesión")
        }
    }
}
@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = textColor
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AjustesScreenPreview(){
    HaptivoiceTheme {
        AjustesScreen()
    }
}