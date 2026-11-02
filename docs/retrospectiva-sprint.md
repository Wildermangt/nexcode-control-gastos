# Nexcode · Control de Gastos — Retrospectiva del sprint

Sprint del 28 de octubre al 2 de noviembre de 2026 (versión 1.1).
Formato: qué se hizo, qué salió bien, qué mejorar y acciones con responsable.

## Qué se hizo

| Fecha | Entrega | Dónde quedó |
|---|---|---|
| 28/10 | Problema, alcance y requisitos trazados al código; mockups de las pantallas | `feature/planeacion-y-diseno` → `main` |
| 28/10 | URL del webhook de Make fuera del código (se lee de `local.properties`) | `fix/credenciales-y-respaldo` → `main` |
| 28/10 | Base Room y preferencias excluidas de los respaldos | `fix/credenciales-y-respaldo` → `main` |
| 28/10 | Documentación de pruebas, correcciones y pasos pendientes en las consolas | `main` |
| 30/10 | Webhook de Make protegido con un secreto compartido | `fix/seguridad-webhook-y-agente` |
| 30/10 | Sin gastos duplicados ni mensajes falsos cuando Gemini falla | `fix/seguridad-webhook-y-agente` |
| 30/10 | Pruebas en dispositivo, manual de usuario, plan de pruebas del agente, pruebas de autorización y conclusiones | `fix/seguridad-webhook-y-agente` → PR #1, fusionado el 02/11 |
| 30/10 | Botón «Hablar con Leandro» y servidor `leandro-voz` (Cloudflare Workers) para el agente de Retell AI | `feature/agente-leandro`, **sin fusionar** |

## Qué salió bien

- **La app sigue funcionando completa sin conexión**: Room primero y sincronización con
  Firestore cuando hay red.
- **El webhook de Make dejó de ser público**: la URL salió del APK, se rotó y cada aviso
  lleva un secreto compartido que Make valida.
- **El fallo de Gemini (503) ya no duplica gastos**: el asesor reconoce que el gasto ya se
  guardó y lo confirma aunque el modelo no conteste.
- **La separación en `app`, `domain` y `data` se comprobó en la práctica**: la voz se agregó
  sin tocar el dominio, y las 51 pruebas unitarias corren sobre él.
- **El servidor de voz `leandro-voz` trae sus propias pruebas** y deja la clave de Retell
  del lado del servidor (Cloudflare Workers), no en el APK.
- Se probó en un teléfono físico con Android 11, no solo en el emulador.

## Qué mejorar

- **La integración del agente de voz no se cerró.** `feature/agente-leandro` (botón en la
  pantalla del asistente y el Worker `leandro-voz`) todavía no tiene PR ni revisión.
- **La clave de API de Retell llegó inválida** porque se copió de una página traducida
  automáticamente por el navegador; el 401 genérico no ayudó a encontrar la causa.
- **Firma del APK y `google-services.json`**: en un clon nuevo faltaban los archivos locales
  y la APK salió con otra firma, así que no se pudo instalar sobre la versión anterior.
- **El antivirus AVG intercepta HTTPS** en uno de los equipos y bloqueó `git`, Gradle, npm y
  `wrangler` hasta configurar su certificado.
- En la copia local de trabajo quedaron carpetas sueltas sin versionar; conviene mantener
  limpio el área de trabajo para no subir algo por error.

## Acciones

| # | Acción | Responsable | Para |
|---|---|---|---|
| 1 | Abrir el PR de `feature/agente-leandro` con las pruebas de `leandro-voz` en verde y una llamada probada en teléfono físico | Leo / revisa Jeferson | Próximo sprint |
| 2 | Copiar claves solo desde la consola original con la traducción del navegador desactivada, y validarlas con una llamada de prueba | Equipo | Desde ya |
| 3 | Documentar qué archivos locales hacen falta (`local.properties`, `google-services.json`, keystore, secretos del Worker) y de dónde se obtienen, sin su contenido | Jeferson | Próximo sprint |
| 4 | Anotar cómo hacer que `git`, Gradle, npm y `wrangler` confíen en el certificado del antivirus sin desactivarlo | Leo | Próximo sprint |
| 5 | Presupuesto por categoría con alerta (el modelo y el cálculo ya existen) | Equipo | Backlog |
| 6 | Pruebas de interfaz automáticas con Compose UI Test | Equipo | Backlog |
