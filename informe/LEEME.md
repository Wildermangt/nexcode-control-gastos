# Informe IEEE — Nexcode

Las **22 secciones** exigidas por el Proyecto Integrador.

## Para entregar: el documento único

| Archivo | Qué es |
|---|---|
| `Informe-IEEE-Nexcode-completo.docx` | **Las 22 secciones en un solo Word**, con formato de conferencia IEEE: dos columnas, Times New Roman 10 pt, títulos en numeración romana, bibliografía unificada. 37 páginas. |
| `Informe-IEEE-Nexcode-completo.pdf` | El mismo documento exportado desde Word, listo para imprimir o subir. |

En el documento unido, las secciones 1 (título) y 2 (autores) son el encabezado de la
primera página, como manda la plantilla IEEE, de modo que los títulos numerados van de la
**III** a la **XXII**. Esa numeración romana se corresponde una a una con las secciones
3 a 22 del plan de estudios.

Las tablas, figuras, fragmentos de código y salidas de consola se **renumeraron por orden
de aparición** —Tabla I a XXXVII, Fig. 1 a 13, Fragmento 1 a 16, Salida 1 a 5— y las
referencias del texto se reescribieron para seguir apuntando al sitio correcto. Los nueve
documentos originales se habían escrito en un orden distinto al de lectura, así que su
numeración no era ascendente al unirlos.

### Bibliografía

Los originales traían **tres listas de referencias distintas**, cada una numerando desde
[1], de modo que una misma cita significaba cosas diferentes según la sección. El
documento unido las funde en **una sola lista de 34 entradas**, al final, en la Sección
XXII.

Las veintiuna primeras conservan el orden que ya suponían las citas escritas, así que
ninguna referencia existente cambió de significado. A partir de la 22 se añadieron las
fuentes de las tecnologías que el informe usaba sin citar —Compose, Room, WorkManager,
las API de voz, R8, Gradle y la documentación de Kotlin sobre nulos, clases selladas y
*value classes*—, con la cita insertada en el punto donde cada una aparece por primera vez.

Se comprueba en cada generación que **toda cita del texto tiene entrada y toda entrada
está citada**, sin huecos en la numeración: es el requisito de IEEE. Por eso no figura
ninguna fuente que el texto no nombre.

## Los originales por secciones

Se conservan sin modificar. Cada archivo `.html` se abre con doble clic en cualquier
navegador, y la misma versión está publicada en línea, en un enlace privado que puedes
compartir.

| Secciones | Archivo | En línea |
|---|---|---|
| 1–7 · Planteamiento, justificación y objetivos | `01-07-planteamiento.html` | [ver](https://claude.ai/code/artifact/b11f36f4-fb27-4e03-bcd7-1b067707c007) |
| 8 · Estado del arte | `08-estado-del-arte.html` | [ver](https://claude.ai/code/artifact/79eb4fee-8b1e-49ab-a398-5d5808fd482a) |
| 9 y 18 · Tecnologías y asistente de IA | `09-18-tecnologias-e-ia.html` | [ver](https://claude.ai/code/artifact/0ef5c14a-81ba-48ac-a854-01efa1b47e70) |
| 10 · Diseño de la interfaz | `10-diseno.html` | [ver](https://claude.ai/code/artifact/cd3a8d02-bd08-4555-8460-2da717b75da7) |
| 11 · Arquitectura en capas | `11-arquitectura.html` | [ver](https://claude.ai/code/artifact/66301cb6-243f-427b-8284-1a2d8ccbcfca) |
| 12–16 · Pilares de Kotlin | `12-16-pilares-kotlin.html` | [ver](https://claude.ai/code/artifact/8d0ae495-e40d-4bd2-88ef-3520a7d3a448) |
| 17 · Multiplataforma | `17-multiplataforma.html` | [ver](https://claude.ai/code/artifact/7b701206-3d2a-4f31-b897-2f57b3a90378) |
| 19 · Pruebas de funcionamiento | `19-pruebas.html` | [ver](https://claude.ai/code/artifact/189b9c67-21d9-49b9-87f2-e133ecfcc001) |
| 20–22 · Resultados, conclusiones y referencias | `20-22-resultados.html` | [ver](https://claude.ai/code/artifact/414307ff-4f59-46fc-9167-cf6361beeefa) |

## Si editas los originales

Los nueve archivos HTML son la fuente; el Word se genera a partir de ellos. Si cambias
un HTML, el `.docx` **no se actualiza solo**: hay que volver a generarlo, o el documento
de entrega quedará desfasado respecto a las secciones sueltas.

Su numeración interna (Figs. 1–13, Tablas I–XXXVII, Fragmentos 1–16, Salidas 1–5) sigue
el orden en que se escribieron, no el de lectura. Es correcta dentro del conjunto de
nueve, y el documento unido la recalcula por su cuenta.

## Criterio de redacción

Se siguieron dos reglas de forma deliberada, y conviene mantenerlas si editas el texto:

1. **Cada afirmación va acompañada del comando que la reproduce.** Las cifras de pruebas,
   los tamaños y las salidas de consola no se transcribieron de memoria: se obtuvieron
   ejecutando lo que el propio informe indica.
2. **Cada sección declara sus limitaciones.** La Sección 8 incluye a propósito una tabla
   comparativa desfavorable frente a las aplicaciones del mercado, y la 21 enumera siete
   limitaciones del entregable. Un informe que solo presenta aciertos no es creíble.

## Pendiente

En la portada (Sección 1) queda el marcador **`[correo institucional]`** por rellenar.

## Para convertir a PDF

Abre el archivo en el navegador y usa *Imprimir → Guardar como PDF*. Los estilos están
preparados para papel: los fondos de color no se imprimen y los bloques de código no se
parten entre páginas.
