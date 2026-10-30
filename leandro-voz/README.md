# leandro-voz · servidor de llamadas con el agente Leandro

Servidor mínimo (Cloudflare Workers, plan gratuito) que abre las llamadas de voz con
**Leandro - Agente Nexcode**, el agente de Retell AI compartido por las tres apps.

```
 App (Control de Gastos / HuellitasPlus / Marketplace)
   │  POST /llamada {"app": "gastos" | "huellitas" | "marketplace"}
   ▼
 leandro-voz (Worker) ── guarda la API key de Retell ──▶ Retell /v3/create-web-call
   │                                                     (agente publicado + variable {{app}})
   ◀── call_id + access_token ──┘
   │
 App ── WebRTC (audio) ──▶ Retell ── Leandro, con la voz clonada
```

**Por qué existe:** la API key de Retell no puede ir dentro de un APK, porque
cualquiera podría extraerla y gastar el crédito. Aquí vive como secreto del Worker y
la app solo recibe un token de un solo uso.

## Desplegar (una sola vez, desde tu computador)

Necesitas Node 18 o superior y una cuenta gratuita de Cloudflare.

1. **API key de Retell.** En el panel de Retell (cuenta TellerUniversitario) ve a
   **Settings → API Keys** y copia la clave.
2. En esta carpeta:

   ```bash
   cd leandro-voz
   npm install
   npx wrangler login                    # abre el navegador para entrar a Cloudflare
   npx wrangler secret put RETELL_API_KEY   # pega la clave cuando la pida
   npx wrangler deploy
   ```

3. Anota la URL que imprime, por ejemplo `https://leandro-voz.<tu-subdominio>.workers.dev`.
   Si la abres en el navegador debe responder
   `{"servicio":"leandro-voz","ok":true,"configurado":true}`.

## Conectar cada app

En el `local.properties` de **cada** proyecto Android (no se sube a GitHub) agrega:

```
leandro.servidorUrl=https://leandro-voz.<tu-subdominio>.workers.dev
```

Compila de nuevo y el botón **Hablar con Leandro** queda funcionando:

| App | Dónde está el botón | Valor de `app` |
|---|---|---|
| Control de Gastos | Pestaña del asistente, bajo el título | `gastos` |
| HuellitasPlus | Pestaña del asistente con IA, bajo el aviso | `huellitas` |
| Marketplace | Pendiente: falta el repositorio | `marketplace` |

## Cambiar el agente

El servidor usa siempre la **última versión publicada** del agente
(`agent_version: "latest_published"`). Lo que se edite en Retell no llega a las apps
hasta pulsar **Publicar**. El `agent_id` está en `wrangler.toml`.

## Costos y límites

- Cloudflare Workers gratuito: 100 000 peticiones al día.
- Retell cobra por minuto de llamada. El agente cuelga a los ~3 minutos o tras 41 s
  de silencio.
- **Riesgo conocido:** el servidor no pide inicio de sesión, así que quien conozca la
  URL podría abrir llamadas y gastar crédito. Para una demo de clase es aceptable; no
  publiquen la URL. Para producción habría que exigir el token de Firebase de cada app.

## Pruebas

```bash
npm test
```
