// Pruebas sin red: npm test (Node 20+). La API de Retell se simula.
import { test } from "node:test";
import assert from "node:assert/strict";
import worker from "../src/index.js";

const env = { RETELL_API_KEY: "key_prueba", RETELL_AGENT_ID: "agent_x" };
const pedir = (cuerpo, metodo = "POST", ruta = "/llamada") =>
  new Request("https://leandro-voz.test" + ruta, {
    method: metodo,
    body: metodo === "POST" ? (typeof cuerpo === "string" ? cuerpo : JSON.stringify(cuerpo)) : undefined,
  });

function simularRetell(respuesta, estado = 201) {
  const enviados = [];
  globalThis.fetch = async (url, op) => {
    enviados.push({ url, op, cuerpo: JSON.parse(op.body) });
    return new Response(JSON.stringify(respuesta), { status: estado });
  };
  return enviados;
}

test("abre la llamada y le dice al agente desde qué app viene", async () => {
  const enviados = simularRetell({ call_id: "c1", access_token: "t1", ice_servers: [{ urls: "stun:x" }], agent_id: "agent_x" });
  const res = await worker.fetch(pedir({ app: "huellitas" }), env);
  assert.equal(res.status, 200);
  assert.deepEqual(await res.json(), { call_id: "c1", access_token: "t1", ice_servers: [{ urls: "stun:x" }] });
  assert.equal(enviados[0].url, "https://api.retellai.com/v3/create-web-call");
  assert.equal(enviados[0].op.headers.Authorization, "Bearer key_prueba");
  assert.equal(enviados[0].cuerpo.agent_id, "agent_x");
  assert.equal(enviados[0].cuerpo.agent_version, "latest_published");
  assert.deepEqual(enviados[0].cuerpo.retell_llm_dynamic_variables, { app: "HuellitasPlus" });
});

test("rechaza apps desconocidas y cuerpos inválidos sin llamar a Retell", async () => {
  const enviados = simularRetell({});
  assert.equal((await worker.fetch(pedir({ app: "otra" }), env)).status, 400);
  assert.equal((await worker.fetch(pedir({ app: "__proto__" }), env)).status, 400);
  assert.equal((await worker.fetch(pedir("no es json"), env)).status, 400);
  assert.equal(enviados.length, 0);
});

test("no expone la API key y avisa si falta", async () => {
  simularRetell({ call_id: "c1", access_token: "t1" });
  const texto = await (await worker.fetch(pedir({ app: "gastos" }), env)).text();
  assert.ok(!texto.includes("key_prueba"));
  const sinClave = await worker.fetch(pedir({ app: "gastos" }), { RETELL_AGENT_ID: "a" });
  assert.equal(sinClave.status, 500);
});

test("propaga un error de Retell como 502", async () => {
  simularRetell({ error: "x" }, 401);
  const res = await worker.fetch(pedir({ app: "marketplace" }), env);
  assert.equal(res.status, 502);
});

test("estado y rutas", async () => {
  const ok = await worker.fetch(pedir(null, "GET", "/"), env);
  assert.deepEqual(await ok.json(), { servicio: "leandro-voz", ok: true, configurado: true });
  assert.equal((await worker.fetch(pedir(null, "GET", "/llamada"), env)).status, 404);
});
