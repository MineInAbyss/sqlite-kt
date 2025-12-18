package me.dvyy.sqlite

import androidx.sqlite.SQLiteConnection
import me.dvyy.sqlite.statement.NamedColumnSqliteStatement
import org.intellij.lang.annotations.Language
import kotlin.coroutines.RestrictsSuspension

@RestrictsSuspension
class WriteTransaction(
    connection: SQLiteConnection,
    identity: Identity,
) : Transaction(connection, identity) {
    fun <T, R> insert(
        @Language("SQLite") sql: String,
        context: T,
        vararg parameters: Any,
        returning: NamedColumnSqliteStatement.(T) -> R,
    ) = select(sql, *parameters).firstOrNull { returning(this, context) }
}
