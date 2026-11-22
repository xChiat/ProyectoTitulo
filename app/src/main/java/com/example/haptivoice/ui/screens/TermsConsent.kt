
package com.example.haptivoice.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.haptivoice.utils.TermsConfig

@Composable
fun TermsConsent(
    accepted: Boolean,
    onAcceptedChange: (Boolean) -> Unit,
    showError: Boolean = false
) {
    var showDialog by rememberSaveable {
        mutableStateOf(false)
    }

    var termsViewed by rememberSaveable {
        mutableStateOf(false)
    }

    Column(modifier = Modifier.fillMaxWidth()) {

        TextButton(
            onClick = { showDialog = true }
        ) {
            Text("Leer términos y condiciones")
        }

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = accepted,
                onCheckedChange = onAcceptedChange,
                enabled = termsViewed
            )

            Text(
                "He leído y acepto los términos y " +
                        "condiciones de HaptiVoice"
            )
        }

        if (showError && !accepted) {
            Text(
                text = "Debes leer y aceptar los términos para continuar.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = {
                showDialog = false
            },

            title = {
                Text(
                    "Términos y condiciones - " +
                            TermsConfig.VERSION
                )
            },

            text = {
                Text(
                    text = TERMS_TEXT,
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 4.dp)
                )
            },

            confirmButton = {
                TextButton(
                    onClick = {
                        termsViewed = true
                        showDialog = false
                    }
                ) {
                    Text("Terminé de leer")
                }
            },

            dismissButton = {
                TextButton(
                    onClick = {
                        showDialog = false
                    }
                ) {
                    Text("Cerrar")
                }
            }
        )
    }
}

// aprobar el texto antes del lanzamiento público.

