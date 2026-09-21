# Nexcode · Contexto del proyecto

Estado a **7 de septiembre de 2026**. Este archivo existe para que cualquiera
—un compañero, el profesor, o yo mismo dentro de un mes— entienda en qué punto
está el proyecto y **por qué** está construido como está, sin tener que leer el
código entero.

---

## 1. Qué es

Aplicación Android nativa de control de gastos personales, dirigida a
estudiantes universitarios colombianos.

| | |
|---|---|
| **Materia** | Optativa II — Desarrollo Móvil, Ingeniería de Sistemas |
| **Universidad** | San Buenaventura |
| **Autores** | Jeferson Wilderman González Tenjo · José Leandro Ocampo Camacho |
| **Entrega** | Aplicación funcional, **7 de octubre de 2026** |
| **Paquete** | `com.nexcode.gastos` |

### Estado del código

```
106 archivos Kotlin + 2 Java     13 140 líneas
domain 39 · data 24 · app 45     21 casos de uso
51 pruebas unitarias             ejecutadas en JVM y JavaScript, 0 fallos
Base de datos Room               versión 3
APK depuración 17 MB             APK publicación 3,3 MB (firmado, R8)
```

---

## 2. Lo que pide el profesor

Del PDF de la guía (`PROYECTO OPTA II (1).pdf`) y de indicaciones posteriores:

**Obligatorio y calificable:**

| Criterio | Peso |
|---|---|
| Desarrollo y funcionamiento de la app Android | 20 % |
| Implementación de las funcionalidades del proyecto | 20 % |
| **Agente de IA: desarrollo e integración** | 20 % |
| **Integración entre la app y el agente** | 10 % |
| Uso de **Kotlin y Java** | 10 % |
| Diseño e interacción | 10 % |
| Pruebas y demostración funcional | 10 % |

La guía es explícita: *"Una aplicación que no tenga el agente de IA desarrollado
e integrado funcionalmente no cumplirá con la totalidad de los requisitos"*.

**Pedido aparte, fuera del PDF:**

- La base de datos debe quedar **en la nube y en físico**, y si una falla, la
  otra sigue operando — redundancia real.
- Un asistente de **chat**, y de **voz** si el usuario lo prefiere.
- Si el usuario necesita un **asesor humano**, que **genere una alerta** para
  que el equipo atienda el caso.

**No lo pide el profesor** (es iniciativa propia): un panel web de
administración. Se decidió **no construirlo** y usar la consola de Firebase.

---

## 3. Arquitectura

Tres módulos Gradle. La regla de dependencias la verifica el compilador, no la
disciplina del equipo:

```
app  ──►  domain  ◄──  data
```

`:domain` no puede importar nada de `:app` ni de `:data` porque no los declara.
Es Kotlin Multiplatform: compila para JVM y JavaScript, y las pruebas corren en
las dos plataformas.

### Decisiones que conviene entender antes de tocar nada

**Room se queda como la base "en físico".** Cuando se añadió la nube se
consideró dejar que Firestore fuera también el almacén local. Se descartó: la
caché de Firestore es opaca, y el requisito del profesor —"en físico"— se
defiende enseñando un archivo SQLite real, no una caché de la que no se puede
mostrar nada.

**Los metadatos de sincronización no cruzan al dominio.** Que una fila esté
pendiente de subir es un detalle de cómo se guarda, no una regla de negocio. Los
casos de uso ni saben que la app sincroniza. Gracias a eso, cambiar de proveedor
de nube no toca el dominio.

**El contrato de las herramientas del agente vive en el dominio.** Quien decide
QUÉ puede hacer el agente es la capa de negocio, no el proveedor de IA. Esto ya
se pagó solo: al cambiar de Claude a Gemini se tocó un archivo de `:data` y cero
del dominio.

---

## 4. El agente de IA

**Proveedor: Gemini, por API REST directa** (`data/remote/ai/GeminiAdvisor.kt`).

