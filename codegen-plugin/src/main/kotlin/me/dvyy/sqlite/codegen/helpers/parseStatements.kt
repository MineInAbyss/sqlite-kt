package me.dvyy.sqlite.codegen.helpers

import me.dvyy.sqlite.codegen.ParsedStatement
import me.dvyy.sqlite.generated.antlr.SQLiteLexer
import me.dvyy.sqlite.generated.antlr.SQLiteParser
import me.dvyy.sqlite.generated.antlr.SQLiteParserBaseListener
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
        val name =
            if (includeNames) text.lineSequence().drop(statement.start.line - 2).first().removePrefix("--").trim()
            else "Unnamed"
        val sql = statement.start.inputStream.getText(
            Interval.of(
                statement.start.startIndex,
                statement.stop.stopIndex
            )
        )
        ParsedStatement(
            name = name,
            sql = sql,
            parsed = statement,
            binds = binds
        )
    }
}