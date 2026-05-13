plugins {
    id(miaLibs.plugins.kotlin.jvm.get().pluginId)
    alias(miaLibs.plugins.mia.publication)
    `java-gradle-plugin`
    `maven-publish`
    antlr
}

repositories {
    mavenCentral()
    google()
}

dependencies {
    antlr("org.antlr:antlr4:4.13.2")
    implementation("com.squareup:kotlinpoet:2.1.0")
    implementation("dev.kord.codegen:kotlinpoet:1.0.2")

    compileOnly(miaLibs.gradle.kotlin)
    implementation(gradleApi())
    implementation(miaLibs.kotlinx.coroutines)
    implementation(project(":")) {
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core")
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core-jvm")
    }
}

tasks {
    generateGrammarSource {
        outputDirectory = file("${project.buildDir}/generated/sources/main/java/me/dvyy/sqlite/generated/antlr")
        arguments = listOf("-package", "me.dvyy.sqlite.generated.antlr")
    }
    compileKotlin {
        dependsOn(generateGrammarSource)
    }
}

gradlePlugin {
    plugins {
        create("sqliteKt") {
            id = "me.dvyy.sqlite.codegen"
            implementationClass = "me.dvyy.sqlite.codegen.SqliteCodegenPlugin"
        }
    }
}

sourceSets {
    main {
        java.srcDir("build/generated/sources/main/java")
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}
