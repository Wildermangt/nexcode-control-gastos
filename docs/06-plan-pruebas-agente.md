# Control de Gastos — Plan de pruebas del agente de IA

Cubre el criterio de la rúbrica "se validaron las respuestas del agente mediante diferentes
escenarios de prueba" y "se controlan las respuestas incorrectas, las solicitudes no válidas
y los errores del servicio" (sección 8).

## Qué es el agente

Asistente financiero con **Gemini** (API de Gemini) que analiza el mes y responde preguntas
sobre las finanzas del usuario, por texto o por voz (`ReconocedorDeVoz` / `SintetizadorDeVoz`).
Cuando el agente no está disponible, la app **cae a un análisis local de reglas**, de modo que
nunca se queda sin respuesta (`GeminiAdvisor` → `respaldo`). La pantalla muestra siempre una
etiqueta de origen: *"Respondido por el agente de IA"* o *"Respondido sin conexión, con el
análisis local"*.

## Escenarios de validación

Ejecutar en el emulador/dispositivo y capturar cada uno en `docs/evidencias-pruebas/agente/`.

| # | Entrada | Resultado esperado | Criterio |
|---|---|---|---|
| T1 | "¿En qué estoy gastando de más?" (con movimientos del mes) | Resumen por categoría con el gasto más alto; etiqueta "agente de IA" | Respuestas pertinentes |
| T2 | "¿Cuánto llevo ahorrado este mes?" | Responde con el balance real (ingresos − gastos) | Integración con los datos |
| T3 | Registrar un gasto por voz: "gasté 20 mil en comida" | Entiende el monto y la categoría y lo registra o lo propone | Recibe entradas y actúa |
| T4 | Pregunta sin relación ("¿qué hora es?") | Redirige a temas financieros, no inventa datos | Respuestas incorrectas |
| T5 | Pregunta con el mes vacío (sin movimientos) | Indica que no hay movimientos, sin error | Caso límite |
| T6 | Modo avión / sin red | Cae al análisis local y muestra la etiqueta "sin conexión" | Error del servicio |
| T7 | Clave de API ausente (quitar `gemini.apiKey` de local.properties) | Muestra la tarjeta "El agente de IA no está configurado" y usa el análisis local | Degradación controlada |
| T8 | Micrófono sin permiso | Pide el permiso; si se niega, la app sigue funcionando por texto | Permisos |

## Manejo de errores (cómo responde la app)

| Situación | Comportamiento (ya implementado) |
|---|---|
| Sin conexión o timeout (`TIMEOUT_MILIS`) | Reintenta y, si falla, usa el análisis local; etiqueta "sin conexión" |
| Clave inválida o cuota agotada | Corta la llamada remota y responde con el análisis local |
| Respuesta del modelo mal formada | Se captura la excepción al parsear y se degrada al análisis local |
| Error de voz (`estadoVoz.error`) | Se muestra el mensaje en rojo en la tarjeta de escucha |
| Dispositivo sin reconocimiento de voz | Aviso "este dispositivo no tiene reconocimiento de voz" y queda la entrada por texto |

## Aviso al usuario

La pantalla del asistente muestra, bajo el título, el texto fijo: *"🤖 Estás hablando con un
asistente de inteligencia artificial. Sus respuestas pueden contener errores; revisa tus
finanzas antes de decidir."* Además, la etiqueta de origen deja claro si respondió el agente
remoto o el análisis local.

## Protección de la clave

Ver `docs/seguridad-permisos-y-autorizacion.md`. La clave de Gemini hoy se compila en el APK;
el fix recomendado (gratis) es enrutarla por Firebase AI Logic o restringirla en Google Cloud.