No usa el SDK oficial de Android. Se probó y se descartó por dos motivos
comprobados en dispositivo:

1. La API rechaza con 400 el rol `function` que el SDK usa para devolver el
   resultado de una función. Hoy va como `user`.
2. Los modelos Gemini 3.x firman cada llamada a función con un
   `thoughtSignature` y exigen esa firma de vuelta. **El SDK no modela ese
   campo**: lo descarta, y la conversación muere con un 400 en el segundo turno
   —justo donde un agente empieza a servir—.

Hablando REST se controla el JSON y la firma viaja intacta. Además quitó 4 MB de
dependencia y todas las reglas de R8 asociadas.

### Es un agente, no una llamada a un modelo

Se le declaran **cinco funciones** sobre los datos del usuario y el modelo decide
cuáles llamar, en qué orden y cuántas veces:

| Función | Qué hace |
|---|---|
| `consultar_resumen_del_mes` | Ingresos, gastos, saldo, reparto por categoría |
| `consultar_tendencia` | Serie de 6 meses, promedio, ritmo diario, proyección |
| `consultar_pagos_pendientes` | Pagos fijos vencidos o próximos |
| `buscar_movimientos` | Por texto o categoría, contra la base de datos |
| `registrar_gasto` | **Escribe**: crea un gasto nuevo |

**El mensaje inicial no lleva el estado financiero a propósito.** Si se le
entregara masticado, el modelo no tendría motivo para usar las herramientas y
dejaría de ser un agente.

### Cascada de modelos

El nivel gratuito devuelve **503 "high demand" de forma aleatoria y por modelo**
—comprobado lanzando la misma petición contra seis modelos seguidos, y los que
fallaban cambiaban entre ejecuciones—. Ante saturación la app **cambia de
modelo** en vez de insistir:

```
gemini-flash-latest → gemini-3.7-flash → gemini-flash-lite-latest
```

El primero es un alias, no una versión fija: `gemini-2.0-flash`, que se usó al
principio, quedó **retirado y devolvía 404** en mitad del desarrollo.

### Verificado en emulador, los cuatro casos de la guía

| Caso | Resultado |
|---|---|
| 3 · Consulta al agente | Respondió con pagos vencidos reales y formato colombiano |
| 4 · El agente consulta la app | Llamó a dos funciones por decisión propia |
| 5 · El agente ejecuta una acción | Registró un gasto de 25.000; el saldo cambió en la UI |
| 6 · Fuera de alcance | *"Solo te puedo ayudar con tus finanzas personales…"* |

**La clave va en `local.properties`** como `gemini.apiKey`, fuera del control de
versiones. Limitación conocida y declarada: acaba dentro del APK y es extraíble.
Lo correcto en producción sería un proxy; queda fuera del alcance del curso.

---

## 5. Java, el 10 % de la nota

El proyecto era 100 % Kotlin. Se añadieron dos clases Java **usadas de verdad**
en tres sitios, no decorativas:

- **`ExportadorCsv.java`** — genera el CSV con escapado RFC 4180. Sin él, un
  título como `"Mercado; frutas"` parte la fila en dos columnas.
- **`ValidadorEntrada.java`** — sanea el texto libre. Cubre un hueco real: el
  dominio comprueba que el título no esté vacío, pero no que quepa en pantalla.

**Trampa de interoperabilidad encontrada:** `Money` es una `value class`, así que
Kotlin **mutila el nombre** de todo método que la devuelva y desde Java no se
puede llamar. Se resolvió con `Transaction.montoConSigno()`, que devuelve texto.

---

## 6. Redundancia nube + físico

Es lo que pidió el profesor y está construido, **pendiente de probar**.

