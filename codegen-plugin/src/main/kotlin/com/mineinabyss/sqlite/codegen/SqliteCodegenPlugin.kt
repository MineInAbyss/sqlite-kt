package com.mineinabyss.sqlite.codegen

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer

class SqliteCodegenPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("sqliteCodegen", SqliteCodegenExtension::class.java)
        extension.apply {
            outputDir.convention(project.layout.buildDirectory.dir("generated/source/sqlite"))
            packageName.convention("me.dvyy.sqlite.generated")
            mainClassName.convention("Schema")
            sourceDir.convention(project.layout.projectDirectory.dir("src/main/sql"))
            generatedDatabasePath.convention(project.layout.buildDirectory.file("schema.db"))
        }

        // Register a task to generate code
        val generateTask = project.tasks.register("generateSqliteBindings", GenerateSqliteBindingsTask::class.java) {
            it.group = "codegen"
            it.description = "Generate Kotlin code from SQL files"

            it.source = extension.sourceDir.get().asFile.toPath()
            it.packageName = extension.packageName.get()
            it.mainClassName = extension.mainClassName.get()
            it.outputDir = extension.outputDir.get().asFile.toPath()
            it.databaseLocation = extension.generatedDatabasePath.get().asFile.toPath()
        }

        project.afterEvaluate {
            // Add generated sources to the main source set
            project.plugins.withId("org.jetbrains.kotlin.jvm") {
                val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
                sourceSets.getByName("main") { sourceSet ->
                    sourceSet.java.srcDir(extension.outputDir)
                }
            }

            // Make compileKotlin depend on generation task
            project.tasks.findByName("compileKotlin")?.dependsOn(generateTask)
        }
    }
}
