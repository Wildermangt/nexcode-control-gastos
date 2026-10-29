# Nexcode · Control de Gastos Personales · Planeación y requisitos

Optativa II – Desarrollo de Aplicaciones para Dispositivos Móviles · Universidad de San Buenaventura, Bogotá
Equipo Nexcode: Jeferson Wilderman González Tenjo (Product Owner · Developer) y José Leandro Ocampo Camacho (Scrum Master · Developer)

El análisis completo del problema está en el informe IEEE (`informe/Informe-IEEE-Nexcode-completo.pdf`). Este documento resume la planeación y deja los requisitos trazados al código.

## 1. Problema

Los estudiantes universitarios colombianos manejan ingresos pequeños e irregulares (mesadas, trabajos por horas, monitorías) y gastos frecuentes de bajo valor: transporte, comida y fotocopias. Como cada gasto parece insignificante, casi nadie los registra, y a mitad de mes no saben en qué se fue el dinero ni cuánto les queda en cada cuenta (efectivo, Nequi o banco). Las hojas de cálculo exigen disciplina y las apps bancarias solo muestran una cuenta.

## 2. Justificación

Una app de registro rápido en el teléfono reduce el esfuerzo de anotar cada gasto a unos segundos:

- **Registro por voz,** para anotar un gasto mientras se camina o se hace fila.
- **Varias cuentas** con saldo independiente, como en la vida real del estudiante.
- **Resumen del mes por categoría y tendencia,** para ver de un vistazo en qué se va el dinero.
- **Recordatorios de pagos recurrentes** (arriendo, plan del celular, suscripciones) para no pagar recargos.
- **Asistente conversacional** que responde con los datos reales del usuario.
- **Funciona sin red y sin cuenta,** porque los datos viven en el teléfono; la nube es opcional para respaldar.

## 3. Objetivo

Desarrollar una aplicación Android que permita a un estudiante registrar sus ingresos y gastos en varias cuentas, analizar su comportamiento financiero mensual, recordar sus pagos recurrentes y consultar a un asistente con IA, con almacenamiento local y sincronización opcional en la nube.

## 4. Alcance

**Incluye:**

- Registro, edición y eliminación de ingresos y gastos, con categoría, cuenta, fecha y nota.
- Registro de gastos dictados por voz.
- Varias cuentas con saldo independiente.
- Resumen mensual: balance, gráfico por categoría y tendencia.
- Búsqueda de movimientos por texto.
- Pagos recurrentes con notificaciones de recordatorio.
- Asistente conversacional con IA (Gemini) que consulta los datos del usuario y puede registrar gastos, con un motor local de respaldo cuando no hay red ni clave.
- Exportación de movimientos a CSV.
- Inicio de sesión opcional y sincronización con Cloud Firestore.
- Aviso a un escenario de Make que guarda cada movimiento en una hoja de cálculo y envía un correo.

**No incluye:**

- Conexión directa con bancos o billeteras digitales.
- Presupuestos compartidos entre varias personas.
- Inversiones, créditos o cambio de divisas: todo se maneja en pesos colombianos.
- Versión para iOS o web.

## 5. Usuarios

| Perfil | Necesidad principal |
|---|---|
| Estudiante universitario | Saber en qué gasta, cuánto tiene en cada cuenta y no olvidar sus pagos fijos |

## 6. Requisitos funcionales

La columna *Dónde se implementa* sirve como trazabilidad entre cada requisito y el código (`app`, `domain` y `data`).

