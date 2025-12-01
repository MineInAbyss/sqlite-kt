package com.mineinabyss.sqlite.codegen

import androidx.sqlite.SQLiteException
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
import net.sf.jsqlparser.JSQLParserException
import net.sf.jsqlparser.expression.ExpressionVisitorAdapter
import net.sf.jsqlparser.expression.JdbcNamedParameter
import net.sf.jsqlparser.expression.JdbcParameter
import net.sf.jsqlparser.parser.CCJSqlParserUtil
import net.sf.jsqlparser.statement.StatementVisitorAdapter
import net.sf.jsqlparser.statement.delete.Delete
import net.sf.jsqlparser.statement.insert.Insert
import net.sf.jsqlparser.statement.select.Select
import net.sf.jsqlparser.util.TablesNamesFinder
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

    @OutputDirectory
    lateinit var outputDir: Path

    @OutputFile
    lateinit var databaseLocation: Path

    @OptIn(ExperimentalPathApi::class, ExperimentalKotlinPoetApi::class)
    @TaskAction
    fun generate() {
        val dbLocation = project.layout.buildDirectory.dir("schema.db").get().asFile.toPath()
        source.resolve("schema").walk().filter { it.toString().endsWith(".sql") }.associateWith {
            it.readText()
        }.toList()
        val queries =
            source.resolve("queries").walk().filter { it.toString().endsWith(".sql") }.associateWith {
                it.readText()
            }.toList()
        dbLocation.deleteIfExists()
        Database(dbLocation.absolutePathString(), useWAL = false) {
            FileSpec(packageName, "Schema") {
                addClass("Schema") {
                    val schemaSource = source / "schema" / "Schema.sql"
                    if (schemaSource.notExists()) throw GradleException("Could not find schema source file: $schemaSource")
                    val schema = source.resolve("schema").resolve("Schema.sql").readText()
                        .split("^--.*".toRegex(RegexOption.MULTILINE))
                        .map { it.trim().trim { it == '\n' } }
                        .filter { it.isNotBlank() }
                    addFunction("create") {
                        contextParameter("tx", WriteTransaction::class)

                        // Verify schema doesn't error
                        schema.forEach { query ->
                            try {
                                exec(query)
                            } catch (e: SQLiteException) {
                                throw GradleException(
                                    """Error creating database schema
╔══ Error
${e.message?.prependIndent("║ ")}
╠══ Query
${query.prependIndent("║ ")}
╚══
                                """
                                )
                            }
                        }
                        schema.forEach {
                            addCode("tx.exec(%S)\n", it)
                        }
                    }
                }
            }.writeTo(outputDir)

            val fileSpec = FileSpec(packageName, "HelloWorld") {
                addClass("HelloWorld") {
                    queries.flatMap { (file, query) ->
                        query.split("^--".toRegex(RegexOption.MULTILINE)).filter { !it.isBlank() }
                            .map { file to it.trim() }
                    }.forEach { (file, namedQuery) ->
                        val name = namedQuery.lines().first().removePrefix("--").trim()
                        val query = namedQuery.lines().drop(1).joinToString("\n").trim { it == '\n' }
                        if (query.isBlank()) return@forEach
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
                                    override fun <S> visit(jdbcParameter: JdbcParameter, context: S?): Any {
                                        project.logger.lifecycle("Visited $jdbcParameter")
                                        if (jdbcParameter.toString() !in paramNames)
                                            paramNames += jdbcParameter.toString()
                                        return 1
                                    }

                                    override fun <S> visit(jdbcParameter: JdbcNamedParameter, context: S?): Any {
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
                            val parsedQuery = CCJSqlParserUtil.parse(query)
                            TablesNamesFinder.findTables(query)
                            parsedQuery.accept(statementVisitor)
                            addFunction(name) {
                                paramNames.forEach { addParameter(it, Any::class) }
                                when (parsedQuery) {
                                    is Insert -> {

                                        contextParameter("tx", WriteTransaction::class)
                                        returns(Long::class)
                                        addCode("return tx.insert(%S,", query)
                                    }

                                    is Select -> {
                                        val contextClass = this@addClass.addClass("Context$name") {
                                            colNames.forEachIndexed { id, name ->
                                                addProperty(name, Int::class) {
                                                    initializer("%L", id)
                                                }
                                            }
                                        }
                                        val contextClassName = ClassName("", contextClass.name!!)
                                        contextParameter("tx", Transaction::class)
                                        returns(SelectStatement::class.asClassName().parameterizedBy(contextClassName))
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
                        } catch (e: Exception) {
                            if (e is SQLiteException || e is JSQLParserException)
                                throw GradleException(
                                    """Could not compile query $name in ${file.relativeTo(source)}
╔══ Error
${e.message?.prependIndent("║ ")}
╠══ Query
${query.prependIndent("║ ")}
╚══"""
                                )
                            else throw e
                        }
                    }
                }
            }
            fileSpec.writeTo(outputDir)
        }
    }
}