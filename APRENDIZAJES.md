# Aprendizajes del proyecto Nexcode

Lecciones que costaron tiempo y que sirven fuera de este proyecto. Cada una está
aquí porque **se descubrió fallando**, no porque suene bien.

---

## 1. Un SDK oficial puede estar peor que la API cruda

**Pasó dos veces en el mismo día.**

El SDK de Anthropic para Java compiló, pero reventó el empaquetado de Android:
tres bibliotecas de Apache con `META-INF/DEPENDENCIES` en conflicto. Se arregló,
pero costó 4 MB de APK y un bloque de exclusiones.

El SDK de Gemini para Android fue peor. Estaba descontinuado y se había quedado
atrás respecto de su propia API: usaba un rol que ahora devuelve 400, y **no
modelaba el campo `thoughtSignature`** que los modelos nuevos exigen de vuelta en
cada llamada a función. Resultado: la conversación moría en el segundo turno,
justo donde un agente empieza a servir.

**La regla que queda:** antes de comprometerse con un SDK, comprobar
empíricamente que compila, dexa y **completa un ciclo real de dos turnos** en el
dispositivo. Un "hola mundo" no prueba nada. Y cuando la API es JSON sobre HTTP,
hablarla directamente suele ser menos código, menos peso y menos sorpresas —el
proyecto ya tenía ese patrón con `HttpURLConnection` y `org.json`.

---

## 2. Tragarse una excepción cuesta horas

Dos veces se escribió esto:

```kotlin
} catch (e: Exception) {
    null   // sigue en local
}
```

Y las dos veces la pantalla dijo "sin conexión" mientras el problema real era
otro: un 503 de saturación la primera, un proveedor de autenticación sin
habilitar la segunda. Diagnosticar fue imposible hasta añadir una línea:

```kotlin
Log.w(ETIQUETA, "No hubo sesion: " + e::class.java.simpleName + ": " + e.message)
```

Con esa línea, `CONFIGURATION_NOT_FOUND` apareció al primer intento y el
diagnóstico fue inmediato.

**La regla:** degradar en silencio de cara al usuario, sí. En el registro, nunca.
Si el `catch` decide qué mensaje ve el usuario, tiene que dejar constancia de qué
pasó de verdad.

---

## 3. Fijar la versión de un modelo de IA es fijar su fecha de caducidad

`gemini-2.0-flash` se eligió al empezar. A mitad del desarrollo devolvía:

```
This model is no longer available. Please update your code to use...
```

Un modelo fijado muere el día que el proveedor lo retira, y la app deja de
funcionar sin que nadie haya tocado el código. El alias (`gemini-flash-latest`)
apunta siempre al vigente.

**Y además:** en niveles gratuitos el 503 por saturación es **aleatorio y por
modelo**. Se comprobó lanzando la misma petición contra seis modelos: los que
fallaban cambiaban entre ejecuciones. Reintentar sobre el mismo modelo no arregla
nada; **cambiar de modelo** sí. De ahí la cascada de tres.

---

## 4. Sincronizar tiene tres trampas, y las tres se descubren tarde

**El identificador.** El autoincremento de SQLite empieza en 1 en cada
dispositivo. Dos teléfonos sin conexión generan el id 3 para movimientos
distintos, y al subir uno pisa al otro. Hace falta un UUID generado en el
dispositivo.

**El borrado.** Una fila borrada de verdad simplemente deja de estar, y el otro
extremo no puede distinguir *"esto se borró"* de *"esto todavía no me ha
llegado"*. Sin borrado lógico, lo eliminado resucita en la siguiente bajada.

**Las claves foráneas.** Un movimiento apunta a `account_id = 3`, y ese 3 no
significa nada en el otro teléfono. Lo que viaja es el uuid de la cuenta, y al
bajar se traduce al id local. De ahí también que el orden importe: **cuentas y
categorías antes que movimientos**, o la clave foránea los rechaza.

