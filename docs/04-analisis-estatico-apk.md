# Nexcode · Control de Gastos — Análisis estático del APK (Kali Linux)

Prueba de seguridad de caja blanca sobre la APK de publicación, realizada el 29 de
octubre de 2026 en un laboratorio aislado.

## Entorno y método

| Componente | Detalle |
|---|---|
| Atacante | Kali Linux 2026.2 (VirtualBox), red Host-Only |
| APK analizada | `entregables/nexcode-1.1-release-2026-10-29.apk` (3,3 MB) |
| Herramientas | `apktool`, `jadx`, `unzip` + `strings`/`grep` sobre `classes.dex` |

Se decompiló la APK, se revisó el manifiesto (banderas, permisos, componentes
exportados) y se buscaron secretos en recursos y en el bytecode (`classes.dex`).

## Resultado general

| Control | Resultado |
|---|---|
| Firma | Esquema **v2** válido · huella SHA-256 `9f:c9:6c:2f…52:77:b9` |
| `android:allowBackup` | **false** ✔ (corrección del 28/10 presente en la APK) |
| `android:debuggable` | ausente ✔ |
| Tráfico en claro | sin `usesCleartextTraffic`; bloqueado por defecto con `targetSdk 35` ✔ |
| Ofuscación (R8) | **activada** (`isMinifyEnabled = true`): `BuildConfig` y clases internas aparecen renombrados en jadx ✔ |
| minSdk / targetSdk | 26 / 35 |

## Permisos

`INTERNET`, `RECORD_AUDIO` (asistente por voz), `POST_NOTIFICATIONS` y
`RECEIVE_BOOT_COMPLETED` (recordatorios). Ninguno excesivo.

## Componentes exportados

Solo `MainActivity` (launcher) y los componentes estándar de Firebase Auth, Google Play
Services, `WorkManager` y `profileinstaller`. Sin componentes propios expuestos de más.

## Búsqueda de secretos

| Qué se buscó | Resultado |
|---|---|
| Claves `AIza…` | **Una sola**, en `resources.arsc`: la clave de Firebase (`AIzaSyCb4is…Vp9c`), pública por diseño |
| Clave de Gemini | **No está en la APK.** Se buscó en `classes.dex` y no aparece ninguna clave `AIza` fuera de la de Firebase (0 coincidencias en el dex) |
| URL del webhook de Make | **Incrustada en `classes.dex`**: `https://hook.us2.make.com/o3hn…` (se verificó que la cadena está en el dex) |

## Hallazgos y recomendaciones

| ID | Hallazgo | Severidad | Recomendación |
|---|---|---|---|
| APK-01 | La URL del webhook de Make viaja en el `classes.dex` del APK de publicación. Moverla a `BuildConfig`/`local.properties` la sacó del código fuente y de git, **pero no del artefacto entregado**: cualquiera que extraiga el APK puede obtenerla y enviar movimientos falsos al escenario de Make (el mismo riesgo descrito en el hallazgo 1 del documento de pruebas) | MEDIA | Tratar el webhook como público: que Make valide un **secreto compartido/HMAC** que la app envíe en cada petición, o enrutar el aviso por un backend autenticado. Si la URL ya se expuso, rotarla en Make |
| APK-02 | Clave de API de Firebase en los recursos (inevitable, debe restringirse) | BAJA | En Google Cloud Console, restringir la clave por **aplicación Android** (paquete `com.nexcode.gastos` + SHA-256 de firma) y por **API** |
| APK-03 | La clave de Gemini **no** se encontró en el APK de publicación | — (positivo) | Confirma que el hallazgo 3 del documento de pruebas no afecta a este artefacto. Mantener la clave fuera de `local.properties` al generar la APK de entrega |

**Conclusión:** la APK aplica configuraciones seguras por defecto y está ofuscada con
R8. No filtra la clave de Gemini. El punto a corregir, de severidad media, es que la URL
del webhook de Make sigue siendo extraíble del APK; conviene protegerla con un secreto
que el servidor valide o moverla detrás de un backend autenticado.
