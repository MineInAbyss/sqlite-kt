package com.mineinabyss.sqlite.codegen

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile

interface SqliteCodegenExtension {
    @get:InputDirectory
    val sourceDir: DirectoryProperty

    @get:Input
    val packageName: Property<String>

    @get:OutputDirectory
    val outputDir: DirectoryProperty

    @get:OutputFile
    val databaseLocation: RegularFileProperty
}