```
   App Android                                    Nube
┌────────────────┐                        ┌──────────────────┐
│  Room / SQLite │  ── cola de salida ──► │    Firestore     │
│   (en físico)  │  ◄── descarga ───────  │    (réplica)     │
└────────────────┘                        └──────────────────┘
   opera sola sin red                      opera sola sin la app
```

### Etapa 1 — hecha y verificada

Las cuatro tablas ganaron metadatos de réplica (`MetadatosDeSincronizacion`):

| Campo | Para qué |
|---|---|
| `uuid` | Identidad global. El autoincremento de SQLite empieza en 1 en cada teléfono: dos dispositivos sin conexión generan el mismo id y al subir uno pisa al otro |
| `actualizado_en` | Resuelve conflictos y permite bajar solo lo que cambió |
| `borrado` | Hace que los borrados se puedan propagar |
| `sincronizado` | La cola de lo que falta subir |

**Migración 2 → 3 probada como le pasará a un usuario real**: se instaló el APK
viejo, se abrió para crear la base v2 con datos, y se instaló encima la versión
nueva.

```
ANTES    user_version=2   10 transacciones   sin columna uuid
DESPUÉS  user_version=3   10 transacciones   10 uuid únicos, 0 vacíos
```

**El detalle que la habría roto:** el índice único sobre `uuid` se crea *después*
de rellenar las filas existentes. Si se crea antes, todas valen `''`, el índice
único no se puede construir y la actualización tumba la app con los datos dentro.

**Borrado lógico verificado en vivo:** al deslizar "Netflix", visibles pasó de 10
a 9, filas físicas siguió en 10, y la fila quedó `borrado=1 sincronizado=0`.

### Etapa 2 — **probada contra la nube el 7 de septiembre de 2026**

Authentication anónimo habilitado y base Firestore creada. Las cinco pruebas
corridas en el emulador, con la nube real:

| | Prueba | Resultado |
|---|---|---|
| 1 | Subida de 40 filas pendientes | `sincronizado` 40 → 0; los 40 documentos en Firestore |
| 2 | Base local borrada, sesión conservada | **No sembró** (0 filas) y la pasada bajó las 40: 0 duplicados, 0 claves foráneas huérfanas |
| 3 | Modo avión + registrar un gasto | Guardado en Room, `sincronizado=0`, la app entera operativa |
| 4 | Recuperar la red y sincronizar | El gasto llegó a la nube íntegro |
| 5 | Deslizar para borrar «Netflix» | Local: físicas 11, visibles 10. Nube: `borrado: true` |

La prueba 2 es la que valida la corrección de la siembra duplicada: el
identificador `demo-movimiento-netflix` es el mismo en las dos puntas, así que la
bajada actualizó la fila en vez de añadir una segunda.

### Lo que las pruebas dejaron al descubierto

**1. Las reglas no estaban publicadas — resuelto el mismo día.** La base había
quedado en modo de prueba: con un token anónimo cualquiera se podía escribir
dentro del subárbol de otro usuario (HTTP 200 donde debía haber un 403). Se
desplegó `firestore.rules` desde el repositorio y la intrusión ya devuelve 403.
Ver la sección 7.

**2. La sesión anónima es por instalación — resuelto en código el 7 de
septiembre, pendiente de habilitar el proveedor.** Firebase
asigna un `uid` distinto a cada instalación, y se pierde al borrar los datos de la
app o al reinstalar. Consecuencias que hay que decir en voz alta:

- «Se pierde el teléfono, los datos siguen en la nube» **no se cumple hoy**: la
  copia queda huérfana porque nadie puede volver a autenticarse como ese `uid`.
- Dos dispositivos **no comparten datos**: cada uno replica su propio subárbol.

La salida está dentro de Firebase y es acotada: `linkWithCredential` sobre la
sesión anónima. El `uid` se conserva —y con él todos los datos ya subidos—, y a
partir de ahí la cuenta se puede recuperar en otro teléfono. Es lo que convierte
la réplica en una copia de seguridad de verdad.

**Ya está construido:**

