plugins {
    id(libs.plugins.kotlin.jvm.get().pluginId)
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
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
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

publishing {
    repositories {
        maven {
            name = "mineinabyss"
            url = uri("https://repo.mineinabyss.com/releases")
            credentials(PasswordCredentials::class)
        }
        maven {
            name = "mineinabyssSnapshots"
            url = uri("https://repo.mineinabyss.com/snapshots")
            credentials(PasswordCredentials::class)
        }
    }
}
