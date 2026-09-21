pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // PREFER_SETTINGS y no FAIL_ON_PROJECT_REPOS: el objetivo JavaScript de
    // Kotlin Multiplatform necesita registrar el repositorio de distribuciones
    // de Node.js para poder ejecutar sus pruebas.
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Nexcode"

// Una capa = un modulo Gradle. El compilador vigila que nadie rompa la direccion
// de las dependencias:  app -> domain <- data
include(":app")
include(":data")
include(":domain")
