package com.mineinabyss.sqlite.codegen

import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.TypeSpec
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.tasks.SourceSetContainer
import java.io.File

class SqliteCodegenPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("sqliteCodegen", SqliteCodegenExtension::class.java)
        val source = project.layout.projectDirectory.dir(extension.sourceDir)
        project.afterEvaluate {
            val outputDir = File(project.layout.buildDirectory.get().asFile, "generated/source/sqlite")

            // Register a task to generate code
            val generateTask = project.tasks.register("generateSqliteCode") {
                it.group = "codegen"
                it.description = "Generate Kotlin code from SQL files"

                it.doLast {
                    outputDir.mkdirs()

                    // For now, generate a simple HelloWorld class
                    generateHelloWorld(source, outputDir)

                    project.logger.lifecycle("Generated HelloWorld class to: ${outputDir.absolutePath}")
                    project.logger.lifecycle("Configured source directory: ${extension.sourceDir}")
                }
            }

            // Add generated sources to the main source set
            project.plugins.withId("org.jetbrains.kotlin.jvm") {
                val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
                sourceSets.getByName("main") { sourceSet ->
                    sourceSet.java.srcDir(outputDir)
                }
            }

            // Make compileKotlin depend on generation task
            project.tasks.findByName("compileKotlin")?.dependsOn(generateTask)
        }
    }

    private fun generateHelloWorld(source: Directory, outputDir: File) {
        val helloWorldClass = TypeSpec.classBuilder("HelloWorld")
            .addFunction(
                FunSpec.builder("greet")
                    .returns(String::class)
                    .addStatement("return %S", "Hello from SQLite Codegen! ${source.asFile.listFiles().toList()}")
                    .build()
            )
            .build()

        val fileSpec = FileSpec.builder("com.mineinabyss.sqlite.generated", "HelloWorld")
            .addType(helloWorldClass)
            .build()

        fileSpec.writeTo(outputDir)
    }
}
