plugins {
    id(libs.plugins.kotlin.jvm.get().pluginId)
    `java-gradle-plugin`
}

repositories {
    mavenCentral()
    google()
}

dependencies {
    implementation("com.squareup:kotlinpoet:2.1.0")
    implementation(gradleApi())
    implementation("com.github.jsqlparser:jsqlparser:5.3")
    implementation("dev.kord.codegen:kotlinpoet:1.0.2")
    implementation(project(":"))  {
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core")
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core-jvm")
    }
    implementation(libs.kotlinx.coroutines.core.jvm)
}

gradlePlugin {
    plugins {
        create("sqliteCodegen") {
            id = "com.mineinabyss.sqlitekt.codegen"
            implementationClass = "com.mineinabyss.sqlite.codegen.SqliteCodegenPlugin"
        }
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}