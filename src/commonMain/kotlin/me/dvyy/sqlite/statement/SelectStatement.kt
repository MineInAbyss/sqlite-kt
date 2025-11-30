package me.dvyy.sqlite.statement

import me.dvyy.sqlite.Transaction

class SelectStatement<R>(
    val sql: String,
    val parameters: Array<out Any>,
    val context: R,
) {
    context(tx: Transaction)
    inline fun <T> prepare(block: NamedColumnSqliteStatement.(R) -> T): T = tx.prepare(sql) {
        bindParams(*parameters)
        block(context)
    }

    context(tx: Transaction)
    inline fun forEach(
        statement: NamedColumnSqliteStatement.(R) -> Unit,
    ) {
        prepare {
            while (step()) {
                statement(context)
            }
        }
    }

    context(tx: Transaction)
    inline fun <T> map(
        statement: NamedColumnSqliteStatement.(R) -> T,
    ): List<T> = buildList {
        this@SelectStatement.forEach {
            add(statement(context))
        }
    }

    context(tx: Transaction)
    inline fun <T> first(
        statement: NamedColumnSqliteStatement.(R) -> T,
    ): T = prepare {
        if (step()) statement(context)
        else error("Tried getting first row from empty result set")
    }

    context(tx: Transaction)
    inline fun <T> firstOrNull(
        statement: NamedColumnSqliteStatement.() -> T,
    ): T? = prepare {
        if (step()) statement()
        else null
    }
}
