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
| 5 | Al registrar un gasto con el asistente, si Gemini fallaba **después** de guardarlo (503 «high demand» del nivel gratuito), la app respondía «(Sin conexión con el agente) Todavía no tengo movimientos de este mes» aunque el gasto sí quedó guardado. Además se reintentaba la conversación con otro modelo desde cero, lo que podía registrar el gasto dos veces | Medio: mensaje falso y riesgo de duplicados | `GeminiAdvisor.conversar()` recuerda los gastos registrados; si la API falla después, responde «Listo, registré: …» sin reintentar con otro modelo | `assembleRelease` y 51 pruebas en verde. El error se observó el 29/10 a las 09:59 (gasto «Bus» de $ 8.000 creado con el mensaje falso). El camino corregido no se pudo reproducir en vivo porque depende de un 503 justo después del registro |
| 6 | Perfil mostraba «Nexcode 1.0» escrito a mano, aunque la APK es la 1.1 | Bajo | Se usa `BuildConfig.VERSION_NAME` | Perfil muestra «Nexcode 1.1» (captura 19) |

## 4. Pasos pendientes en las consolas

1. **Restringir la clave de Gemini.** Google Cloud Console → APIs y servicios → Credenciales → la clave de Gemini:
   - *Restricciones de aplicación:* **Apps de Android**, con el paquete `com.nexcode.gastos` y la huella SHA-1 del certificado de firma (`./gradlew signingReport`).
   - *Restricciones de API:* solo **Generative Language API**.
2. **Cambiar el webhook de Make.** La URL anterior quedó en el historial público de GitHub. En Make, abra el escenario *NEXCODE Gastos - Registro de movimiento*, genere un webhook nuevo y escriba la URL nueva en `make.webhookUrl` de `local.properties`.

## 5. Ejecución de los casos en el dispositivo (29 de octubre de 2026)

Emulador Pixel 6 (API 35) con la APK de publicación `entregables/nexcode-1.1-release-2026-10-29.apk`, sobre los datos existentes de la app. Los movimientos de prueba se eliminaron al terminar. Capturas en `docs/evidencias-pruebas/`.

| Caso | Resultado obtenido | Estado | Capturas |
|---|---|---|---|
| Registrar gasto | $ 15.000 en Comida desde Nequi: aparece en movimientos y el disponible de Nequi pasa de $ -25.800 a $ -40.800 | ✅ | 01, 04–07 |
| Monto inválido | «abc» → «"abc" no es un monto valido»; 0 → «El monto debe ser mayor que cero». No guarda | ✅ | 02, 03 |
| Editar y eliminar | Editar a $ 20.000 deja Nequi en $ -45.800; al deslizar para eliminar vuelve a $ -25.800. **El borrado es inmediato, sin confirmación ni deshacer** | ✅ (observación) | 08–10 |
| Voz | Probada en un teléfono físico (Motorola moto g power). El dictado funciona: la app transcribió «¿Cuánto me queda?» y el agente respondió con los datos reales, y transcribió «gasté ocho mil en bus» (reconocido como «caste $8000 en un bus»). El **registro por voz** no se pudo completar en vivo porque el plan gratuito de Gemini dio *timeout* repetido; en ese caso la app degrada al análisis local **sin registrar un gasto falso** (hallazgo 5 corregido). No quedó ningún dato de prueba en el teléfono | ✅ dictado y agente · ⚠️ registro depende de Gemini (plan gratuito) | 11, 20, 21, 22 |
| Sin red | En modo avión se registra $ 9.000 en Transporte; al volver la red, «Sincronizar ahora» → «Listo: 3 enviados y 0 recibidos» | ✅ | 13–15 |
| Sincronización de borrados | Eliminar el movimiento y sincronizar → «Listo: 1 enviados». El campo `borrado = true` en Firestore no se revisó en la consola | ✅ (desde la app) | 16 |
| Exportar | Movimientos → Exportar CSV abre el menú para compartir `nexcode-movimientos-2026-10-29.csv` | ✅ | 17, 18 |
