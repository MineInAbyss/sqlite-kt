package com.mineinabyss.sqlite.codegen

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory

interface SqliteCodegenExtension {
    val name: String

    /** Directory containing SQL files to generate bindings from. */
    @get:InputDirectory
    val sourceDir: DirectoryProperty

    /** Package name to output generated code to. */
    @get:Input
    val packageName: Property<String>

    /** Name of the main class with schema creation logic and references to generated queries.*/
    @get:Input
    val mainClassName: Property<String>

    /** Output directory for generated code, will be added as generated source root. */
//    @get:OutputDirectory
    val outputDir: DirectoryProperty

    /** Path to place generated sqlite database (for attaching to in IDE.) */
//    @get:OutputFile
    val generatedDatabasePath: RegularFileProperty
}
