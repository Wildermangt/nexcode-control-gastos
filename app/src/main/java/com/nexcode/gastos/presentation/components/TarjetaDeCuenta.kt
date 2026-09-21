package com.nexcode.gastos.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexcode.gastos.cuenta.EstadoDeCuenta
import com.nexcode.gastos.presentation.theme.IncomeGreen
import com.nexcode.gastos.presentation.theme.NexcodeBlue
import com.nexcode.gastos.presentation.theme.TextPrimary
import com.nexcode.gastos.presentation.theme.TextSecondary
import com.nexcode.gastos.presentation.theme.WarningAmber

/** Que esta haciendo el usuario en el dialogo. */
private enum class Modo { PROTEGER, ENTRAR }

/**
 * La cuenta con la que se protege la copia en la nube.
 *
 * Existe porque la sesion anonima, por si sola, no basta: le da dueno a los
 * datos pero ese dueno muere con la instalacion. Mientras la tarjeta este en
 * ambar, la copia de la nube NO se puede recuperar si el telefono se pierde, y
 * el usuario merece saberlo antes de confiar en ella.
 */
@Composable
fun TarjetaDeCuenta(
    estado: EstadoDeCuenta,
    onProteger: (String, String) -> Unit,
    onEntrar: (String, String) -> Unit,
    onMensajeVisto: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Sin nube configurada no hay nada que proteger y la tarjeta sobra.
    if (!estado.hayNube) return

    var modo by remember { mutableStateOf<Modo?>(null) }

    NexcodeCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (estado.enlazada) IncomeGreen else WarningAmber)
            )
            Text(
                text = "Cuenta",
                color = TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 10.dp)
            )
        }

        Text(
            text = if (estado.enlazada) {
                "Protegida como " + estado.correo +
                    ". Puedes recuperar tus datos en otro telefono entrando con " +
                    "ese correo."
            } else {
                "Tu copia en la nube esta atada a este telefono. Si borras la " +
                    "aplicacion o cambias de equipo, no habra forma de recuperarla. " +
                    "Ponle un correo y una contrasena para que si se pueda."
            },
            color = TextSecondary,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 8.dp)
        )

        estado.mensaje?.let { mensaje ->
            Text(
                text = mensaje,
                color = TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (estado.procesando) {
            CircularProgressIndicator(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .size(20.dp),
                strokeWidth = 2.dp,
                color = NexcodeBlue
            )
        } else if (!estado.enlazada) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { modo = Modo.ENTRAR }) {
                    Text("Entrar en mi cuenta", color = TextSecondary, fontSize = 14.sp)
                }
                TextButton(onClick = { modo = Modo.PROTEGER }) {
                    Text(
                        text = "Proteger mi copia",
                        color = NexcodeBlue,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }

    modo?.let { activo ->
        DialogoDeCuenta(
            modo = activo,
            onCerrar = { modo = null },
            onConfirmar = { correo, clave ->
                onMensajeVisto()
                if (activo == Modo.PROTEGER) onProteger(correo, clave) else onEntrar(correo, clave)
                modo = null
            }
        )
    }
}

@Composable
private fun DialogoDeCuenta(
    modo: Modo,
    onCerrar: () -> Unit,
    onConfirmar: (String, String) -> Unit
) {
    var correo by remember { mutableStateOf("") }
    var clave by remember { mutableStateOf("") }

    // Firebase exige seis caracteres; comprobarlo aqui evita un viaje a la red
    // para recibir el mismo "no" un segundo despues.
    val valido = correo.contains("@") && correo.length > 4 && clave.length >= 6

    AlertDialog(
        onDismissRequest = onCerrar,
        title = {
            Text(
                text = if (modo == Modo.PROTEGER) "Proteger mi copia" else "Entrar en mi cuenta",
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = if (modo == Modo.PROTEGER) {
                        "Tus datos actuales se conservan: la cuenta se anade a esta " +
                            "misma copia, no crea otra."
                    } else {
                        "Se reemplazaran los datos de este telefono por los de la " +
                            "cuenta. Usalo en un equipo nuevo, no en el que ya tiene " +
                            "tus movimientos."
                    },
                    color = TextSecondary,
                    fontSize = 13.sp
                )

                OutlinedTextField(
                    value = correo,
                    onValueChange = { correo = it },
                    label = { Text("Correo") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                )

                OutlinedTextField(
                    value = clave,
                    onValueChange = { clave = it },
                    label = { Text("Contrasena") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )

                Text(
                    text = "Minimo seis caracteres.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valido,
                onClick = { onConfirmar(correo, clave) }
            ) {
                Text(
                    text = if (modo == Modo.PROTEGER) "Proteger" else "Entrar",
                    color = if (valido) NexcodeBlue else TextSecondary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onCerrar) {
                Text("Cancelar", color = TextSecondary)
            }
        }
    )
}
