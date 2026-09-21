import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// CAPA DE DOMINIO — MODULO MULTIPLATAFORMA
//
// El codigo vive en `commonMain`, que no puede usar la biblioteca estandar de
// Java ni ninguna API de Android: solo Kotlin y bibliotecas multiplataforma.
// Esa restriccion la impone el compilador, y es la que permite compilar la
// misma logica de negocio para la JVM (Android y escritorio) y para JavaScript.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    // Destino JVM: lo consumen el modulo :data y la aplicacion Android.
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    // Destino JavaScript: demuestra que el dominio compila fuera de la JVM.
    js(IR) {
        nodejs()
        binaries.library()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                api(libs.kotlinx.datetime)
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}