| ID | Requisito | Prioridad | Dónde se implementa |
|---|---|---|---|
| RF-01 | Registrar un ingreso o un gasto con monto, categoría, cuenta, fecha y nota | Alta | `presentation/addtransaction/AddTransactionScreen.kt`, `domain/usecase/transaction/AddTransactionUseCase.kt` |
| RF-02 | Editar y eliminar movimientos | Alta | `UpdateTransactionUseCase.kt`, `DeleteTransactionUseCase.kt`, `presentation/home/HomeViewModel.kt` |
| RF-03 | Registrar un gasto dictándolo por voz | Media | `presentation/voz/` (`AsistenteDeVoz`, `ReconocedorDeVoz`, `SintetizadorDeVoz`) |
| RF-04 | Manejar varias cuentas con saldo independiente | Alta | `presentation/accounts/AccountsScreen.kt`, `UpdateAccountBalanceUseCase.kt` |
| RF-05 | Ver el balance y el resumen del mes por categoría | Alta | `presentation/home/HomeScreen.kt`, `CalculateBalanceUseCase.kt`, `GetCategorySummaryUseCase.kt` |
| RF-06 | Ver la tendencia de gastos | Media | `AnalizarTendenciaUseCase.kt`, `components/TendenciaChart.kt` |
| RF-07 | Buscar movimientos por texto | Media | `presentation/transactions/TransactionsScreen.kt`, `BuscarTransaccionesUseCase.kt` |
| RF-08 | Programar pagos recurrentes y recibir recordatorios | Media | `presentation/recurring/RecurringScreen.kt`, `notificaciones/RecordatoriosWorker.kt` |
| RF-09 | Consultar a un asistente con IA que usa los datos del usuario y puede registrar gastos | Media | `presentation/assistant/AssistantScreen.kt`, `data/remote/ai/GeminiAdvisor.kt`, `HerramientasDelAgente.kt` |
| RF-10 | Exportar los movimientos a CSV | Baja | `presentation/util/CompartirCsv.kt` |
| RF-11 | Iniciar sesión de forma opcional y sincronizar los datos con la nube | Media | `presentation/profile/ProfileScreen.kt`, `cuenta/CuentaViewModel.kt`, `data/auth/CuentaDeUsuario.kt`, `data/sync/MotorDeSincronizacion.kt` |
| RF-12 | Enviar cada movimiento a la automatización de Make | Baja | `automatizacion/AvisoDeMovimiento.kt` |

## 7. Requisitos no funcionales

| ID | Requisito | Cómo se cumple |
|---|---|---|
| RNF-01 | **Funcionamiento sin red:** la app se usa completa sin conexión ni cuenta | Base local Room; la sincronización es opcional |
| RNF-02 | **Seguridad en la nube:** cada usuario accede solo a sus datos | `firestore.rules` exige `request.auth.uid == uid` |
| RNF-03 | **Protección de credenciales:** ninguna clave ni URL privada está en el código fuente | La clave de Gemini y el webhook de Make se leen de `local.properties`, que no se versiona |
| RNF-04 | **Privacidad:** los datos financieros no salen del teléfono en las copias de seguridad | `android:allowBackup="false"` y reglas de exclusión de la base y las preferencias |
| RNF-05 | **Integridad:** un monto inválido nunca llega a la base de datos | Tres niveles de validación: `Money.parse()`, el caso de uso y el `init` del modelo |
| RNF-06 | **Consistencia al sincronizar:** las eliminaciones se propagan a la nube | Borrado lógico (`borrado = 1`) que el motor de sincronización sube a Firestore |
| RNF-07 | **Mantenibilidad:** la lógica de negocio no depende de Android | Módulos `app → domain ← data`; `domain` es Kotlin puro y multiplataforma |
| RNF-08 | **Calidad:** la lógica de negocio tiene pruebas automatizadas | 51 pruebas en `domain/src/commonTest`, ejecutadas en JVM y JS |
| RNF-09 | **Compatibilidad:** funciona desde Android 8.0 | `minSdk 26`, `targetSdk 35` |

## 8. Historias de usuario y Sprints

Las historias de usuario, sus tareas y los tres Sprints de dos semanas se gestionan en Azure DevOps, en el proyecto **Nexcode - Control de Gastos Personales** (https://dev.azure.com/wildermangt). Cada historia sigue el formato *Como [rol] quiero [acción] para [beneficio]*; por ejemplo, *«Como usuario quiero registrar mis ingresos y gastos»* corresponde a RF-01.

## 9. Diseño de pantallas

Los mockups de las pantallas principales están en [`02-mockups.md`](02-mockups.md).
