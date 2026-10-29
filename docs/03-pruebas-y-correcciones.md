# Nexcode · Control de Gastos · Pruebas y correcciones

## 1. Pruebas unitarias

La lógica de negocio vive en el módulo `:domain`, que es Kotlin puro, así que se prueba sin emulador. `./gradlew :domain:allTests` ejecuta las **51 pruebas** en JVM y en JavaScript; `./gradlew :domain:jvmTest` solo en JVM.

| Clase de prueba | Qué verifica | Requisitos |
|---|---|---|
| `AddTransactionUseCaseTest` | Registro de movimientos y rechazo de montos inválidos | RF-01, RNF-05 |
| `UpdateAccountBalanceUseCaseTest` | Ajuste del saldo de una cuenta: aumentar, disminuir sin quedar en negativo y rechazar montos inválidos | RF-04 |
| `GetCategorySummaryUseCaseTest` | Resumen del mes por categoría, ordenado y con porcentajes que suman 100 % | RF-05 |
| `AnalizarTendenciaUseCaseTest` | Tendencia mensual, variación frente al mes anterior y proyección del cierre | RF-06 |
| `HerramientasDelAgenteTest` | Herramientas del asistente: resumen, búsqueda, pagos pendientes y registro de gastos | RF-08, RF-09 |
| `MoneyTest` | Lectura de montos en formato colombiano, redondeo, límites y formato con punto de miles | RNF-05 |

## 2. Casos de uso para probar en el dispositivo

Plan de prueba manual para la sustentación. Al ejecutarlo, anote la fecha y el resultado obtenido de cada caso.

| Caso | Pasos | Resultado esperado |
|---|---|---|
| Registrar gasto | Botón + → gasto de $ 15.000 en Comida desde Nequi | Aparece en Últimos movimientos y baja el saldo de Nequi |
| Monto inválido | Escribir «abc» o 0 en el monto y guardar | La app muestra el error y no guarda |
| Editar y eliminar | Editar el monto de un gasto y luego deslizarlo para eliminarlo | El saldo se recalcula en ambos casos |
| Voz | Dictar «gasté ocho mil en bus» | Se registra $ 8.000 en Transporte |
| Sin red | Modo avión, registrar un gasto | Se guarda en el teléfono y se sincroniza al volver la red |
| Sincronización de borrados | Con sesión iniciada, eliminar un movimiento | En Firestore el documento queda con `borrado = true` |
| Exportar | Movimientos → Exportar CSV | Se abre el menú para compartir el archivo |

## 3. Correcciones de la revisión del 28 de octubre de 2026

| # | Hallazgo | Riesgo | Corrección | Verificación |
|---|---|---|---|---|
| 1 | La URL del webhook de Make estaba escrita en `AvisoDeMovimiento.kt`. Quien la conozca puede enviar movimientos falsos a la hoja de cálculo | Medio | La URL se lee de `make.webhookUrl` en `local.properties` (no se versiona) mediante `BuildConfig.MAKE_WEBHOOK_URL`. Sin ella, la app funciona igual y solo omite el aviso | `:app:assembleDebug` compila |
| 2 | `android:allowBackup="true"`: la base Room con los movimientos financieros se copiaba en los respaldos de Google | Medio | `allowBackup="false"` y reglas que excluyen la base y las preferencias, también en la transferencia entre teléfonos (Android 12+) | `:app:assembleDebug` compila |
| 3 | La clave de Gemini se lee de `local.properties`, pero `BuildConfig` la incluye en el APK, de donde se puede extraer | Medio | Pendiente en la consola: restringir la clave en Google Cloud (ver sección 4). La solución definitiva es pasar el agente a Firebase AI Logic | Pendiente |
| 4 | La revisión inicial no encontró eliminaciones en Firestore | Ninguno | Se verificó que sí existen: el borrado es lógico (`borrado = 1`) y `MotorDeSincronizacion` lo sube a Firestore | No requiere cambios |

## 4. Pasos pendientes en las consolas

1. **Restringir la clave de Gemini.** Google Cloud Console → APIs y servicios → Credenciales → la clave de Gemini:
   - *Restricciones de aplicación:* **Apps de Android**, con el paquete `com.nexcode.gastos` y la huella SHA-1 del certificado de firma (`./gradlew signingReport`).
   - *Restricciones de API:* solo **Generative Language API**.
2. **Cambiar el webhook de Make.** La URL anterior quedó en el historial público de GitHub. En Make, abra el escenario *NEXCODE Gastos - Registro de movimiento*, genere un webhook nuevo y escriba la URL nueva en `make.webhookUrl` de `local.properties`.
