package me.dvyy.sqlite.codegen.helpers

import me.dvyy.sqlite.codegen.ParsedStatement
import me.dvyy.sqlite.generated.antlr.*
import org.antlr.v4.runtime.ANTLRInputStream
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.misc.Interval
import org.antlr.v4.runtime.tree.ParseTreeWalker
import org.antlr.v4.runtime.tree.TerminalNode

/**
 * Parses sqlite statements from generated ANTLR4 parser.
 */
internal fun parseStatements(text: String, includeNames: Boolean = true): List<ParsedStatement> {
    val lexer = SQLiteLexer(ANTLRInputStream(text))
    val parser = SQLiteParser(CommonTokenStream(lexer))
    val statementList = parser.parse().sql_stmt_list()
    return statementList.sql_stmt().map { statement ->
        val nodes = mutableListOf<TerminalNode>()
        ParseTreeWalker.DEFAULT.walk(object : SQLiteParserBaseListener() {
            override fun visitTerminal(node: TerminalNode) {
                nodes += node
            }
        }, statement)
        val binds = nodes.filter { it.text.startsWith(":") }.map { it.text.removePrefix(":") }.distinct()
        val nameLine =
            if (includeNames) parseNameLine(text.lineSequence().drop(statement.start.line - 2).first())
            else ParsedStatement.Function("Unnamed", listOf())
        val sql = statement.start.inputStream.getText(
            Interval.of(
                statement.start.startIndex,
                statement.stop.stopIndex
            )
        )
        ParsedStatement(
            name = nameLine.name,
            sql = sql,
            parsed = statement,
            binds = binds,
            functionParameters = nameLine.parameters.associate { it.name to it.type }
        )
    }
}

internal fun parseNameLine(text: String): ParsedStatement.Function {
    val trimmed = text.removePrefix("--").trim()
    if (!trimmed.startsWith("fun ")) return ParsedStatement.Function(trimmed, listOf())
    val declaration = KotlinParser(CommonTokenStream(KotlinLexer(ANTLRInputStream(trimmed)))).functionDeclaration()
    val name = declaration.simpleIdentifier().text
    val parameters = mutableListOf<ParsedStatement.Parameter>()
    ParseTreeWalker.DEFAULT.walk(object : KotlinParserBaseListener() {
        override fun enterParameter(ctx: KotlinParser.ParameterContext) {
            parameters += ParsedStatement.Parameter(ctx.simpleIdentifier().text, ctx.type().text)
        }
    }, declaration.functionValueParameters())

    return ParsedStatement.Function(name, parameters)
}