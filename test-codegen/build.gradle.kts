import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    application
    id("org.jetbrains.kotlin.jvm") version "2.2.0"
    id("me.dvyy.sqlite.codegen")
}

repositories {
    mavenCentral()
    google()
}

dependencies {
    implementation("me.dvyy.sqlite:sqlite-kt")
}

val compileKotlin: KotlinCompile by tasks
compileKotlin.compilerOptions {
    freeCompilerArgs.set(listOf("-Xcontext-parameters"))
}

sqliteKt {
    register("main") {
        packageName = "me.dvyy.sqlite.generated"
    }
    register("other") {
        packageName = "me.dvyy.sqlite.generated.other"
    }
}