```
data/auth/CuentaDeUsuario.kt          enlazar(), entrar(), estado()
data/di/DataProvider.kt               reemplazarDatosLocalesPorLaNube()
app/cuenta/CuentaViewModel.kt         estado e invocación
app/presentation/components/TarjetaDeCuenta.kt
```

Dos decisiones que no son obvias:

- **Se enlaza, no se migra.** La alternativa era crear la cuenta por separado y
  mover los datos: eso significaría reescribir los cuarenta documentos bajo un
  `uid` nuevo y perder cualquier cambio que llegara a mitad. `linkWithCredential`
  conserva el `uid`, así que no hay nada que mover.
- **Al entrar en una cuenta desde otro teléfono, el borrado local es físico.**
  Marcar las filas como borradas propagaría esos borrados a la nube en la
  siguiente pasada y destruiría los datos buenos en todos los dispositivos: el
  borrado lógico existe justamente para viajar, y aquí no debe viajar nada.

**Falta habilitar el proveedor** en la consola —Authentication → Sign-in method →
Correo electrónico/contraseña— para poder probarlo de punta a punta. Sin eso, la
app ya responde lo que debe: en pantalla *«Falta habilitar el acceso por correo
en la consola de Firebase»* y en el registro el `FirebaseAuthException` completo.
El motivo va en el **código** del error (`ERROR_OPERATION_NOT_ALLOWED`), no en su
mensaje; mirar solo el texto dejaba este caso cayendo en un «no se pudo»
genérico, y así se encontró.

**3. Tres asperezas menores, ninguna bloqueante:**

- La tarjeta dice «Al día. Tus datos están en el teléfono y en la nube» aunque la
  base local esté vacía o haya un cambio pendiente: muestra el resultado de la
  última pasada, no el estado actual.
- Al decidir **no sembrar** porque la nube tiene datos, la app no lanza una pasada
  inmediata; hasta que el usuario pulse o pasen 15 minutos, ve la aplicación
  vacía.
- El worker es periódico de 15 minutos (`MINUTOS_ENTRE_PASADAS = 15`), así que al
  recuperar la red no sube «al instante». Un `OneTimeWorkRequest` con la
  restricción de red lo arreglaría.

### Etapa 2 — el diseño

```
data/sync/
  MotorDeSincronizacion.kt     orden de la pasada y sesión anónima
  AdaptadorDeTabla.kt          algoritmo de subida y bajada, una sola vez
  Adaptadores.kt               las cuatro traducciones tabla ↔ documento
  MarcasDeSincronizacion.kt    hasta dónde bajó cada colección
app/sincronizacion/
  SincronizadorWorker.kt       replica en segundo plano al recuperar red
  SincronizacionViewModel.kt   estado e invocación manual
firestore.rules                reglas de seguridad, hay que pegarlas en consola
```

Una pasada hace:

```
1. Sube todo lo pendiente, borrados incluidos
2. Baja cuentas y categorías
3. RECONSTRUYE la traducción de identificadores
4. Baja movimientos y pagos fijos
```

El paso 3 no es adorno: el paso 2 pudo crear cuentas nuevas, y sin rehacer la
tabla de equivalencias los movimientos bajarían apuntando a ids que ya no valen.

**Tres decisiones que evitan perder datos:**

- Las filas se marcan como sincronizadas **después** de que la nube confirme.
- La marca de bajada avanza solo sobre lo aplicado: un corte a mitad se reanuda.
- Un movimiento cuya cuenta aún no bajó no se descarta; espera a la siguiente
  pasada.

**No puede romper la entrega:** sin `google-services.json` el plugin ni se
aplica, el motor vale `null` y la app se comporta como antes. Mismo criterio que
ya se usaba con `keystore.properties`.

### La siembra duplicada, resuelta el 7 de septiembre

