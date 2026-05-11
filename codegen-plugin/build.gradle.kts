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
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
    implementation(gradleApi())
    implementation(libs.kotlinpoet.extensions)
    implementation(project(":")) {
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core")
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core-jvm")
    }
    implementation(libs.kotlinx.coroutines.core.jvm)
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
