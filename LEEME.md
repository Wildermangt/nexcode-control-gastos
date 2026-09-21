# Nexcode — Control de Gastos Personales

Aplicación Android nativa para el control de gastos personales. Opera íntegramente
en el dispositivo: no requiere cuenta de usuario ni envía datos a ningún servidor.

**Proyecto Integrador** · Optativa II — Desarrollo Móvil
Universidad de San Buenaventura · Ingeniería de Sistemas
Jeferson Wilderman González Tenjo · José Leandro Ocampo Camacho

---

## Cómo abrir el proyecto

Ábrelo en **Android Studio** desde esta carpeta (la que contiene `settings.gradle.kts`).
La primera compilación descarga las dependencias y tarda varios minutos.

Requisitos verificados: **JDK 21**, Android SDK con **API 35**, Gradle 8.14.5.
El archivo `local.properties` apunta al SDK de este computador; si mueves el proyecto
a otra máquina, Android Studio lo regenera solo.

## Estructura

| Carpeta | Qué contiene |
|---|---|
| `app/` | **Capa de presentación.** Pantallas en Compose, ViewModels, navegación, notificaciones y asistente de voz. Es el único módulo que conoce a `:data`. |
| `domain/` | **Capa de dominio.** Kotlin puro, sin Android. Modelos, casos de uso y contratos de repositorio. Módulo Kotlin Multiplatform: compila para JVM y JavaScript. |
| `data/` | **Capa de datos.** Base de datos Room, entidades, DAO y el servicio de análisis financiero. Expone una sola clase pública: `DataProvider`. |
| `informe/` | **Informe IEEE completo**, 22 secciones en nueve documentos HTML. Ver `informe/LEEME.md`. |
| `entregables/` | APK compilados y listos para instalar. |
| `recursos/` | Material de origen: el logotipo del que se derivaron los iconos. |
| `gradle/` | Catálogo de versiones (`libs.versions.toml`) y el envoltorio de Gradle. |

La regla de dependencias es **`app → domain ← data`** y la verifica el compilador:
`:domain` no puede importar nada de `:app` ni de `:data` porque no los declara.

## Los APK

| Archivo | Tamaño | Firma | Para qué sirve |
|---|---|---|---|
| `entregables/nexcode-1.0-release.apk` | 1,7 MB | certificado propio | **Entrega e instalación.** Optimizado con R8. |
| `entregables/nexcode-1.0-debug.apk` | 11,9 MB | clave de depuración | Pruebas durante el desarrollo. |

Para instalarlo con el teléfono conectado por USB:

```
adb install -r entregables/nexcode-1.0-release.apk
```

> **Ojo:** los dos APK están firmados con claves distintas, así que Android no deja
> instalar uno encima del otro. Hay que desinstalar primero, y eso borra la base de
> datos. La aplicación vuelve a sembrar sus datos de demostración al abrirse.

## Compilar

```bash
./gradlew :app:assembleDebug      # APK de depuración
./gradlew :app:assembleRelease    # APK firmado y optimizado
./gradlew :domain:jvmTest :domain:jsNodeTest   # 41 pruebas en dos plataformas
./gradlew lint                    # análisis estático
```

Los APK aparecen en `app/build/outputs/apk/`. Cópialos a `entregables/` si quieres
conservarlos, porque `./gradlew clean` borra todo lo que hay bajo `build/`.

## Firma de publicación

La compilación de publicación se firma con `nexcode-release.jks`, y las credenciales
están en `keystore.properties`. **Ninguno de los dos debe subirse a un repositorio**;
ambos ya figuran en `.gitignore`.

Si `keystore.properties` no existe, el proyecto **compila igual** pero sin firmar: así
cualquiera puede clonarlo y trabajar en depuración sin necesidad de las claves.

> **Conserva el archivo `.jks`.** Google Play identifica una aplicación por su
> certificado: si se pierde, no se pueden publicar actualizaciones de esta app nunca más.

## Notas de mantenimiento

- **Kotlin está fijado en 2.3.21.** Con 2.4 el procesador de anotaciones de Room falla
  (`Provided Metadata instance has version 2.4.0, while maximum supported version is 2.3.0`).
- **`app/proguard-rules.pro` no es opcional.** R8 es ciego a lo que se instancia por
  reflexión: sin la regla que conserva `RecordatoriosWorker`, las notificaciones dejan
  de funcionar sin ningún error de compilación.
- La base de datos va por la **versión 2**, con una migración escrita a mano. Si añades
  una tabla o una columna, escribe la migración: perder los movimientos del usuario no
  es aceptable.
