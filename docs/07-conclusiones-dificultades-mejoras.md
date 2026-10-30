# Nexcode · Control de Gastos — Conclusiones, dificultades y mejoras

Estado al 30 de octubre de 2026 (versión 1.1). El análisis completo, con la discusión de
cada decisión, está en el informe IEEE del proyecto.

## Conclusiones

- Se entregó una aplicación Android nativa en Kotlin que registra ingresos y gastos,
  maneja varias cuentas, pagos fijos con recordatorio, resumen por categoría y exportación
  a CSV, y que **funciona completa sin conexión**: todo se escribe primero en Room y
  `MotorDeSincronizacion` lo replica en Cloud Firestore cuando hay red.
- La identidad en la nube parte de una **sesión anónima** de Firebase Authentication, que
  no le pide nada al usuario. Vincular correo y contraseña (`linkWithCredential`) conserva
  el mismo `uid`, así que los datos ya subidos se recuperan desde otro teléfono.
- El **asistente con IA** (Gemini) registra gastos por texto y por voz y responde sobre el
  mes. La voz se agregó sin tocar la capa de dominio: el reconocimiento del sistema llama al
  mismo caso de uso que la entrada escrita. Esa es la prueba práctica de que la separación
  en tres módulos (`app`, `domain`, `data`) funciona.
- La arquitectura es **verificable, no solo declarada**: el módulo `domain` compila también
  para JavaScript (Kotlin Multiplatform), lo que reveló dos dependencias de Java que ninguna
  revisión manual había visto. Las 51 pruebas unitarias corren sobre ese dominio.
- Los importes se guardan como enteros dentro de un tipo propio y los errores viajan como
  resultados sellados, no como excepciones. Por eso la interfaz nunca recibe una excepción
  sin manejar.

## Dificultades encontradas y cómo se resolvieron

| Dificultad | Solución aplicada |
|---|---|
| La navegación desde la notificación de un pago fijo funcionaba con la app cerrada y fallaba con la app abierta | El destino se lee también en `onNewIntent`, no solo en `onCreate` |
| El nivel gratuito de Gemini responde a veces 503 («high demand»). Si fallaba después de guardar el gasto, la app mostraba un mensaje falso y podía registrar el gasto dos veces | `GeminiAdvisor` reconoce que el gasto ya se guardó: no lo repite y confirma el registro aunque el modelo no conteste (corrección 5 de `03-pruebas-y-correcciones.md`) |
| La URL del webhook de Make quedaba en el código y en el APK, y cualquiera podía enviar movimientos falsos | La URL sale de `local.properties`, se rotó, y cada aviso lleva un **secreto compartido** que Make valida con un filtro |
| `allowBackup="true"` copiaba la base de movimientos financieros en los respaldos de Google | `allowBackup="false"` y reglas que excluyen la base y las preferencias |
| El perfil mostraba «1.0» escrito a mano | Se usa `BuildConfig.VERSION_NAME` |
| Comprobar la voz y las notificaciones solo en el emulador dejaba casos sin probar | Se probó también en un teléfono físico con Android 11, que transcribió voz real y ejercitó la ruta sin el permiso de notificaciones de Android 13 |

## Mejoras futuras

- **Presupuesto por categoría con alerta.** Es la de mejor relación esfuerzo/impacto: el
  modelo `Category` ya tiene el presupuesto mensual y el cálculo de su consumo está probado.
  Falta la pantalla y conectarlo con el asistente.
- **Pruebas de interfaz automáticas** (Compose UI Test). Hoy los casos de sistema se
  repiten a mano en cada entrega.
- **App Check** (Play Integrity) cuando la app se publique en Google Play, para que solo la
  app firmada pueda usar los servicios de Firebase.
- **Modo oscuro**: el sistema de color ya lo permite.
- **Versión para iOS** del módulo de dominio: el código común no lo impide, pero compilar
  para iOS exige un equipo con macOS.
