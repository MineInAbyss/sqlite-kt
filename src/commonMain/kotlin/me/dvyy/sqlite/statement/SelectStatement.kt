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

    /**
     * Run on each result row
     */
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

    /**
     * Query each result row, building a list by mapping each.
     */
    context(tx: Transaction)
    inline fun <T> map(
        statement: NamedColumnSqliteStatement.(R) -> T,
    ): List<T> = buildList {
        this@SelectStatement.forEach {
            add(statement(context))
        }
    }

    /**
     * Query the first result row, or error if none were returned.
     */
    context(tx: Transaction)
    inline fun <T> first(
        statement: NamedColumnSqliteStatement.(R) -> T,
    ): T = prepare {
        if (step()) statement(context)
        else error("Tried getting first row from empty result set")
    }

    /**
     * Query the first result row, or return null if none were returned.
     */
    context(tx: Transaction)
    inline fun <T> firstOrNull(
        statement: NamedColumnSqliteStatement.(R) -> T,
    ): T? = prepare {
        if (step()) statement(context)
        else null
    }

    /**
     * @return Whether any rows were changed as a result of this statement.
     */
    context(tx: Transaction)
    fun anyChanged(): Boolean {
        prepare { step() }
        return countChanges() != 0
    }

    /**
     * @return How many row changes occurred as a result of this statement.
     */
    context(tx: Transaction)
    fun countChanges(): Int {
        prepare { step() }
        return tx.select("SELECT changes()").first { getInt(0) }
    }
}
