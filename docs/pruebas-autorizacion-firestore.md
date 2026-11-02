# Pruebas de autorización entre usuarios — Firestore Rules Playground

Cubre el criterio **"Pruebas de autorización entre usuarios"** de la sección 9 en los tres
proyectos. El Playground simula una petición contra las reglas publicadas y dice si queda
**Permitida** o **Denegada**, sin tocar datos reales. De cada caso se adjunta una captura.

## Cómo llegar al Playground

1. Abrir el link del proyecto (abajo) → pestaña **Reglas**.
2. Botón **Rules Playground** (esquina inferior, o el ícono de "play"/"laboratorio").
3. En cada corrida se configura:
   - **Tipo de simulación**: `get` (leer un doc), `list` (listar), `create`, `update`, `delete`.
   - **Ubicación**: la ruta del documento (p. ej. `/usuarios/UID_B/perros/perro1`).
   - **Autenticado**: interruptor ON/OFF. Si ON, se escribe un **Firebase UID** (puede ser
     inventado, p. ej. `UID_A`) y, si hace falta, *custom claims* (p. ej. `admin: true`).
   - Para `create`/`update` se construye el **documento** (los datos que se intentan escribir).
4. **Ejecutar** y capturar el resultado (Permitida ✅ / Denegada ⛔).

> **Antes de empezar:** confirmar que las reglas publicadas en la consola son las mismas del
> repositorio (`firestore.rules` de cada proyecto). Si no, pegarlas y **Publicar** primero;
> si no, el Playground prueba reglas viejas.

Convención: `UID_A` = usuario atacante, `UID_B` = víctima (son UIDs cualesquiera, distintos).

---

## 1. Control de Gastos — `nexcode-gastos`

Link: https://console.firebase.google.com/project/nexcode-gastos/firestore/rules

Regla: cada usuario solo alcanza `usuarios/{su propio uid}/**`. Solo un token con
`admin == true` puede **leer** todo (nunca escribir).

**Resultado 29/10/2026: todos los ataques quedan denegados. ✅ Aprueba.**

| # | Simulación | Ubicación | Autenticado | Resultado |
|---|---|---|---|---|
| CTRL | `get` | `/usuarios/USER_A/movimientos/m1` | ON, UID = `USER_A` | ✅ Permitido (su propio subárbol) |
| A1 | `get` | `/usuarios/USER_A/movimientos/m1` | ON, UID = `USER_B` | ❌ Denegado (uid distinto) |
| A2 | `get` | `/usuarios/USER_A/movimientos/m1` | **OFF** (sin sesión) | ❌ Denegado (`request.auth == null`) |
| A3 | `create` | `/usuarios/USER_A/pagos_fijos/p1` | ON, UID = `USER_B` | ❌ Denegado |
| A4 | `update` | `/usuarios/USER_A/cuentas/c1` | ON, UID = `USER_B` | ❌ Denegado |
| A5a | `get` | movimiento ajeno | ON, claim `admin: true` | ✅ Permitido (así está diseñado el soporte) |
| A5b | `update` | movimiento ajeno | ON, claim `admin: true` | ❌ Denegado (el admin solo lee, no altera) |

> **Corrección 29/10:** antes, A1 se denegaba con "Property admin is undefined" (se veía mal
> en la captura). Se cambió la regla a `request.auth.token.get('admin', false) == true`, así
> una cuenta sin el claim produce una **denegación limpia**. Volver a correr A1 tras publicar.

---

## 2. HuellitasPlus — `huellitasplus-usb`

Link: https://console.firebase.google.com/project/huellitasplus-usb/firestore/rules

Reglas: `usuarios/{uid}` y sus subcolecciones (`perros`, `cuidados`) solo las ve su dueño o un
admin; nadie puede auto-asignarse `rol: 'admin'`; una conversación solo la ven sus dos
participantes (privacidad del chat, Punto 9).

**Resultado 29/10/2026: todos los ataques quedan denegados. ✅ Aprueba.**

| # | Simulación | Ubicación | Autenticado | Documento / nota | Resultado |
|---|---|---|---|---|---|
| CTRL | `get` | `/usuarios/USER_A/perros/p1` | ON, UID = `USER_A` | — | ✅ Permitido (su propio perro) |
| A1 | `get` | `/usuarios/USER_A/perros/p1` | ON, UID = `USER_B` | — | ❌ Denegado (no es el dueño ni admin) |
| A2 | `get` | `/usuarios/USER_A` | **OFF** | — | ❌ Denegado (sin sesión) |
| A3 | `get` | conversación real en la que USER_B no participa | ON, UID = `USER_B` | se evaluó el documento real | ❌ Denegado (no es participante) |
| A3-ctrl | `get` | la misma conversación | ON, UID = un participante real | — | ✅ Permitido |
| A4 | `create` | `/usuarios/USER_B` | ON, UID = `USER_B` | documento con `rol: "admin"` | ❌ Denegado (no se puede subir a admin) |

> A4 es el caso estrella de HP: aunque el usuario esté autenticado **como él mismo**, la regla
> le impide crearse/ascenderse con `rol: admin`. A3 se probó contra una conversación real, lo
> que hace la evidencia más fuerte.

---

## 3. Marketplace — `nexcode-marketplace`

Link: https://console.firebase.google.com/project/nexcode-marketplace/firestore/rules

Reglas: cualquiera con sesión ve la tienda; un comprador solo ve **sus** pedidos y **su**
chat; un no-administrador solo puede tocar ciertas claves del producto (su compra o su
calificación), no la ficha completa.

