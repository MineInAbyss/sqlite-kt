plugins {
    id("org.jetbrains.kotlin.jvm") version "2.2.0"
    `java-gradle-plugin`
}

repositories {
    mavenCentral()
    google()
}

dependencies {
    implementation("com.squareup:kotlinpoet:2.1.0")
    implementation(gradleApi())
}

gradlePlugin {
    plugins {
        create("sqliteCodegen") {
            id = "com.mineinabyss.sqlitekt.codegen"
            implementationClass = "com.mineinabyss.sqlite.codegen.SqliteCodegenPlugin"
        }
    }
}
