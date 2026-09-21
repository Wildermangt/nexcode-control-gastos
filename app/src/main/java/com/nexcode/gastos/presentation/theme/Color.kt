package com.nexcode.gastos.presentation.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Paleta NEXCODE, la misma que usa NEXCODE Banca digital.
 *
 * Los tres tonos de marca salieron de muestrear los pixeles del logotipo:
 * azul profundo #104080, azul brillante #1080D0 y verde menta #40E0A0.
 * Nexcode Gastos reutiliza esos valores para que las dos aplicaciones se
 * vean como productos de la misma casa.
 */

// --- Marca ---
val NexcodeBlueDeep = Color(0xFF104080)      // nex_azul_profundo
val NexcodeBlue = Color(0xFF1080D0)          // nex_azul
val NexcodeBlueLight = Color(0xFF4FB3E8)     // nex_azul_claro
val NexcodeCyan = Color(0xFF5FD8E8)          // nex_cian
val NexcodeMint = Color(0xFF40E0A0)          // nex_verde
val NexcodeMintDeep = Color(0xFF17A97B)      // nex_verde_oscuro

/** Degradado de encabezado: azul profundo -> azul (el del banco). */
val HeaderGradientColors = listOf(NexcodeBlueDeep, Color(0xFF1568B4), NexcodeBlue)

/** Degradado de la tarjeta de saldo: los tres tonos del logotipo. */
val BalanceGradientColors = listOf(NexcodeBlueDeep, NexcodeBlue, NexcodeMint)

/** Degradado de botones: termina en verde oscuro para que el texto blanco resalte. */
val NexcodeGradientColors = listOf(NexcodeBlueDeep, NexcodeBlue, NexcodeMintDeep)

val NexcodeGradient: Brush = Brush.linearGradient(NexcodeGradientColors)

// --- Superficies (fondo blanco) ---
val NexcodeBackground = Color(0xFFFFFFFF)        // fondo de la app: blanco
val NexcodeSurface = Color(0xFFFFFFFF)           // tarjetas blancas
val NexcodeSurfaceSoft = Color(0xFFF7FAFD)       // nex_superficie_suave
val NexcodeSurfaceElevated = Color(0xFFF2F6FB)   // nex_fondo, para chips y realces
val DividerSoft = Color(0xFFE2E9F2)              // nex_borde

// --- Texto ---
val TextPrimary = Color(0xFF0E1C33)              // nex_texto
val TextMedium = Color(0xFF4A5B72)               // nex_texto_medio
val TextSecondary = Color(0xFF8494A8)            // nex_texto_suave
val TextOnBrand = Color(0xFFFFFFFF)              // nex_texto_sobre_marca

// --- Estados ---
val ExpenseRed = Color(0xFFE5484D)               // nex_rojo
val WarningAmber = Color(0xFFF5A524)             // nex_ambar
val IncomeGreen = NexcodeMintDeep                // nex_exito

// --- Transparencias sobre el degradado de marca ---
val WhiteAlpha10 = Color(0x1AFFFFFF)
val WhiteAlpha15 = Color(0x26FFFFFF)
val WhiteAlpha20 = Color(0x33FFFFFF)
val WhiteAlpha70 = Color(0xB3FFFFFF)
