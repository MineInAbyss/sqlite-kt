plugins {
    alias(miaLibs.plugins.kotlin.multiplatform)
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
                    api(miaLibs.androidx.sqlite.bundled)
                    implementation(miaLibs.kotlinx.coroutines)
                    implementation(libs.log4k)
                }
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(miaLibs.kotlinx.coroutines.test)
                implementation(miaLibs.kotest.assertions)
            }
        }
    }
}