El segundo teléfono se encontraba la base local vacía, sembraba sus diez cuentas
de demostración **y** además se bajaba las diez de la nube. Como cada siembra
generaba `uuid` aleatorios, para él eran veinte cuentas distintas. Igual con las
categorías, los movimientos y los pagos fijos: cuarenta filas por duplicado.

Se arregló por dos lados, y hacen falta los dos:

- **Los datos de demostración tienen identidad fija.** El `uuid` se deriva del
  nombre —`demo-cuenta-efectivo`, `demo-pago-arriendo`—, así que dos teléfonos
  generan exactamente el mismo. Si los dos siembran, la bajada **actualiza** esa
  fila en lugar de añadir otra.
- **Se le pregunta a la nube antes de sembrar.** `laNubeTieneDatos()` lee una
  sola cuenta con `Source.SERVER`; si la nube ya tiene datos de este usuario, no
  se siembra nada y la primera pasada trae los de verdad. Sin red la pregunta
  devuelve *no se sabe* y se siembra igual: la app tiene que abrir con algo, y
  el `uuid` fijo hace que esa siembra a ciegas no duplique nada.

Dos detalles que costaron pensarlos:

- **La fecha de la semilla es fija y está en el pasado** (`EPOCA_SEMILLA`). Los
  conflictos se resuelven por la fecha más reciente, así que cualquier edición
  del usuario —que lleva la hora real— gana siempre contra una siembra tardía.
- **El guardia mira el conteo físico, no el visible.** Con el visible, un usuario
  que borrara todas sus cuentas volvería a sembrar sobre unos `uuid` que ya
  existen, y el índice único tumbaría la app con los datos dentro.

Verificado en emulador con instalación limpia: 10 + 10 + 10 + 10 filas, todos los
`uuid` con prefijo `demo-`, ninguno repetido, y la segunda apertura no añade
ninguna. La pregunta a la nube se hizo y falló con el
`CONFIGURATION_NOT_FOUND` de la sección 7, que es el camino degradado.

---

## 7. La nube, montada y cerrada

Todo lo del 7 de septiembre de 2026:

| | |
|---|---|
| Authentication anónimo | Habilitado. El `CONFIGURATION_NOT_FOUND` era que el servicio no se había creado nunca; lo resolvió el botón «Comenzar» |
| Base Firestore | Creada, `(default)`, modo producción |
| Reglas | **Publicadas desde el repositorio**, no copiadas a mano |

### Las reglas se despliegan, no se pegan

El archivo `firestore.rules` del repositorio **no protege nada por sí solo**: las
reglas viven en los servidores de Google. Hasta que alguien las publica, la base
usa lo que tenga la consola —y lo que tenía era el modo de prueba, abierto a
cualquiera que conociera el id del proyecto—.

Por eso se dejó montado el despliegue por línea de comandos:

```bash
npx --yes firebase-tools login          # una vez, por navegador
npx --yes firebase-tools deploy --only firestore:rules --project nexcode-gastos
```

`firebase.json` y `.firebaserc` en la raíz son lo que hace falta para eso: el
primero dice qué archivo publicar, el segundo a qué proyecto. La ventaja sobre
pegar en la consola es que **lo publicado y lo versionado son el mismo texto**;
si alguien las cambia a mano en el navegador, la diferencia se ve en el
repositorio y se vuelve a desplegar con un comando.

### Verificación: atacarse a uno mismo

Publicar no es lo mismo que estar protegido. La comprobación es intentar la
intrusión con una sesión anónima cualquiera, que es exactamente lo que tendría
un atacante:

| Intento | Resultado |
|---|---|
| Escribir en los datos de la app | **403** |
| Leer un movimiento de la app | **403** |
| Escribir bajo un uid ajeno | **403** |
| Escribir en el subárbol propio | **200** |

Y después, la otra mitad: que la app siga funcionando. Se creó un gasto y se
sincronizó —1 pendiente → 0, cero `PERMISSION_DENIED` en el registro—. Unas
reglas mal escritas cierran la base **y** rompen la sincronización, y en pantalla
las dos cosas se ven igual: «Sin conexión».

