plugins {
    id(libs.plugins.kotlin.jvm.get().pluginId)
    id("com.mineinabyss.sqlitekt.codegen")
}

repositories {
    mavenCentral()
    google()
}

sqliteCodegen {
    sourceDir = "src/main/sql"
}