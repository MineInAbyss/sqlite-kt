plugins {
    alias(libs.plugins.kotlin.multiplatform)
    `maven-publish`
}

repositories {
    mavenCentral()
    google()
}

kotlin {
    jvm()

    compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")
    }

    sourceSets {
        commonMain {
            dependencies {
                dependencies {
                    api(libs.androidx.sqlite)
                    implementation(libs.kotlinx.coroutines.core)
                    implementation(libs.log4k)
                }
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.kotest.assertions)
            }
        }
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
