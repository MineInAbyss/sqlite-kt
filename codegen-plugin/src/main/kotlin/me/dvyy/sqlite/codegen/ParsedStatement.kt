package me.dvyy.sqlite.codegen

import me.dvyy.sqlite.generated.antlr.SQLiteParser

internal data class ParsedStatement(
    val name: String,
    val sql: String,
    val parsed: SQLiteParser.Sql_stmtContext,
    val binds: List<String>,
    val functionParameters: Map<String, String>,
) {
    data class Function(
        val name: String,
        val parameters: List<Parameter>,
    ) {}

    data class Parameter(
        val name: String,
        val type: String,
    )
}

