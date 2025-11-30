import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.2.0"
    id("com.mineinabyss.sqlitekt.codegen")
}

repositories {
    mavenCentral()
    google()
}

dependencies {
    implementation("me.dvyy:sqlite-kt")
}

//sqliteCodegen {
//    sourceDir = "src/main/sql"
//}
val compileKotlin: KotlinCompile by tasks
compileKotlin.compilerOptions {
    freeCompilerArgs.set(listOf("-Xcontext-parameters"))
}