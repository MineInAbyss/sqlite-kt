# sqlite-kt

A simple wrapper around [androidx.sqlite](https://developer.android.com/kotlin/multiplatform/sqlite) for Kotlin
Multiplatform. This library provides some good-to-have features like a simple connection pool built for
kotlinx.coroutines, but keeps low-level access to let you leverage sqlite's low query latency. We aim to keep the
library simple enough to document fully in this file.

**NOTE: This library is a WIP for my own projects, expect breaking changes!**

## Features
- Automatic setup of WAL for performance, with a connection pool that has one write connection and many read connections.
- Uses Kotlin's experimental context-parameters introduced in `2.2.0` to jump between suspending and database world nicely.
  - Suspending calls are only required once to get access to a read/write connection, the rest of db logic just uses the connection as a context parameter
- TODO: Observe changes as flows (by listening to table updates)
- TODO: Potential to support new targets as androidx.sqlite multiplatform drivers mature

# Docs: sqlite-kt

## Setup (TODO)

### Gradle (TODO)

### Entrypoint

Create a database using the `Database` constructor, we also provide helpers to create a temporary database (preferred
for tests), or an in-memory database (which is limited to a single connection due to SQLite internals).

```kotlin
val db = Database("path/to/database.db") {
    // Optional init write transaction
}
Database.temporary { ... }
Database.inMemorySingleConnection()
```

## Connection pool

The `Database` class manages a pool of a configurable number of read connections and one write connection.
In the configured WAL mode, multiple reads can run in parallel, including while a write transaction is running.
SQLite handles this perfectly fine, however note that long-running writes will block other writes due to how SQLite's
WAL mode works (being limited to a single write connection.)

## Running queries

To run queries, use `db.read { ... }` or `db.write { ... }`.
These functions suspend, awaiting the correct thread associated with each connection in the pool, and begin a new
transaction on said connection, represented by a `Transaction` or `WriteTransaction` object respectively.

### Low-level queries

For low-level helper functions (ex. a DAO), prefer
using [context parameters](https://kotlinlang.org/docs/context-parameters.html), which currently need to be enabled via
a compiler argument.

We provide an optional gradle plugin to generate this style of query with bindings from `.sql` files,
but you may always drop down to writing them by hand.

```kotlin
context(tx: Transaction)
fun getAllIds(): List<Int> = tx.select("SELECT id FROM my_table") { getInt(0) }
```

This allows you to call functions from other functions using the same `context` receiver:

```kotlin
context(tx: Transaction)
fun getFirstFewIds(): List<Int> {
    return getAllIds().sorted().take(5)
}
```

You are responsible for requesting a `WriteTransaction` in the context for queries that modify the database,
writing in a read-only transaction will throw a runtime error.

You may run multiple read or write statements inside one function, SQLite encourages such use cases thanks to low query
latency.
This library is designed to avoid swapping threads for each transaction (notice that none of the examples above are
marked `suspend`, no thread swapping occurs after you have received a transaction via `db.read/write`.)

Note that because of this, you should NOT perform any thread swaps of your own inside transactions, this blocks other
transactions and makes you lose guarantees about database state.

#### Binding data (TODO)

#### Reading back data (TODO)

#### Transaction helpers (TODO)

- `prepare`
- `exec`
- `select`
- `insert`
- `delete`

### Exposing data

Queries that return data to the outside world or whose database modifications make sense to run in the same transaction
should suspend. How exactly you split these layers depends on your app architecture, in Android this might be Repository
and DataSource classes:

```kotlin
class SomeRepository(
    val db: Database,
    val myDataSource: MyDataSource,
    val names: Map<Int, String>
) {
    suspend fun getNamesOfFirstFew(): List<String> = db.read {
        myDataSource.getFirstFewIds()
    }.map { names[it] }
}
```

As a general rule, keep in mind that you should not have long-running write queries due to SQLite's blocking writes.
Moreover, you should not cause side effects (i.e. modify outside app state) inside transactions as this loses
transaction
guarantees provided by SQLite (ex. a rollback after a device crash.) thus, once you need to store data somewhere else
in your app, it's a good time to wrap everything with a `db.read`, returning the data you need at the end of it.

### Reactive queries (TODO)

- `db.watch`

### Extras (TODO)

#### Identity (TODO)

#### Closing (TODO)

# Docs: sqlite-kt-codegen

The codegen module is a Gradle plugin that generates low-level bindings from `.sql` files and validates them at compile
time.
It also helps generate a database file from a `Schema.sql` definition which can be attached to in IntelliJ for getting
syntax highlighting on your queries.

## Setup (TODO)

### Gradle (TODO)

## Creating the database schema (TODO)

## Writing queries (TODO)