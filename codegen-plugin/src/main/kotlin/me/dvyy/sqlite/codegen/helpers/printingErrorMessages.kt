package me.dvyy.sqlite.codegen.helpers

import androidx.sqlite.SQLiteException
import org.gradle.api.GradleException

internal inline fun printingErrorMessages(
    query: String,
    errorMessage: String,
    block: () -> Unit,
) {
    try {
        block()
    } catch (e: Exception) {
        if (e is SQLiteException)
            throw GradleException(
                """$errorMessage
╔══ Error
${e.message?.prependIndent("║ ")}
╠══ Query
${query.prependIndent("║ ")}
╚══
"""
            )
        else throw e
    }
}
