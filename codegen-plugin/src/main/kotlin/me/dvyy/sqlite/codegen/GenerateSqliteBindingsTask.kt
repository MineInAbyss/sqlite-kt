package me.dvyy.sqlite.codegen

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
import me.dvyy.sqlite.codegen.helpers.parseStatements
import me.dvyy.sqlite.codegen.helpers.printingErrorMessages
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
        val schema = parseStatements(source.resolve("schema").resolve("Schema.sql").readText(), includeNames = false)

        val queryFiles = (source / "queries").walk().filter { it.toString().endsWith(".sql") }

        databaseLocation.deleteIfExists()
        Database(databaseLocation.absolutePathString(), useWAL = false) {
            // Verify schema doesn't error
            schema.forEach { query ->
                printingErrorMessages(
                    query.sql,
                    "Error creating database schema"
                ) {
                    exec(query.sql)
                }
            }

            val seenColumnNames = mutableListOf<List<String>>()

            // Create query files
            queryFiles.forEach { queriesFile ->
                val statements = parseStatements(queriesFile.readText())
                val fileSpec = FileSpec(packageName, queriesFile.nameWithoutExtension) {
                    addClass(queriesFile.nameWithoutExtension) {
                        statements.forEach { statement ->
                            printingErrorMessages(
                                statement.sql,
                                "Could not compile query ${statement.name} in ${queriesFile.relativeTo(source)}"
                            ) {
                                val colNames = prepare(statement.sql) {
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

                                addFunction(statement.name) {
                                    statement.binds.forEach { addParameter(it, Any::class) }
                                    when {
                                        statement.parsed.insert_stmt() != null -> {
                                            contextParameter("tx", WriteTransaction::class)
                                            returns(Long::class)
                                            addCode("return tx.insert(%S,", statement.sql)
                                        }

                                        statement.parsed.select_stmt() != null -> {
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
                                                statement.sql,
                                                contextClassName
                                            )
                                        }

                                        else -> {
                                            contextParameter("tx", WriteTransaction::class)
                                            returns(Unit::class)
                                            addCode("tx.exec(%S,", statement.sql)
                                        }
                                    }
                                    statement.binds.forEach {
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
