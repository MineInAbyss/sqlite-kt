package com.mineinabyss.sqlite.codegen

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property

interface SqliteCodegenExtension {
    val name: String

    /** Directory containing SQL files to generate bindings from. */
    val sourceDir: DirectoryProperty

    /** Package name to output generated code to. */
    val packageName: Property<String>

    /** Name of the main class with schema creation logic and references to generated queries.*/
    val mainClassName: Property<String>

    /** Output directory for generated code, will be added as generated source root. */
    val outputDir: DirectoryProperty

    /** Path to place generated sqlite database (for attaching to in IDE.) */
    val generatedDatabasePath: RegularFileProperty
}