Y una cuarta que sigue pendiente: si un dispositivo nuevo **siembra** datos de
demostración *y además* baja los de la nube, termina con todo por duplicado.

---

## 5. Una migración de base de datos se prueba como la sufre el usuario

No basta con que compile. La prueba real fue: instalar el APK **viejo**, abrirlo
para que creara la base con datos, e instalar encima el nuevo.

Ahí apareció lo que habría destruido datos ajenos: **el índice único sobre `uuid`
hay que crearlo después de rellenar las filas existentes**. Creado antes, todas
valen `''`, un índice único no se puede construir sobre valores repetidos, y la
actualización tumba la app con los datos del usuario dentro.

**La regla:** toda migración con datos se prueba versión anterior → versión
nueva, sobre una base con filas, antes de darla por buena.

---

## 6. Kotlin y Java no se hablan tan bien como parece

`Money` es una `value class`. Kotlin **mutila el nombre** de todo método que la
devuelva, y desde Java no se puede llamar:

```
error: cannot find symbol
        movimiento.getSignedAmount()
```

La frontera se cruza devolviendo tipos que Java entienda. Se resolvió con
`Transaction.montoConSigno(): String`.

---

## 7. R8 borra en silencio lo que no ve

R8 sigue llamadas explícitas. Todo lo que se instancia por reflexión le resulta
invisible: lo elimina, **la compilación pasa sin un solo aviso**, y la app falla
en el dispositivo.

Le pasa a Room, a WorkManager, a Jackson y a kotlinx-serialization. Por eso el
proyecto tiene reglas documentadas una por una, cada una diciendo por qué existe.

**La regla:** si se añade una dependencia que serializa o usa reflexión, hay que
**compilar en modo publicación y ejecutar esa ruta** en el dispositivo. Un build
de depuración correcto no dice nada sobre el de publicación.

Corolario grato: al pasar a REST puro, esas reglas desaparecieron. Sin reflexión
no hay nada que R8 pueda romper.

---

## 8. Poner el contrato en la capa correcta se paga solo

El contrato de las herramientas del agente (`HerramientasFinancieras`) se puso en
el **dominio**, no en la capa de datos. Parecía ceremonia.

Semanas después se cambió de proveedor de IA —de Claude a Gemini— y el cambio
tocó **un archivo de `:data` y cero del dominio**. Las cinco funciones, sus
descripciones y las diez pruebas siguieron intactas.

Lo mismo con los metadatos de sincronización: viven en las entidades de `:data` y
no suben al dominio. Los casos de uso ni saben que la app sincroniza.

**La regla:** la capa de negocio decide *qué* se puede hacer; la capa de
infraestructura decide *cómo*. Cuando el "cómo" cambia —y siempre cambia— solo se
reescribe un archivo.

---

## 9. Compilar no es funcionar

Todo lo importante de este proyecto se descubrió **ejecutando**, no compilando:

- El rol `function` rechazado con 400
- El `thoughtSignature` que faltaba
- El modelo retirado
- El 503 aleatorio por modelo
- El `CONFIGURATION_NOT_FOUND`
- El índice único que habría roto la migración

Ninguno era visible en el compilador ni en las pruebas unitarias. Emulador,
`adb logcat` y consultas con `sqlite3` contra la base real fueron las
herramientas que de verdad encontraron los problemas.

---

## 10. Lo gratuito tiene letra pequeña, y hay que leerla antes

No todos los "gratis" son iguales:

| Tipo | Ejemplos |
|---|---|
| Permanente y sin tarjeta | Firebase Spark, FCM, Gemini |
| Permanente pero exige tarjeta | Cognito, DynamoDB provisionado 5/5 |
| Solo 12 meses | RDS, API Gateway |
| Prueba con crédito limitado | Retell |
| **Exige plan de pago** | **Cloud Functions de Firebase** |

