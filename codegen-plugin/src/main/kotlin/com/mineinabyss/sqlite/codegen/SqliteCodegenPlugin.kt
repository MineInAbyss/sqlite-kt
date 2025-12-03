package com.mineinabyss.sqlite.codegen

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.internal.extensions.stdlib.capitalized

class SqliteCodegenPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val objects = project.objects
        val databaseContainer = objects.domainObjectContainer(SqliteCodegenExtension::class.java) { name ->
            objects.newInstance(SqliteCodegenExtension::class.java, name).apply {
                outputDir.convention(project.layout.buildDirectory.dir("generated/source/sqlite"))
                packageName.convention("me.dvyy.sqlite.generated")
                mainClassName.convention(name.capitalized() + "Database")
                sourceDir.convention(project.layout.projectDirectory.dir("src/main/sql/$name"))
                generatedDatabasePath.convention(project.layout.buildDirectory.file("generated/databases/$name.db"))
            }
        }
        val extension = project.extensions.add("sqliteCodegen", databaseContainer)
        databaseContainer.all { db ->
            val task = project.tasks.register(
                "generateSqliteBindingsFor${db.name.capitalized()}",
                GenerateSqliteBindingsTask::class.java
            ) {
                it.group = "codegen"
                it.description = "Generate Kotlin code from SQL files"

                it.source = db.sourceDir.get().asFile.toPath()
                it.packageName = db.packageName.get()
                it.mainClassName = db.mainClassName.get()
                it.outputDir = db.outputDir.get().asFile.toPath()
                it.databaseLocation = db.generatedDatabasePath.get().asFile.toPath()
            }

            // Add generated sources to the main source set
            project.plugins.withId("org.jetbrains.kotlin.jvm") {
                val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
                sourceSets.getByName("main") { sourceSet ->
                    sourceSet.java.srcDir(task.map { it.outputDir })
                }
            }
        }

        project.tasks.register("generateSqliteBindings") {
            it.group = "codegen"
            it.description = "Generate Kotlin code from SQL files"
            it.dependsOn(project.tasks.withType(GenerateSqliteBindingsTask::class.java))
        }
    }
}
