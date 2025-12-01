package me.dvyy.sqlite.observers

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.merge

class DatabaseObservers {
    val tableObservers = mutableMapOf<Int, MutableSharedFlow<Unit>>()

    fun notify(tables: Collection<Int>) {
        tables.forEach { table -> tableObservers[table]?.tryEmit(Unit) }
    }

    fun forTable(table: Int): Flow<Unit> {
        return tableObservers.getOrPut(table) {
            MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)
        }
    }

    fun forTables(tables: Set<Int>): Flow<Unit> {
        return merge(*tables.map { forTable(it) }.toTypedArray())
    }
}
