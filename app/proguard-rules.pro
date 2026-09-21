# Reglas de R8 para la compilacion de publicacion.
#
# R8 elimina el codigo que no se alcanza desde ningun punto de entrada y renombra
# el que sobrevive. Su analisis sigue llamadas explicitas, de modo que TODO lo que
# se instancia por reflexion le resulta invisible: si no se declara aqui, R8 lo
# borra y la aplicacion falla en tiempo de ejecucion, no al compilar.
#
# Cada bloque documenta por que existe. Una regla sin justificacion tiende a
# quedarse para siempre y a proteger codigo que ya nadie usa.

# --- WorkManager -------------------------------------------------------------
# El planificador guarda el NOMBRE de la clase del trabajador en su base de datos
# y la instancia por reflexion al despertar. R8 no ve ninguna llamada al
# constructor, asi que hay que conservarlo junto con su firma exacta.
-keep class com.nexcode.gastos.notificaciones.RecordatoriosWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# --- Room --------------------------------------------------------------------
# Room genera en tiempo de compilacion las implementaciones de la base y de los
# DAO, y las localiza por nombre a partir de la clase abstracta. Las entidades se
# conservan con sus campos porque el codigo generado los lee posicionalmente.
-keep class com.nexcode.gastos.data.local.entity.** { *; }
-keep class com.nexcode.gastos.data.local.NexcodeDatabase { *; }
-keep class com.nexcode.gastos.data.local.NexcodeDatabase_Impl { *; }

# --- Corrutinas --------------------------------------------------------------
# El cargador de servicios de kotlinx-coroutines busca su manejador de
# excepciones por nombre de recurso; sin esta regla el aviso aparece en cada
# compilacion y el manejador queda sin registrar.
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# --- Diagnostico -------------------------------------------------------------
# Conserva los numeros de linea en las trazas de error. Sin esto, un fallo en
# produccion se reporta sin indicar donde ocurrio.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- Agente de IA ------------------------------------------------------------
# No hace falta ninguna regla. El agente habla con la API por HttpURLConnection
# y org.json, ambas de la plataforma: no hay reflexion ni serializadores
# generados que R8 pueda borrar sin darse cuenta. Es la ventaja practica de
# haber prescindido del SDK.
