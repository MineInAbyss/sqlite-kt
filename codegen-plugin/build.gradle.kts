plugins {
    kotlin("jvm")
    alias(miaLibs.plugins.mia.publication)
    `java-gradle-plugin`
    antlr
}

repositories {
    mavenCentral()
    google()
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    antlr(libs.antlr)
    implementation(libs.kotlinpoet)
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
        outputDirectory =
            project.layout.buildDirectory.file("generated/sources/main/java/me/dvyy/sqlite/generated/antlr")
                .get().asFile
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
