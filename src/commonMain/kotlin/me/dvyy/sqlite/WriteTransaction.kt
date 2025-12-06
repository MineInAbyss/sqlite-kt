package me.dvyy.sqlite

import androidx.sqlite.SQLiteConnection
import org.intellij.lang.annotations.Language
import kotlin.coroutines.RestrictsSuspension

@RestrictsSuspension
class WriteTransaction(
    connection: SQLiteConnection,
    identity: Identity,
) : Transaction(connection, identity) {
    fun insert(
        @Language("SQLite") sql: String,
        vararg parameters: Any,
    ): Long {
        exec(sql, *parameters)
        return select("SELECT last_insert_rowid()").first { getLong(0) }
    }
}
