import org.gradle.kotlin.dsl.mavenCentral

pluginManagement {
    includeBuild("../")
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}
rootProject.name = "sqlite-kt-tests"
