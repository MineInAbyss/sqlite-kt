plugins {
    id(libs.plugins.kotlin.jvm.get().pluginId)
    `java-gradle-plugin`
    antlr
}

repositories {
    mavenCentral()
    google()
}

dependencies {
    antlr("org.antlr:antlr4:4.13.2")
    implementation("com.squareup:kotlinpoet:2.1.0")
    implementation(gradleApi())
    implementation("dev.kord.codegen:kotlinpoet:1.0.2")
    implementation(project(":")) {
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core")
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core-jvm")
    }
    implementation(libs.kotlinx.coroutines.core.jvm)
}

tasks {
    generateGrammarSource {
        outputDirectory = file("${project.buildDir}/generated/sources/main/java/antlr/me/dvyy/sqlite/generated/antlr")
        arguments = listOf("-package", "me.dvyy.sqlite.generated.antlr")
    }
    compileKotlin {
        dependsOn(generateGrammarSource)
    }
}

gradlePlugin {
    plugins {
        create("sqliteCodegen") {
            id = "com.mineinabyss.sqlitekt.codegen"
            implementationClass = "com.mineinabyss.sqlite.codegen.SqliteCodegenPlugin"
        }
    }
}

sourceSets {
    main {
        java.srcDir("build/generated/sources/main/java")
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}