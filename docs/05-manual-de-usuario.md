# Control de Gastos (Nexcode) — Manual de usuario

Aplicación Android para registrar ingresos y gastos, controlar presupuestos y ver el resumen
financiero del mes, con un asistente de IA por texto y voz. Requiere Android 8 (API 26) o
superior. Los datos se guardan en el dispositivo y, si hay sesión, se sincronizan con Firebase.

## 1. Primeros pasos

1. Instalar el APK (`entregables/`) o ejecutar desde Android Studio.
2. En la pantalla de **bienvenida**, crear una cuenta con correo y contraseña o iniciar sesión
   (Firebase Auth). También se puede usar sin cuenta; al iniciar sesión, los datos se sincronizan.

## 2. Pantallas principales

| Pantalla | Para qué sirve |
|---|---|
| **Inicio (Home)** | Resumen del mes: ingresos, gastos, balance y gráfico por categoría. |
| **Transacciones** | Lista de movimientos con búsqueda y filtros; permite eliminar. |
| **Agregar** | Registrar un ingreso o un gasto. |
| **Recurrentes** | Movimientos que se repiten (p. ej. arriendo, suscripciones). |
| **Cuentas** | Cuentas o bolsillos del usuario. |
| **Asistente** | Asistente de IA por texto o voz. |
| **Perfil** | Datos de la cuenta, versión y cierre de sesión. |

## 3. Registrar un movimiento

1. Pulsar **Agregar**.
2. Elegir **Ingreso** o **Gasto**.
3. Escribir el monto, elegir la categoría y la fecha, y una descripción opcional.
4. Guardar. El movimiento aparece en Transacciones y actualiza el resumen del mes.

## 4. Presupuestos y alertas

- Definir un presupuesto por categoría.
- La app avisa cuando el gasto llega al **80 %** del presupuesto y marca cuando se **excede
  (>100 %)**.

## 5. Asistente de IA

1. Abrir **Asistente**.
2. Escribir una pregunta ("¿en qué estoy gastando de más?") y pulsar **Analizar mis finanzas**,
   o pulsar el **micrófono** y preguntar por voz (la respuesta se lee en voz alta).
3. Bajo el título aparece el aviso de que se habla con una IA, y una etiqueta que indica si
   respondió el **agente de IA** o el **análisis local** (sin conexión).

> El micrófono pide permiso la primera vez. Si se niega, el asistente sigue funcionando por
> texto.

## 6. Exportar a CSV

Desde el resumen/transacciones se puede exportar el mes a **CSV** (separador `;`, compatible
con Excel).

## 7. Cerrar sesión

Desde **Perfil → Cerrar sesión**.

## 8. Problemas frecuentes

| Síntoma | Solución |
|---|---|
| El asistente responde "sin conexión" | No hay red o la clave de IA no está; usa el análisis local. Revisa la conexión. |
| "El agente de IA no está configurado" | Falta la clave de Gemini en `local.properties` al compilar. |
| El micrófono no funciona | Conceder el permiso de micrófono, o usar la entrada por texto. |