**El comando de la sonda**, para repetirlo cuando se toquen las reglas:

```bash
TOK=$(curl -s -X POST "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=<apiKey>" \
  -H "Content-Type: application/json" -d '{"returnSecureToken":true}' | jq -r .idToken)

curl -s -o /dev/null -w "%{http_code}\n" -X PATCH \
  -H "Authorization: Bearer $TOK" -H "Content-Type: application/json" \
  -d '{"fields":{"x":{"stringValue":"y"}}}' \
  "https://firestore.googleapis.com/v1/projects/nexcode-gastos/databases/(default)/documents/usuarios/AJENO/cuentas/intruso"
```

**403 = las reglas están bien. 200 = la base sigue abierta.** Si devuelve 200,
borrar el documento que acaba de crear con el mismo token y un `DELETE`.

### Vía alternativa: AWS

Se exploró a petición del equipo y **quedó configurada el 7 de septiembre de
2026**, siguiendo el documento oficial de AWS (`agent-toolkit-for-aws`).

| | |
|---|---|
| Cuenta | 289633969103 · experiencia nueva de AWS (proyecto) |
| Perfil del CLI | `nexcode`, región `us-east-1` |
| Credenciales | 12 horas, renovables 90 días sin volver al navegador |
| Agent Toolkit | 23 skills instaladas; servidor MCP `aws-mcp` en cinco herramientas |
| Reglas | `CLAUDE.md` en la raíz, con `help_level: HIGH` |

Dos detalles que costarían tiempo si se olvidan:

- El toolkit escribe la entrada `aws-mcp` **sin perfil**, así que busca
  credenciales en `default` —que aquí no existe— y falla con
  `JSON-RPC error: -32602`. Hay que añadirle `AWS_MCP_PROXY_PROFILES` con el
  nombre del perfil. Ya está hecho en los cinco archivos.
- La sesión de `aws login` entra como **root** de la cuenta, no como usuario
  IAM. Sirve para desarrollo, pero conviene saberlo antes de crear nada.

Cuando haya que renovar: `aws login --region us-east-1 --profile nexcode`.

### Qué hay encendido en AWS (revisado el 7 de septiembre de 2026)

**Nada de lo que montamos cobra.** La sesión del CLI, las skills, el servidor MCP
y el presupuesto son gratis. No se creó ni un recurso.

Lo que sí había, de marzo de 2026 y ajeno a este proyecto:

| Recurso | Región | Estado |
|---|---|---|
| EC2 `i-0cf87fcf45dab8bca` «wiil94», t3.micro | us-east-1 | detenida |
| EC2 `i-095f2ab0ae5da90b1` «Will94», t3.micro | us-east-2 | detenida |
| Discos EBS 8 GB gp3 (uno por instancia) | ambas | en uso |
| Bucket S3 `wil.94` | us-east-1 | vacío |

**Decisión del equipo: se quedan todas detenidas.** No se terminan, porque
borrarlas destruiría lo que haya en esos discos.

El detalle que se pasa por alto: **una instancia detenida no cobra cómputo, pero
su disco sigue cobrando**. Son unos 1,28 USD al mes entre las dos, contra 112 USD
de crédito. Sin IP elásticas —que sí cobran cuando no están en uso— y con el
bucket vacío, no hay ninguna otra fuga. Barrido de ocho regiones: cero instancias
encendidas.

**Todavía no se ha creado ni un recurso en AWS**, y hay un motivo para no correr:

```
aws freetier get-account-plan-state --region us-east-1 --profile nexcode
  accountPlanType    PAID          ← no es el plan gratuito
  accountPlanStatus  ACTIVE
  créditos restantes 112,08 USD
```

