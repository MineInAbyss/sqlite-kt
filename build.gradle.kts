plugins {
    alias(miaLibs.plugins.mia.kotlin.multiplatform)
    alias(miaLibs.plugins.mia.publication)
}

repositories {
    mavenCentral()
    google()
}

kotlin {
    jvmToolchain(17)
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