internal val TERMS_TEXT = """
TÉRMINOS Y CONDICIONES DE HAPTIVOICE
Versión 1.1 - 21 de noviembre de 2026

1. ACEPTACIÓN DE LOS TÉRMINOS

Bienvenido a HaptiVoice, una aplicación
desarrollada para facilitar la comunicación
y ofrecer herramientas de accesibilidad.

Al registrarse, el usuario declara haber leído
y aceptado los presentes Términos y Condiciones.

La aceptación es obligatoria para completar
el registro de una cuenta nueva.

Si el usuario no está de acuerdo con estas
condiciones, puede abstenerse de crear
una cuenta y utilizar los servicios
que requieran registro.


2. OBJETIVO DE HAPTIVOICE

HaptiVoice tiene como finalidad apoyar
la comunicación entre personas mediante
herramientas tecnológicas de accesibilidad.

La aplicación ofrece funcionalidades
relacionadas con la voz, el texto,
la traducción y otras herramientas
de apoyo a la comunicación.

HaptiVoice es una herramienta complementaria
y no garantiza una comunicación perfecta
en todas las situaciones.


3. REGISTRO Y CUENTAS DE USUARIO

Para acceder a determinadas funciones,
el usuario debe crear una cuenta utilizando
su correo electrónico y contraseña,
o un proveedor de autenticación disponible,
como Google.

El usuario es responsable de:

- Proporcionar información correcta.
- Mantener protegidas sus credenciales.
- Evitar compartir su contraseña.
- Utilizar su cuenta de forma responsable.
- Informar de problemas de seguridad
  mediante los canales de soporte disponibles.

La aplicación permite administrar
determinados datos del perfil,
cambiar la contraseña y solicitar
la eliminación de la cuenta.

La eliminación de la cuenta puede implicar
la pérdida de los datos asociados,
según las condiciones descritas
en la Política de Privacidad.


4. FUNCIONALIDADES DE LA APLICACIÓN

HaptiVoice incorpora herramientas
destinadas a facilitar la comunicación.

Entre sus funcionalidades se encuentran:

A) RECONOCIMIENTO DE VOZ A TEXTO

Permite convertir la voz captada
por el micrófono en texto visible.

El resultado puede variar según
la pronunciación, el ruido ambiental,
el idioma y la calidad del micrófono.

B) SÍNTESIS DE VOZ

Permite reproducir mediante voz sintética
el contenido de texto compatible
con las funciones de la aplicación.

Las voces disponibles dependen del plan
del usuario y de los servicios compatibles.

C) TRADUCCIÓN DE IDIOMAS

HaptiVoice incorpora herramientas
para facilitar la traducción de contenido
entre español e inglés.

Las traducciones son automáticas
y pueden contener errores
o interpretaciones imprecisas.

D) DICCIONARIO Y GESTOS

La aplicación incluye un apartado
relacionado con un diccionario de gestos.

Algunas entradas pueden corresponder
a ejemplos o demostraciones.

Las funciones de captura y reconocimiento
de gestos mediante dispositivos externos
solo estarán disponibles cuando
se encuentren implementadas y habilitadas.

E) CONFIGURACIÓN Y ACCESIBILIDAD

El usuario puede administrar preferencias
disponibles, como determinadas opciones
de voz y datos de su perfil.

La disponibilidad de cada función
puede variar según la versión
de la aplicación y el dispositivo.


5. PLAN GRATUITO

HaptiVoice dispone de un plan gratuito
que permite utilizar las funcionalidades
identificadas como gratuitas
dentro de la aplicación.

Este plan puede incluir herramientas
de comunicación, transcripción,
reproducción de voz convencional
y otras opciones habilitadas.

Las funciones gratuitas pueden estar
sujetas a límites técnicos,
de disponibilidad o de uso.

El plan gratuito no incluye
automáticamente todas las funciones
disponibles en el plan Premium.


6. PLAN PREMIUM

HaptiVoice ofrece un plan Premium
mediante suscripciones administradas
a través de Google Play.

El acceso a las funciones Premium
requiere una suscripción válida
y activa.

Las funcionalidades identificadas
como Premium están restringidas
a los usuarios que cumplan
con esta condición.

Entre los beneficios Premium
se encuentra el acceso a las voces
de alta calidad de Google Cloud
Text-to-Speech Neural2.

Las voces Neural2 utilizan servicios
de procesamiento en la nube
para generar audio de alta calidad.

IMPORTANTE:

Las voces Google Cloud Neural2
son exclusivas para usuarios
con una suscripción Premium activa.

Los usuarios del plan gratuito
no podrán utilizar estas voces
Premium, aunque puedan disponer
de otras opciones de síntesis de voz.

El uso de las voces Premium puede
estar sujeto a límites de consumo,
duración, disponibilidad técnica
y controles de uso del servicio.

Tener una suscripción Premium
no significa disponer de un uso
ilimitado de los recursos en la nube.


7. PAGOS Y SUSCRIPCIONES

Las suscripciones Premium
se gestionan mediante Google Play.

Antes de realizar una compra,
Google Play muestra el precio,
la moneda, el período de facturación
y las condiciones aplicables.

El usuario debe revisar esta información
antes de confirmar su suscripción.

Los precios pueden variar
según el país, las condiciones
comerciales y los impuestos aplicables.

Cuando corresponda, las suscripciones
pueden renovarse automáticamente
según las condiciones mostradas
en Google Play.

El usuario puede administrar
o cancelar su suscripción
desde Google Play.

La cancelación no necesariamente
elimina el acceso Premium
de manera inmediata.

El acceso puede mantenerse
hasta el final del período
de suscripción ya pagado.

Al finalizar la vigencia,
las funciones exclusivas Premium
dejarán de estar disponibles,
salvo que exista otra suscripción
válida y activa.

Las compras pendientes o rechazadas
no otorgan acceso Premium
hasta que se cumplan las condiciones
de confirmación correspondientes.

Los reembolsos se gestionan
según las políticas de Google Play
y la normativa aplicable.


8. DISPONIBILIDAD DEL SERVICIO

HaptiVoice requiere conexión a Internet
para determinadas funcionalidades.

Algunos servicios utilizan
tecnologías de terceros,
incluidos servicios de Google
y Firebase.

Por este motivo, algunas funciones
pueden verse afectadas
por problemas de conexión,
mantenimiento, errores técnicos
o interrupciones externas.

HaptiVoice no garantiza
disponibilidad ininterrumpida
de todos sus servicios.


9. PRIVACIDAD Y DATOS PERSONALES

Para ofrecer sus funcionalidades,
HaptiVoice puede tratar información
relacionada con la cuenta,
las preferencias del usuario
y el uso de determinados servicios.

La aplicación utiliza Firebase
para funciones relacionadas
con la autenticación
y el almacenamiento de datos.

Algunas funciones de voz
y procesamiento de contenido
pueden utilizar servicios externos
necesarios para entregar
la funcionalidad solicitada.

Los detalles sobre qué datos
se recopilan, cómo se utilizan,
durante cuánto tiempo se conservan
y cómo se pueden ejercer
los derechos correspondientes
deben consultarse
en la Política de Privacidad.

La aceptación de estos términos
no sustituye la información
que debe entregarse
sobre el tratamiento de datos personales.


10. USO RESPONSABLE

El usuario se compromete
a utilizar HaptiVoice
de manera lícita y responsable.

No está permitido utilizar
la aplicación para:

- Suplantar la identidad de terceros.
- Realizar actividades fraudulentas.
- Vulnerar la seguridad del servicio.
- Intentar obtener acceso no autorizado.
- Interferir deliberadamente
  con los servicios de la aplicación.
- Utilizar funcionalidades
  de manera contraria
  a las leyes aplicables.

El incumplimiento de estas condiciones
puede dar lugar a medidas apropiadas
sobre el acceso al servicio,
según corresponda legalmente.


11. LIMITACIONES DE RESPONSABILIDAD

Los resultados de reconocimiento de voz,
traducción y síntesis de voz
pueden presentar errores.

HaptiVoice no garantiza
que todas las transcripciones,
traducciones o reproducciones
sean completamente precisas.

La aplicación no debe considerarse
un sustituto automático
de servicios profesionales
de interpretación o comunicación
cuando estos sean necesarios.

En situaciones de emergencia
o de alto riesgo,
se recomienda utilizar
medios de comunicación apropiados
y servicios especializados.

Estas limitaciones se aplicarán
sin perjuicio de los derechos
que correspondan al usuario
conforme a la legislación vigente.


12. CAMBIOS EN LAS FUNCIONALIDADES

HaptiVoice podrá recibir
actualizaciones destinadas
a corregir errores,
mejorar la accesibilidad
o incorporar nuevas funcionalidades.

Algunas características
pueden cambiar de disponibilidad
o requerir condiciones adicionales.

Las funciones que aún estén
en desarrollo no se consideran
garantizadas hasta que
sean habilitadas oficialmente.


13. MODIFICACIÓN DE LOS TÉRMINOS

Los presentes Términos y Condiciones
podrán actualizarse
para reflejar cambios
en la aplicación,
sus funcionalidades
o las obligaciones legales aplicables.

Cuando una modificación requiera
una nueva aceptación,
HaptiVoice deberá solicitarla
antes de permitir continuar
con las funciones correspondientes.


14. DECLARACIÓN DE ACEPTACIÓN

Al marcar la casilla de aceptación,
el usuario declara que:

- Ha tenido acceso a estos términos.
- Ha leído su contenido.
- Comprende las diferencias
  entre el plan gratuito
  y las funciones Premium.
- Entiende que las voces
  Google Cloud Neural2 requieren
  una suscripción Premium activa.
- Acepta las condiciones
  de utilización de HaptiVoice.

La aplicación registrará
la versión de los términos aceptados
y la fecha de dicha aceptación
como parte de los datos
asociados al perfil.

FIN DE LOS TÉRMINOS Y CONDICIONES.
""".trimIndent()