La cuenta está en **plan de pago con 112,08 USD de crédito**, no en el plan
gratuito. Mientras el crédito dure no se paga nada, pero al agotarse la cuenta
puede cobrar, y eso choca de frente con la restricción del proyecto.

**Presupuesto de aviso, creado el 7 de septiembre de 2026:**

```
nexcode-tripwire-1usd    1 USD mensual    gasto actual 0,0 USD
avisos por correo al 1 % real, 50 % real y 100 % previsto
IncludeCredit = false  ← mide el consumo facturable ANTES de los créditos
```

Ese `IncludeCredit = false` es la clave: con el valor por defecto, los 112 USD de
crédito absorberían el gasto y el aviso no saltaría hasta agotarlos. Así salta al
primer céntimo de uso facturable, que es lo que interesa cuando la regla es
"solo lo gratuito".

**Lo que el presupuesto NO hace es detener nada.** El corte de verdad es el
**límite de gasto** de la experiencia nueva, que pausa el proyecto al superarse:
se pone en [settings.aws.com](https://settings.aws.com/) → Billing, solo lo puede
tocar el propietario y **no existe en el CLI** (`freetier` solo ofrece
`upgrade-account-plan`; `billing` y `budgets` no exponen nada equivalente).
Pendiente de que el equipo lo fije en el mínimo.

Queda otra pregunta abierta para la consola: si el proyecto puede volver al
**plan gratuito**, que es la única configuración donde un cobro es imposible por
diseño. El CLI solo permite subir de plan, no bajar.

Arquitectura equivalente sin Lambda: **Cognito Identity Pool** con acceso de
invitado + **DynamoDB** directo, con política IAM que restringe por
`dynamodb:LeadingKeys` a la identidad de quien llama.

Advertencias registradas: AWS exige tarjeta y puede cobrar; el push en Android
necesita FCM de todas formas; y RDS solo es gratis 12 meses.

---

## 8. Lo que falta

| | Qué | Cuánto |
|---|---|---|
| **2** | ~~Probar los 5 pasos~~, ~~publicar las reglas~~ y ~~enlazar la cuenta~~: **hecho el 7 de septiembre**. Solo queda **habilitar el proveedor de correo** en la consola y probarlo de punta a punta | 1 hora |
| **3** | Chat de soporte + **FCM para la alerta al asesor**. Cierra un requisito explícito | 1 semana |
| **4** | Retell para la voz. El webhook **no puede ir en Cloud Functions** (exigen plan de pago): va en Cloudflare Workers | 1 semana |

Recomendación: **dar la entrega por cerrada al terminar la etapa 3**. Con eso se
cumple todo lo pedido, alerta al asesor incluida.

### Deuda acumulada

- **La documentación está congelada por instrucción del equipo.** El informe IEEE
  describe un asistente que ya no existe, no menciona la nube ni la redundancia,
  y las cifras están desfasadas otra vez.
- Los APK de `entregables/` son anteriores a todo esto.
- El PDF del informe hay que reexportarlo desde Word.
- La clase `Usuario` que faltaba —primer hueco detectado— se resuelve sola con la
  sesión anónima. Cuando se descongele la documentación, hay que contarlo.

---

## 9. Restricciones permanentes

**Todo servicio externo debe caber en su nivel gratuito.** Es criterio
eliminatorio, no desempate. Y no todos los "gratis" son iguales:

| Tipo | Ejemplos |
|---|---|
| Gratuito permanente | Firebase Spark, FCM, Gemini, Cognito, DynamoDB provisionado 5/5 |
| Gratuito 12 meses | RDS, API Gateway |
| Prueba con crédito | Retell |
| **Exige plan de pago** | **Cloud Functions de Firebase** |

Firebase Spark no pide tarjeta y no puede generar un cobro sorpresa: cuando se
agota la cuota, deja de servir. AWS sí exige tarjeta — por eso el primer paso de
esa vía es crear una alarma de presupuesto.
