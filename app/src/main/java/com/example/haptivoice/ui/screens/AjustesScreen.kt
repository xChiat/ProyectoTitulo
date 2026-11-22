
package com.example.haptivoice.ui.screens

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import com.example.haptivoice.ui.activities.RecoveryActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.haptivoice.viewmodel.ProfileUiState
import com.example.haptivoice.viewmodel.ProfileViewModel
import com.example.haptivoice.utils.SubscriptionManager
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.haptivoice.utils.TermsConfig


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AjustesScreen(
    modifier: Modifier = Modifier,
    profileViewModel: ProfileViewModel = viewModel(),
    subscriptionManager: SubscriptionManager,
    onLogout: () -> Unit = {}
) {
    val uiState by profileViewModel.uiState.collectAsState()

    val context = LocalContext.current

    // Compartimos el estado de Google Play Billing con HomeScreen.
    val isPremium by subscriptionManager.isPremium.collectAsState()

    // Al entrar a Ajustes, consultamos nuevamente el estado en Google Play.
    // No modifica los pagos ni activa Premium manualmente.
    LaunchedEffect(subscriptionManager) {
        subscriptionManager.refresh(
            onError = { message ->
                Log.w("AjustesScreen", "Consulta Premium: $message")
            }
        )
    }

    //confirmación antes de eliminar la cuenta
    var showDeleteDialog by remember { mutableStateOf(false) }

    // NUEVO: controlar el diálogo de términos de servicio
    var showTermsDialog by rememberSaveable {
        mutableStateOf(false)
    }

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

        when (val state = uiState) {

            is ProfileUiState.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(16.dp)
                )
            }

            is ProfileUiState.Error -> {
                Text(
                    text = state.message,
                    color = MaterialTheme.colorScheme.error
                )
            }

            is ProfileUiState.Success -> {
                val userData = state.userData
                var isEditing by remember { mutableStateOf(false) }

                var editedUsername by remember(userData) {
                    mutableStateOf(userData.username)
                }

                val voiceOptions = listOf(
                    "Femenina",
                    "Masculino"
                )
                val currentVoiceLabel =
                    if (
                        userData.voiceType.equals(
                            "Masculino",
                            ignoreCase = true
                        ) ||
                        userData.voiceType.equals(
                            "Masculina",
                            ignoreCase = true
                        )
                    ) {
                        "Masculino"
                    } else {
                        "Femenina"
                    }

                var editedVoiceTypeIndex by remember(userData) {
                    mutableIntStateOf(
                        voiceOptions.indexOf(currentVoiceLabel)
                    )
                }

                if (!isEditing) {

                    SettingsItem(
                        icon = Icons.Default.Person,
                        title = "Nombre de usuario",
                        subtitle = userData.username,
                        trailingContent = {
                            IconButton(
                                onClick = {
                                    isEditing = true
                                }
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Editar perfil"
                                )
                            }
                        }
                    )

                    SettingsItem(
                        icon = Icons.Default.Email,
                        title = "Correo electrónico",
                        subtitle = userData.email
                    )

                    SettingsItem(
                        icon = Icons.Default.RecordVoiceOver,
                        title = "Preferencia de voz",
                        subtitle = currentVoiceLabel,
                        trailingContent = {
                            IconButton(
                                onClick = {
                                    isEditing = true
                                }
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Editar tipo de voz"
                                )
                            }
                        }
                    )

                } else {

                    OutlinedTextField(
                        value = editedUsername,
                        onValueChange = {
                            editedUsername = it
                        },
                        label = {
                            Text("Nombre de usuario")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        singleLine = true
                    )

                    SettingsItem(
                        icon = Icons.Default.Email,
                        title = "Correo electrónico",
                        subtitle = userData.email
                    )

                    Spacer(
                        modifier = Modifier.height(16.dp)
                    )

                    Text(
                        text = "Tipo de voz sintética",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )

                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {

                        voiceOptions.forEachIndexed { index, label ->

                            SegmentedButton(
                                selected = editedVoiceTypeIndex == index,
                                onClick = {
                                    editedVoiceTypeIndex = index
                                },
                                shape = RoundedCornerShape(
                                    topStart =
                                        if (index == 0) 12.dp else 0.dp,

                                    bottomStart =
                                        if (index == 0) 12.dp else 0.dp,

                                    topEnd =
                                        if (index == voiceOptions.size - 1)
                                            12.dp
                                        else
                                            0.dp,

                                    bottomEnd =
                                        if (index == voiceOptions.size - 1)
                                            12.dp
                                        else
                                            0.dp
                                ),
                                modifier = Modifier.weight(1f)
                            ) {

                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {

                        OutlinedButton(
                            onClick = {

                                isEditing = false

                                editedUsername =
                                    userData.username

                                editedVoiceTypeIndex =
                                    voiceOptions.indexOf(currentVoiceLabel)
                            },
                            modifier = Modifier.padding(end = 8.dp)
                        ) {

                            Text("Cancelar")
                        }

                        Button(
                            onClick = {

                                profileViewModel.updateProfile(
                                    newUsername = editedUsername,
                                    newVoiceType =
                                        voiceOptions[editedVoiceTypeIndex]
                                )

                                isEditing = false
                            }
                        ) {

                            Text("Guardar cambios")
                        }
                    }
                }
            }

            is ProfileUiState.AccountDeleted -> {
                LaunchedEffect(Unit) {
                    onLogout()
                }
            }
        }

        // ==========================================
        // PLAN ACTUAL SEGÚN GOOGLE PLAY BILLING
        // ==========================================
        SettingsItem(
            icon = Icons.Default.Star,
            title = "Plan actual",
            subtitle = if (isPremium) {
                "HaptiVoice Premium (Activo)"
            } else {
                "Básico (Free-Tier)"
            }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        // ==========================================
        // SECCIÓN: FACTURACIÓN Y PLANES
        // ==========================================
        SectionTitle(title = "Facturación")

        // Abrimos la gestión oficial de suscripciones de Google Play.
        // Si Premium está activo, mostramos directamente HaptiVoice Premium.
        // En caso contrario, mostramos el centro general de suscripciones.
        SettingsItem(
            icon = Icons.Default.CreditCard,
            title = "Administrar suscripción",
            subtitle = if (isPremium) {
                "Gestionar o cancelar HaptiVoice Premium en Google Play"
            } else {
                "Ver suscripciones en Google Play"
            },
            onClick = {
                val url = if (isPremium) {
                    "https://play.google.com/store/account/subscriptions" +
                            "?sku=${SubscriptionManager.PREMIUM_PRODUCT_ID}" +
                            "&package=${context.packageName}"
                } else {
                    "https://play.google.com/store/account/subscriptions"
                }

                try {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    )
                } catch (exception: Exception) {
                    Log.e(
                        "AjustesScreen",
                        "No se pudo abrir Google Play",
                        exception
                    )
                    android.widget.Toast.makeText(
                        context,
                        "No se pudo abrir la gestión de suscripciones",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        // ==========================================
        // SECCIÓN: SEGURIDAD
        // ==========================================
        SectionTitle(title = "Seguridad")
        SettingsItem(
            icon = Icons.Default.Lock,
            title = "Cambiar contraseña",
            onClick = {
                val intent = Intent(context, RecoveryActivity::class.java)
                context.startActivity(intent)
            }
        )

        SettingsItem(
            icon = Icons.Default.DeleteForever,
            title = "Eliminar cuenta",
            subtitle = "Esta acción es irreversible",
            iconTint = MaterialTheme.colorScheme.error,
            textColor = MaterialTheme.colorScheme.error,
            onClick = {
                showDeleteDialog = true
            }
        )
        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = {
                    showDeleteDialog = false
                },
                title = {
                    Text("Eliminar cuenta")
                },
                text = {
                    Text(
                        "¿Estás seguro de que deseas eliminar tu cuenta? " +
                                "Esta acción es permanente y no se puede deshacer."
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteDialog = false
                            profileViewModel.deleteAccount()
                        }
                    ) {
                        Text(
                            text = "Eliminar",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showDeleteDialog = false
                        }
                    ) {
                        Text("Cancelar")
                    }
                }
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))


        // ==========================================
        // SECCIÓN: INFORMACIÓN
        // ==========================================

        SectionTitle(title = "Información")

        SettingsItem(
            icon = Icons.Default.Description,
            title = "Términos de servicio",
            subtitle = "Consultar términos y condiciones de HaptiVoice",
            onClick = {
                showTermsDialog = true
            }
        )

        // ==========================================
        // NUEVO: MOSTRAR TÉRMINOS Y CONDICIONES
        // ==========================================

        if (showTermsDialog) {

            AlertDialog(
                onDismissRequest = {
                    showTermsDialog = false
                },

                title = {
                    Text(
                        text = "Términos y condiciones - " +
                                TermsConfig.VERSION
                    )
                },

                text = {
                    Text(
                        text = TERMS_TEXT,
                        modifier = Modifier
                            .heightIn(max = 400.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 4.dp)
                    )
                },

                confirmButton = {
                    TextButton(
                        onClick = {
                            showTermsDialog = false
                        }
                    ) {
                        Text("Cerrar")
                    }
                }
            )
        }

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
    onClick: (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
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

        Column(modifier = Modifier.weight(1f)) {
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

        if (trailingContent != null) {
            trailingContent()
        }
    }
}
