# Control de Gastos — Seguridad: permisos, autorización y agente de IA

## 1. Permisos declarados y justificación

El `AndroidManifest.xml` declara solo tres permisos.

| Permiso | Para qué se usa | ¿Peligroso? |
|---|---|---|
| `INTERNET` | Firebase, Gemini y el webhook de Make. | No |
| `POST_NOTIFICATIONS` | Avisar de movimientos y alertas de presupuesto. | Sí (Android 13+) |
| `RECORD_AUDIO` | Dictado por voz del asistente financiero (`ReconocedorDeVoz`). | Sí (en tiempo de ejecución) |

`RECORD_AUDIO` se solicita en tiempo de ejecución al abrir el asistente por voz; la app
funciona sin él usando la entrada de texto.

## 2. Control de acceso y autorización

- **Autenticación**: Firebase Auth con correo y contraseña.
- **Reglas de Firestore** (`firestore.rules`): cada transacción y presupuesto se restringe
  por `uid`; el borrado es lógico (`borrado = 1`) y se sincroniza a la nube.

### Plan de pruebas de autorización entre usuarios

| # | Caso | Resultado esperado |
|---|---|---|
| A1 | Usuario A intenta leer los movimientos del usuario B | Denegado por reglas (`uid` distinto) |
| A2 | Usuario sin sesión intenta leer/escribir | Denegado (`request.auth != null`) |
| A3 | Usuario A intenta crear/modificar un pago fijo o cuenta de B | Denegado |

**Ejecutado el 29/10/2026 en *Firestore → Rules → Playground*: todos los casos quedaron
denegados y el control (el dueño lee lo suyo) permitido.** El admin (claim `admin: true`) puede
leer datos ajenos por diseño (soporte), pero **no** escribirlos. Pasos y resultados completos
en `docs/pruebas-autorizacion-firestore.md`.

> Corrección 29/10: la regla de lectura del admin pasó a `request.auth.token.get('admin',
> false) == true` para que una cuenta sin el claim produzca una denegación limpia (antes daba
> "Property admin is undefined").

## 3. Agente de IA (GeminiAdvisor + voz)

- **Modelo**: API REST de Gemini (`x-goog-api-key`), con voz (`SpeechRecognizer` /
  `TextToSpeech`).
- **Pruebas**: documentar escenarios (sin red, respuesta vacía, pregunta fuera de tema).
- **Manejo de errores**: hay try/catch; falta documentar los casos de error.

### Hallazgo crítico: la clave de Gemini viaja en el APK

`app/build.gradle.kts` toma `gemini.apiKey` de `local.properties` (bien, no se versiona),
pero la inyecta como `BuildConfig.GEMINI_API_KEY`, que **queda compilada dentro del APK** y
se puede extraer con `apktool`. `AppContainer.kt` la pasa a `GeminiAdvisor`, que llama a la
API de Google directamente desde el teléfono.

**Correcciones, de mayor a menor esfuerzo** (todas dentro del nivel gratuito, que para este
proyecto es criterio eliminatorio):

1. **Recomendada**: enrutar Gemini por **Firebase AI Logic** (como HuellitasPlus), que tiene
   nivel gratuito; así el teléfono no lleva la clave. Una **Cloud Function / backend** logra
   lo mismo, pero solo es válida si se mantiene dentro del *free tier* (vigilar invocaciones
   y egreso).
2. **Mínima e inmediata**: restringir la clave en **Google Cloud Console → Credentials**:
   - *Application restriction*: apps Android con el nombre de paquete `com.nexcode.gastos`
     y la huella **SHA-1** del certificado de firma.
   - *API restriction*: solo la *Generative Language API*.
   - Así, aunque se extraiga, no sirve desde otra app ni para otros servicios.
3. Rotar la clave actual, ya que pudo quedar expuesta en APK entregados.

### Pendientes del agente (rúbrica, sección 8)

- **Aviso de IA**: mostrar en la pantalla del asistente un texto visible del tipo
  *"Estás hablando con un asistente de IA; sus respuestas pueden contener errores"*.
- **Plan de pruebas del agente**: hoy no existe uno; crear uno equivalente al
  `PLAN_DE_PRUEBAS_AGENTE_VOZ.md` de HuellitasPlus.

## 3.b Restricción de la clave de API de Firebase (30/10/2026)

**Importante: en este proyecto hay dos claves distintas.**

1. **Android key de Firebase** (la de `google-services.json`, que usan Auth y Firestore). Se
   restringió en Google Cloud Console a **Apps de Android**, con el paquete `com.nexcode.gastos`
   y las dos huellas SHA-1 (debug `35:90:BD:…:5C:6F` y release `87:1A:95:…:47:E2`). La
   restricción por API se dejó sin cambios (25 APIs). No apareció aviso de uso activo. Captura:
   `key-restriccion-gastos.png`. Esto cierra el hallazgo del análisis estático sobre la clave de
   Firebase sin restringir.
2. **Clave de Gemini** (`BuildConfig.GEMINI_API_KEY`, usada por REST desde `GeminiAdvisor`). Es
   **otra credencial**, no la Android key, por lo que la restricción anterior **no la afecta**.
   El análisis estático confirmó que esta clave **no viaja en el APK de entrega** (`docs/04`,
   APK-03). Para endurecerla más, se puede restringir en Google Cloud a *Generative Language
   API* con una clave propia (ver sección 3, punto 2). A diferencia de HuellitasPlus y
   Marketplace, el asistente de Gastos no usa Firebase AI Logic, así que la restricción por app
   de la Android key no toca al asistente.

## 3.c Webhook de Make (hallazgo APK-01) — mitigado 30/10/2026

El análisis estático marcó que la URL del webhook de Make es extraíble del APK (APK-01, MEDIA).
Mitigación aplicada:

1. **Rotación:** se generó un webhook nuevo en Make (la URL anterior quedó en el historial de
   git); la app lee la URL nueva desde `make.webhookUrl` en `local.properties`.
2. **Secreto compartido por cabecera.** La app envía la cabecera `x-make-apikey`
   (`BuildConfig.MAKE_WEBHOOK_SECRET`, leído de `make.webhookSecret` en `local.properties`). Al
   configurar "Autenticación con clave API" en el webhook, el *gateway* de Make **rechaza con 401
   antes de ejecutar** cualquier petición sin la clave correcta, sin gastar operaciones del plan
   gratuito (ventaja frente al filtro, que consumía una operación por petición falsa). De forma
   transitoria la app también manda el campo `secreto` en el cuerpo, para no cortar el servicio
   mientras se activa la clave; una vez validada, se puede retirar el filtro y el campo del cuerpo.

> Residual (riesgo BAJO, aceptado): la clave también viaja dentro del APK, por lo que esto mitiga
> la fuga por git y sube la barrera, pero no es protección total. Lo 100 % robusto sería un
> backend con validación HMAC; se omite por la restricción de "todo en nivel gratuito" (no hay
> servidor propio para Control de Gastos).
>
> Nota: al borrar el webhook anterior se descartaron 7 mensajes en cola; uno era un movimiento
> real del 7/10 ("camioneta") que nunca llegó a la hoja porque el escenario estaba inactivo. Si
> era válido, registrarlo a mano.

## 4. Almacenamiento y comunicaciones

- `allowBackup="false"`; base de datos excluida de respaldos y transferencias.
- El webhook de Make está fuera del código (URL y secreto en `local.properties`) y valida un
  secreto compartido (ver sección 3.c).
- Todas las URL son HTTPS.

## 5. Análisis estático

Ver `docs/04-analisis-estatico-apk.md`.
