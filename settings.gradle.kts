pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://repo.mineinabyss.com/releases")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "sqlite-kt"

include("codegen-plugin")

//includeBuild("test-codegen")

dependencyResolutionManagement {
    val catalogVersion: String by settings

    repositories {
        maven("https://repo.mineinabyss.com/releases")
        maven("https://repo.mineinabyss.com/snapshots")
    }

    versionCatalogs {
        create("miaLibs") {
            from("com.mineinabyss:catalog:$catalogVersion")
        }
    }
}