Esa última fila apareció tarde: el plan tenía un cobro escondido en la última
etapa —el webhook de Retell iba a Cloud Functions— y nadie lo había mirado. La
alternativa gratuita es Cloudflare Workers.

**La regla:** cuando el presupuesto es cero, el nivel gratuito es criterio
**eliminatorio** y se evalúa antes que cualquier ventaja técnica. Y en AWS, la
alarma de presupuesto va antes que el primer recurso.

---

## 11. Las cifras de un informe caducan solas

El informe afirmaba 24 pruebas cuando había 32, 80 archivos cuando había 88, y 17
casos de uso cuando había 19. Nadie mintió: el código siguió creciendo y el texto
se quedó quieto.

**La regla:** toda cifra de un documento técnico debe venir de un comando que se
pueda volver a ejecutar. Si no se puede recalcular, va a estar mal dentro de dos
semanas.

---

## 12. Lo que es igual en todos los dispositivos necesita identidad igual

Los datos de demostración se sembraban con `uuid` aleatorios. Cada teléfono
generaba los suyos, así que las diez cuentas del primero y las diez del segundo
—las mismas diez cuentas, con el mismo nombre— eran veinte filas distintas en
cuanto había nube de por medio. El síntoma se veía en la pantalla; la causa
estaba en el generador de identificadores.

Un `uuid` aleatorio es lo correcto para una fila que el usuario crea: no existe
en ningún otro sitio. Para una fila que el código escribe idéntica en todas las
instalaciones, lo correcto es lo contrario: **derivar el identificador del
contenido**, `demo-cuenta-efectivo`, para que los dos extremos coincidan solos.

Dos correcciones más aparecieron al arreglarlo, y ninguna era evidente:

- La semilla lleva **fecha fija en el pasado**. Con la hora actual, sembrar
  después equivalía a ganar el conflicto, y una instalación nueva pisaba
  ediciones viejas del usuario solo por ser más reciente.
- El guardia de la siembra tiene que contar las filas **físicas**, no las
  visibles. Con borrado lógico, una tabla "vacía" puede estar llena de `uuid`
  ocupados, y sembrar encima rompe el índice único con la app ya entregada.

**La regla:** antes de replicar, preguntarse si cada fila que el código genera
por su cuenta va a tener el mismo identificador en los dos extremos. Si no lo
tiene, no es la misma fila para nadie más que para quien la está mirando.

---

## 13. Un archivo de seguridad en el repositorio no protege nada

`firestore.rules` llevaba dos semanas escrito, revisado y comentado en el
repositorio. La base de datos, mientras tanto, estaba **abierta a cualquiera que
conociera el identificador del proyecto**: las reglas se evalúan en los
servidores de Google, y allí seguía el modo de prueba que pone la consola al
crear la base. El archivo era un borrador con aspecto de defensa.

Lo que lo destapó no fue leer el archivo, sino **atacarse a uno mismo**: con una
sesión anónima corriente, escribir un documento dentro del subárbol de otro
usuario. Devolvió `200`. Después de desplegar, `403`.

**La regla:** un control de seguridad solo cuenta cuando está desplegado y
alguien ha comprobado que **rechaza** lo que debe rechazar. Dos consecuencias
prácticas:

- Verificar por el camino del atacante, no por el del usuario legítimo. Que la
  app funcione no dice nada: funcionaba igual con la base abierta de par en par.
- Desplegar desde el repositorio (`firebase deploy --only firestore:rules`) y no
  copiando en la consola. Si el texto publicado y el versionado pueden
  separarse, tarde o temprano se separan, y nadie se entera hasta que alguien
  mira.

Corolario del mismo día: unas reglas mal escritas cierran la base **y** rompen la
sincronización, y en pantalla las dos cosas dicen «sin conexión». Por eso la
verificación son dos mitades —que el intruso reciba 403 y que la app siga
subiendo—, no una.
