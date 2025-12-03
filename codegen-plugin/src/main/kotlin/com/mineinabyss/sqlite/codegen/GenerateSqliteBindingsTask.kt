package com.mineinabyss.sqlite.codegen

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ExperimentalKotlinPoetApi
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.asClassName
import dev.kord.codegen.kotlinpoet.FileSpec
import dev.kord.codegen.kotlinpoet.addClass
import dev.kord.codegen.kotlinpoet.addFunction
import dev.kord.codegen.kotlinpoet.addProperty
import me.dvyy.sqlite.Database
import me.dvyy.sqlite.Transaction
import me.dvyy.sqlite.WriteTransaction
import me.dvyy.sqlite.statement.SelectStatement
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.*
import java.nio.file.Path
import kotlin.io.path.*


open class GenerateSqliteBindingsTask : DefaultTask() {
    @InputDirectory
    lateinit var source: Path

    @Input
    lateinit var packageName: String

    @Input
    lateinit var mainClassName: String

    @OutputDirectory
    lateinit var outputDir: Path

    @OutputFile
    lateinit var databaseLocation: Path

    @OptIn(ExperimentalPathApi::class, ExperimentalKotlinPoetApi::class)
    @TaskAction
    fun generate() {
        source.resolve("schema").walk().filter { it.toString().endsWith(".sql") }.associateWith {
            it.readText()
        }.toList()

        val schemaSource = source / "schema" / "Schema.sql"
        if (schemaSource.notExists()) throw GradleException("Could not find schema source file: $schemaSource")
        val schema = source.resolve("schema").resolve("Schema.sql").readText()
            .split("^--.*".toRegex(RegexOption.MULTILINE))
            .map { it.trim().trim { it == '\n' } }
            .filter { it.isNotBlank() }

        val queryFiles = (source / "queries").walk().filter { it.toString().endsWith(".sql") }

        databaseLocation.deleteIfExists()
        Database(databaseLocation.absolutePathString(), useWAL = false) {
            // Verify schema doesn't error
            schema.forEach { query ->
                printingErrorMessages(query, "Error creating database schema") {
                    exec(query)
                }
            }

            val seenColumnNames = mutableListOf<List<String>>()

            // Create query files
            queryFiles.forEach { queriesFile ->
                val queries = queriesFile.readText()
                    .split("^--".toRegex(RegexOption.MULTILINE))
                    .filter { !it.isBlank() }
                val fileSpec = FileSpec(packageName, queriesFile.nameWithoutExtension) {
                    addClass(queriesFile.nameWithoutExtension) {
                        queries.forEach { namedQuery ->
                            val name = namedQuery.lines().first().removePrefix("--").trim()
                            val query = namedQuery.lines().drop(1).joinToString("\n").trim { it == '\n' }
                            if (query.isBlank()) return@forEach
                            printingErrorMessages(
                                query,
                                "Could not compile query $name in ${queriesFile.relativeTo(source)}"
                            ) {
                                val colNames = prepare(query) {
                                    val seen = mutableMapOf<String, Int>()
                                    getColumnNames().map { name ->
                                        if (name in seen) {
                                            val count = seen[name]!! + 1
                                            seen[name] = count
                                            "${name}$count"
                                        } else {
                                            seen[name] = 1
                                            name
                                        }
                                    }
                                }

                                // parse parameters using :name style from query using regex
                                val paramNames =
                                    ":([a-zA-Z][a-zA-Z0-9]*)".toRegex().findAll(query).map { it.groupValues[1] }
                                        .distinct()
                                        .toList()
                                val parsedQuery = when {
                                    query.trim().lowercase().startsWith("insert") -> "insert"
                                    query.trim().lowercase().startsWith("select") -> "select"
                                    else -> "other"
                                }

                                addFunction(name) {
                                    paramNames.forEach { addParameter(it, Any::class) }
                                    when (parsedQuery) {
                                        "insert" -> {
                                            contextParameter("tx", WriteTransaction::class)
                                            returns(Long::class)
                                            addCode("return tx.insert(%S,", query)
                                        }

                                        "select" -> {
                                            val existingClass = seenColumnNames.indexOf(colNames)
                                            val name =
                                                "Context${if (existingClass != -1) existingClass else seenColumnNames.size}"
                                            if (existingClass == -1) {
                                                seenColumnNames.add(colNames)
                                                this@addClass.addClass(name) {
                                                    colNames.forEachIndexed { id, name ->
                                                        addProperty(name, Int::class) {
                                                            initializer("%L", id)
                                                        }
                                                    }
                                                }
                                            }
                                            val contextClassName = ClassName("", name)
                                            contextParameter("tx", Transaction::class)
                                            returns(
                                                SelectStatement::class.asClassName()
                                                    .parameterizedBy(contextClassName)
                                            )
                                            addCode(
                                                "return tx.select<%T>(%S,%T(),",
                                                contextClassName,
                                                query,
                                                contextClassName
                                            )
                                        }

                                        else -> {
                                            contextParameter("tx", WriteTransaction::class)
                                            returns(Unit::class)
                                            addCode("tx.exec(%S,", query)
                                        }
                                    }
                                    paramNames.forEach {
                                        addCode("%N,", it)
                                    }
                                    addCode(")")
                                }
                            }
                        }
                    }
                }
                fileSpec.writeTo(outputDir)
            }
        }

        // Create main DB Schema file
        FileSpec(packageName, mainClassName) {
            addClass(mainClassName) {
                val queryFileClasses = queryFiles.map { ClassName(this@FileSpec.packageName, it.nameWithoutExtension) }
                addFunction("create") {
                    contextParameter("tx", WriteTransaction::class)
                    schema.forEach {
                        addCode("tx.exec(%S)\n", it)
                    }
                }

                // Create properties calling empty constructor for each file class
                queryFileClasses.forEach {
                    addProperty(it.simpleName.decapitalize(), it) {
                        initializer("%T()", it)
                    }
                }
            }
        }.writeTo(outputDir)

    }
}