rootProject.name = "sqlite-kt"

pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://repo.mineinabyss.com/releases")
        maven("https://repo.mineinabyss.com/snapshots")
    }
}

dependencyResolutionManagement {
    val miaCatalog: String by settings

    repositories {
        maven("https://repo.mineinabyss.com/releases")
        maven("https://repo.mineinabyss.com/snapshots")
    }

    versionCatalogs {
        create("miaLibs").from("com.mineinabyss:catalog:$miaCatalog")
    }
}

include("codegen-plugin")

includeBuild("test-codegen")
