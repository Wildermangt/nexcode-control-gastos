import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

// CAPA DE PRESENTACION + ensamblaje de la aplicacion.
// Es el unico modulo que conoce a :data, y solo para inyectar las
// implementaciones concretas dentro del contenedor de dependencias.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// El plugin de Google solo se aplica si existe google-services.json.
//
// Es el mismo criterio que se usa con keystore.properties: quien clone el
// proyecto sin las credenciales tiene que poder compilar igual. Sin ese archivo
// la aplicacion funciona entera contra Room y la sincronizacion queda inactiva,
// que es exactamente el comportamiento cuando no hay nube configurada.
val hayFirebase = file("google-services.json").exists()
if (hayFirebase) {
    apply(plugin = "com.google.gms.google-services")
}

// Credenciales de firma leidas de un archivo fuera del control de versiones.
// Si el archivo no existe la compilacion sigue funcionando: solo se pierde la
// firma de publicacion, de modo que quien clone el proyecto puede compilar en
// depuracion sin tener las claves.
// Clave de la API del agente de IA. Vive en local.properties, que ya esta en
// .gitignore: nunca viaja al repositorio. Si no esta, la aplicacion compila
// igual y el asistente responde con su motor local.
val propiedadesLocales = Properties().apply {
    val archivo = rootProject.file("local.properties")
    if (archivo.exists()) archivo.inputStream().use { load(it) }
}
val claveGemini: String = propiedadesLocales.getProperty("gemini.apiKey").orEmpty().trim()

val archivoDeFirma = rootProject.file("keystore.properties")
val firmaDisponible = archivoDeFirma.exists()
val credenciales = Properties().apply {
    if (firmaDisponible) archivoDeFirma.inputStream().use { load(it) }
}

android {
    namespace = "com.nexcode.gastos"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nexcode.gastos"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "GEMINI_API_KEY", "\"" + claveGemini + "\"")

        // Permite que el codigo sepa si la nube esta configurada sin tener que
        // preguntarle a Firebase, que fallaria al inicializarse.
        buildConfigField("boolean", "HAY_NUBE", hayFirebase.toString())
    }

    signingConfigs {
        if (firmaDisponible) {
            create("publicacion") {
                storeFile = rootProject.file(credenciales.getProperty("storeFile"))
                storePassword = credenciales.getProperty("storePassword")
                keyAlias = credenciales.getProperty("keyAlias")
                keyPassword = credenciales.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // R8 elimina el codigo no alcanzado y renombra el resto.
            // Las reglas propias estan en proguard-rules.pro.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (firmaDisponible) {
                signingConfig = signingConfigs.getByName("publicacion")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":data"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
}