> ⚠️ **La primera corrida (29/10) destapó dos vulnerabilidades reales.** Se corrigieron las
> reglas y la app el mismo día, se publicaron y se volvieron a probar hasta quedar denegadas.
> Ver `nexcode-marketplace/docs/seguridad-permisos-y-autorizacion.md` (V1, V2, V3, S-03).

**Reglas corregidas publicadas el 29/10/2026, 11:36 p.m. Re-probadas: V1, V2 y V3 quedaron
denegadas. ✅ Vulnerabilidades cerradas.**

| # | Simulación | Ubicación | Autenticado | Documento / nota | Antes del fix | Tras el fix | Captura |
|---|---|---|---|---|---|---|---|
| CTRL | `get` | `/pedidos/{pedido real}` | ON, dueño | `compradorId` = dueño | ✅ Permitido | ✅ Permitido | — |
| A1 | `get` | `/pedidos/{pedido de otro}` | ON, UID = `USER_B` | `compradorId` = otro | ❌ Denegado | ❌ Denegado | — |
| A2 | `get` | `/chats/{otro}/mensajes/m1` | ON, UID = `USER_B` | — | ❌ Denegado | ❌ Denegado | — |
| 🔴 V1 | `create` | `/usuarios/USER_B` | ON, UID = `USER_B` | `tipoUsuario: "administrador"` | 🔴 **PERMITIDO** | ❌ Denegado (falla `!= 'administrador'`) | `mk-auth-V1-denegado.png` |
| 🔴 V2 | `create` | `/chats/{otro}/mensajes/m1` | ON, UID = `USER_B` | `autorId: "USER_B"` | 🔴 **PERMITIDO** | ❌ Denegado limpio | `mk-auth-V2-denegado.png` |
| V3-bis | `update` | `/productos/5hFKv5qDtmxaVUSYHRMU` (real) | ON, UID = `USER_B` | subir `cantidad` a 99999 | 🔴 Permitido (por reglas) | ❌ Denegado (falla `cantidad < resource.cantidad`) | `mk-auth-V3-denegado-producto-real.png` |

> **Nota sobre V3:** probado primero sobre `/productos/prod1`, que **no existe** en la base; el
> Playground no deja fijar el documento actual, así que la simulación termina en "Null value
> error" en vez de una denegación limpia. Se repitió (V3-bis) sobre un producto **real**
> (`5hFKv5qDtmxaVUSYHRMU`): ahí la regla compara contra el stock real y la rechaza limpiamente.
> Para la evidencia se usa la captura de V3-bis.

### Controles positivos (que las reglas nuevas NO rompieron la app) — ejecutados 29/10

Sobre el producto real `5hFKv5qDtmxaVUSYHRMU` (valores al momento: cantidad 37, vendidos 3,
calificacion 4.5, votos 2), usando los mismos tipos que escribe la app (int64 / double).
**Ambas salieron permitidas: las reglas nuevas no bloquean el flujo normal.**

| # | Simulación | Documento escrito | Resultado | Captura |
|---|---|---|---|---|
| OK-compra | `update` como `USER_B` | `{ cantidad: 36, vendidos: 4 }` | ✅ Permitido | `mk-ok-compra-permitida.png` |
| OK-califica | `update` como `USER_B` | `{ calificacion: 4.5, votos: 3 }` | ✅ Permitido | `mk-ok-calificacion-permitida.png` |

> Observación (riesgo bajo, no corregido): la regla de `votos` acepta +1 sin verificar que el
> usuario tenga su documento en `calificaciones/{uid}`, así que en teoría un comprador podría
> sumar votos repetidos. Se puede endurecer con `existsAfter(.../calificaciones/$(uid))`, pero
> no elimina el voto repetido de quien ya calificó una vez; además la app recalcula `votos`
> como el conteo real de opiniones en la siguiente calificación, así que el efecto es temporal.

---

## Qué capturar y cómo documentarlo

Por cada corrida, una captura que muestre: la ubicación, el usuario, y el resultado
(Permitida/Denegada). Nombrar así, dentro de la carpeta de seguridad de cada proyecto:

- CG → `control-gastos-personales/docs/evidencias-pruebas/` → `auth-A1.png` … `auth-OK.png`
- HP → `huellitas-plus/DOCUMENTACION/evidencias-29-oct/` → `auth-A1.png` … `auth-OK.png`
- MK → `nexcode-marketplace/docs/evidencias-pruebas/` → `auth-A1.png` … `auth-OK2.png`

Con las capturas listas, los tres 🟡 de "Pruebas de autorización entre usuarios" en el
checklist pasan a ✅.

---

## Dar de alta un vendedor en Marketplace (reemplaza al botón "Quiero vender")

Tras cerrar V1, la app ya **no** crea administradores. Un vendedor se habilita a mano:

1. El usuario se registra normal en la app (queda como `comprador`).
2. En la consola: https://console.firebase.google.com/project/nexcode-marketplace/firestore/data
   → colección `usuarios` → documento del usuario (su `uid`).
3. Editar el campo `tipoUsuario` y ponerlo en `administrador`. Guardar.
4. El usuario cierra sesión y vuelve a entrar: ahora ve el **Panel del vendedor**.

> El `uid` aparece en *Authentication → Users*. Así solo el equipo decide quién vende, que es
> justo lo que evita la escalada de privilegios (V1).
