package com.mineinabyss.sqlite.codegen

import androidx.sqlite.SQLiteException
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ExperimentalKotlinPoetApi
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.asClassName
import dev.kord.codegen.kotlinpoet.addClass
import dev.kord.codegen.kotlinpoet.addFunction
import dev.kord.codegen.kotlinpoet.addProperty
import me.dvyy.sqlite.Database
import me.dvyy.sqlite.Transaction
import me.dvyy.sqlite.statement.SelectStatement
import net.sf.jsqlparser.expression.ExpressionVisitorAdapter
import net.sf.jsqlparser.expression.JdbcNamedParameter
import net.sf.jsqlparser.expression.JdbcParameter
import net.sf.jsqlparser.parser.CCJSqlParserUtil
import net.sf.jsqlparser.statement.StatementVisitorAdapter
import net.sf.jsqlparser.statement.delete.Delete
import net.sf.jsqlparser.statement.insert.Insert
import net.sf.jsqlparser.statement.select.Select
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.tasks.SourceSetContainer
import java.io.File
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.io.path.walk


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
                    generateHelloWorld(project, source, outputDir)

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

    @OptIn(ExperimentalPathApi::class, ExperimentalKotlinPoetApi::class)
    private fun generateHelloWorld(project: Project, source: Directory, outputDir: File) {
        val files =
            source.asFile.toPath().resolve("schema").walk().filter { it.toString().endsWith(".sql") }.associateWith {
                it.readText()
            }.toList()
        val queries =
            source.asFile.toPath().resolve("queries").walk().filter { it.toString().endsWith(".sql") }.associateWith {
                it.readText()
            }.toList()
        var result = 0
        val sql = Database("test.db", useWAL = false) {
//            files.forEach {
//                try {
//                    exec(it.second)
//                } catch (e: Exception) {
////                    project.logger.error("Error executing ${it.first}: ${it.second}")
//                    throw GradleException("""
//                        Could not create database schema due to file ${it.first}:
//                        == Error ==
//                        ${e.message}
//                        == Query ==
//                        ${it.second}
//                        ===========
//                    """.trimIndent())
//                }
//            }
            val fileSpec = dev.kord.codegen.kotlinpoet.FileSpec("com.mineinabyss.sqlite.generated", "HelloWorld") {
                addClass("HelloWorld") {
                    queries.flatMap { (file, query) ->
                        query.split(";").filter { !it.isBlank() }.map { file to it.trim() }
                    }.forEach { (file, namedQuery) ->
                        val name = namedQuery.lines().first().removePrefix("--").trim()
                        val query = namedQuery.lines().drop(1).joinToString("\n")
                        try {
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
                            val paramNames = mutableListOf<String>()
                            val expressionVisitor: ExpressionVisitorAdapter<*> =
                                object : ExpressionVisitorAdapter<Any?>() {
                                    override fun <S> visit(jdbcParameter: JdbcParameter, context: S?): Any? {
                                        project.logger.lifecycle("Visited $jdbcParameter")
                                        if (jdbcParameter.toString() !in paramNames)
                                            paramNames += jdbcParameter.toString()
                                        return 1
                                    }

                                    override fun <S> visit(jdbcParameter: JdbcNamedParameter, context: S?): Any? {
                                        project.logger.lifecycle("Visited $jdbcParameter")
                                        if (jdbcParameter.name !in paramNames)
                                            paramNames += jdbcParameter.name
                                        return 1
                                    }

                                }
                            val statementVisitor = object : StatementVisitorAdapter<Any?>() {
                                override fun <S> visit(select: Select, context: S) {
                                    select.withItemsList?.forEach { it.accept(this, context) }
                                    select.plainSelect?.where?.accept(expressionVisitor, context)
                                }

                                override fun <S : Any?> visit(insert: Insert, context: S?) {
                                    insert.withItemsList?.forEach { it.accept(this, context) }
                                    insert.values?.expressions?.accept(expressionVisitor, context)
                                }

                                override fun <S : Any?> visit(delete: Delete, context: S?) {
                                    delete.withItemsList?.forEach { it.accept(this, context) }
                                    delete.where?.accept(expressionVisitor, context)
                                }
                            }
                            CCJSqlParserUtil.parse(query).accept(statementVisitor)
                            val contextClass = addClass("Context$name") {
                                colNames.forEachIndexed { id, name ->
                                    addProperty(name, Int::class) {
                                        initializer("%L", id)
                                    }
                                }
                            }
                            val contextClassName = ClassName("", contextClass.name!!)
                            addFunction(name) {
                                paramNames.forEach { addParameter(it, Any::class) }
                                returns(SelectStatement::class.asClassName().parameterizedBy(contextClassName))
                                contextParameter("tx", Transaction::class)
                                addCode("return tx.select<%T>(%S,%T(),", contextClassName, query, contextClassName)
                                paramNames.forEach {
                                    addCode("%N,", it)
                                }
                                addCode(")")
//                                        addCode("bindParams(${paramNames.joinToString(", ") { it }})")
                            }
//                    CCJSqlParserUtil.newParser(query)
//                    val opcodes = select("$query").map {
//                        Explain(
//                            addr = getInt(0),
//                            opcode = getText(1),
//                            p1 = getInt(2),
//                            p2 = getInt(3),
//                            p3 = getInt(4),
//                            p4 = getText(5),
//                            p5 = getInt(6),
//                            comment = getText(7)
//                        )
//                    }
//                    println(opcodes.joinToString("\n") { it.toString() })
                        } catch (e: SQLiteException) {
//                    project.logger.error("Error executing ${it.first}: ${it.second}")
                            throw GradleException(
                                """
== Could not compile query ${file.relativeTo(source.asFile.toPath())} ==
${e.message}
== Query ==
$query
==========="""
                            )
                        }
                    }
                }
            }
            fileSpec.writeTo(outputDir)
//            exec("INSERT INTO test VALUES (1)")
//            result = getSingle("SELECT * FROM test") { getInt(0) }
        }

//        val helloWorldClass = TypeSpec.classBuilder("HelloWorld")
//            .addFunction(
//                FunSpec.builder("greet")
//                    .returns(String::class)
//                    .addStatement("return %S", "Hello from SQLite Codegen! Result was $result")
////                    .addStatement("return %S", "Hello from SQLite Codegen! ${source.asFile.listFiles().toList()}")
//                    .build()
//            )
//            .build()

    }
}
