package com.mineinabyss.sqlite.codegen

import androidx.sqlite.SQLiteException
import net.sf.jsqlparser.JSQLParserException
import org.gradle.api.GradleException

internal inline fun printingErrorMessages(
    query: String,
    errorMessage: String,
    block: () -> Unit,
) {
    try {
        block()
    } catch (e: Exception) {
        if (e is SQLiteException || e is JSQLParserException)
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