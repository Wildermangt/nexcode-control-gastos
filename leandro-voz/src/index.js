/**
 * leandro-voz — servidor mínimo que abre llamadas con el agente "Leandro" de Retell.
 *
 * Lo usan las tres apps de Nexcode (Control de Gastos, HuellitasPlus y el
 * Marketplace). Existe por una sola razón: la API key de Retell no puede ir
 * dentro de una app, porque cualquiera podría sacarla del APK. Aquí vive como
 * secreto del Worker y la app solo recibe un token de un solo uso.
 *
 *   POST /llamada   { "app": "gastos" | "huellitas" | "marketplace" }
 *   →  { call_id, access_token, ice_servers }
 *
 * Cloudflare Workers, plan gratuito (100 000 peticiones al día).
 */

/** Nombre con que el agente se refiere a cada app. */
export const APPS = {
  gastos: "Control de Gastos",
  huellitas: "HuellitasPlus",
  marketplace: "Marketplace",
};

const responder = (cuerpo, estado = 200) =>
  new Response(JSON.stringify(cuerpo), {
    status: estado,
    headers: { "Content-Type": "application/json; charset=utf-8" },
  });

export default {
  async fetch(request, env) {
    const { pathname } = new URL(request.url);

    if (request.method === "GET" && pathname === "/") {
      return responder({ servicio: "leandro-voz", ok: true, configurado: Boolean(env.RETELL_API_KEY) });
    }
    if (request.method !== "POST" || pathname !== "/llamada") {
      return responder({ error: "Ruta no encontrada" }, 404);
    }
    if (!env.RETELL_API_KEY) {
      return responder({ error: "Falta configurar RETELL_API_KEY en el servidor" }, 500);
    }

    let app;
    try {
      ({ app } = await request.json());
    } catch {
      return responder({ error: "Cuerpo JSON inválido" }, 400);
    }
    if (!Object.hasOwn(APPS, app)) {
      return responder({ error: "App desconocida. Usa gastos, huellitas o marketplace." }, 400);
    }

    const res = await fetch("https://api.retellai.com/v3/create-web-call", {
      method: "POST",
      headers: {
        Authorization: `Bearer ${env.RETELL_API_KEY}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        agent_id: env.RETELL_AGENT_ID,
        // Solo la última versión publicada: lo que se edita en el borrador no
        // llega a las apps hasta que se pulsa "Publicar" en Retell.
        agent_version: "latest_published",
        // El prompt del agente usa {{app}} para saber desde dónde le hablan.
        retell_llm_dynamic_variables: { app: APPS[app] },
        metadata: { app },
      }),
    });

    if (!res.ok) {
      console.error("Retell create-web-call", res.status, await res.text());
      return responder({ error: "Retell no pudo abrir la llamada" }, 502);
    }

    const llamada = await res.json();
    // A la app solo le llega lo necesario para conectarse.
    return responder({
      call_id: llamada.call_id,
      access_token: llamada.access_token,
      ice_servers: llamada.ice_servers ?? [],
    });
  },
};
