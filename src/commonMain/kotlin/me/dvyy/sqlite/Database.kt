package me.dvyy.sqlite

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.*
import androidx.sqlite.execSQL
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.channels.consume
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import me.dvyy.sqlite.connection.PrepareCachingSQLiteConnection
import me.dvyy.sqlite.internal.throttle
import me.dvyy.sqlite.internal.transaction
import me.dvyy.sqlite.observers.DatabaseObservers
import kotlin.io.path.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * A simple sqlite database connection pool in WAL mode.
 * Backed by a single write connection and fixed thread pool of read connections.
 *
 * @property parentScope If provided, closes all database connections and cancels running jobs when this scope cancels its children.
 */
open class Database(
    private val path: String,
    private val driver: BundledSQLiteDriver = BundledSQLiteDriver(),
    val readConnections: Int = 4,
    val defaultIdentity: Identity? = -1,
    val watchQueryThrottle: Duration = 100.milliseconds,
    val parentScope: CoroutineScope? = null,
    val onClose: () -> Unit = {},
    val useWAL: Boolean = true,
    init: WriteTransaction.() -> Unit = {},
) : AutoCloseable {
    private val trackedTables = mutableListOf<String>()

    @PublishedApi
    internal val tableIndices = mutableMapOf<String, Int>()

    @PublishedApi
    internal val writeConnection = createConnection(readOnly = false).apply {
        val tx = WriteTransaction(this, defaultIdentity ?: -1)
        transaction {
            tx.exec(
                """
                CREATE TEMP TABLE skt_table_modification_log (
                    table_id INTEGER PRIMARY KEY,
                    invalidated INTEGER DEFAULT 1
                );
            """.trimIndent()
            )
            init(tx)
        }
    }

    suspend fun trackChangesOn(table: String) {
        write {
            if (table in trackedTables) return@write
            trackedTables.add(table)
            val id = trackedTables.lastIndex
            tableIndices[table] = id
            exec(
                """
                CREATE TEMP TRIGGER IF NOT EXISTS [skt_reactive_insert_$table]
                AFTER INSERT ON $table
                BEGIN
                    INSERT OR IGNORE INTO skt_table_modification_log VALUES ($id, 1);
                END;
            """.trimIndent()
            )
            exec(
                """
                CREATE TEMP TRIGGER IF NOT EXISTS [skt_reactive_update_$table]
                AFTER UPDATE ON $table
                BEGIN
                    INSERT OR IGNORE INTO skt_table_modification_log VALUES ($id, 1);
                END;
            """.trimIndent()
            )
            exec(
                """
                CREATE TEMP TRIGGER IF NOT EXISTS [skt_reactive_delete_$table]
                AFTER DELETE ON $table
                BEGIN
                    INSERT OR IGNORE INTO skt_table_modification_log VALUES ($id, 1);
                END;
            """.trimIndent()
            )
        }
    }

    @PublishedApi
    internal val dbWriteDispatcher = newSingleThreadContext("db-writes")

    @PublishedApi
    internal val dbReadDispatcher =
        if (readConnections == 0) dbWriteDispatcher
        else newFixedThreadPoolContext(readConnections, "db-reads")

    @PublishedApi
    internal val createdReadConnections = Channel<SQLiteConnection>(capacity = UNLIMITED)

    @PublishedApi
    internal val observers = DatabaseObservers()

    private var isClosed = false

    @PublishedApi
    internal val writeScope =
        CoroutineScope((parentScope?.coroutineContext ?: Dispatchers.Default) + dbWriteDispatcher + SupervisorJob())

    // TODO use multiplatform ThreadLocal like koin does (uses Stately library outside JVM)
    //  https://github.com/InsertKoinIO/koin/blob/main/projects/core/koin-core/build.gradle.kts
    @PublishedApi
    internal val threadLocalReadOnlyConnection: ThreadLocal<SQLiteConnection> =
        ThreadLocal.withInitial { createConnection(readOnly = true) }

    init {
        // Close the database when the parent scope is cancelled
        parentScope?.launch {
            try {
                awaitCancellation()
            } finally {
                this@Database.close()
            }
        }
    }

    /**
     * Creates a new connection in WAL mode, specifying [SQLITE_OPEN_READONLY] flag if [readOnly].
     *
     * This connection opens with [SQLITE_OPEN_NOMUTEX], so ensure only a single thread can
     * prepare and run statements with it at a time.
     */
    @PublishedApi
    internal fun createConnection(readOnly: Boolean): SQLiteConnection {
        val readFlag = if (readOnly) SQLITE_OPEN_READONLY else (SQLITE_OPEN_READWRITE or SQLITE_OPEN_CREATE)
        return driver.open(
            path, readFlag or SQLITE_OPEN_NOMUTEX
        ).also {
            it.execSQL(
                buildString {
                    if (useWAL) appendLine("PRAGMA journal_mode=WAL;")
                    appendLine("PRAGMA synchronous=normal;")
                    appendLine("PRAGMA journal_size_limit=6144000;")
                }
            )
            if (readOnly) createdReadConnections.trySend(it)
        }.let { PrepareCachingSQLiteConnection(it) }
    }

    /**
     * Gets or creates a read-only connection for this thread.
     * User must ensure not to pass it to other threads.
     */
    fun getOrCreateReadConnectionForCurrentThread(): SQLiteConnection {
        return threadLocalReadOnlyConnection.get()
    }

    // TODO need SupervisorJob? Check this is safe with parallel writes
    /** Run a write inside a transaction on the single database write connection. */
    suspend inline fun <T> write(
        identity: Identity = defaultIdentity ?: error("Identity must be specified when writing"),
        crossinline block: WriteTransaction.() -> T,
    ): T = withContext(dbWriteDispatcher) {
        val tx = WriteTransaction(writeConnection, identity)
        val (result, modified) = writeConnection.transaction {
            tx.block() to with(tx) {
                val invalidated =
                    select("SELECT table_id FROM skt_table_modification_log WHERE invalidated = 1;").map { getInt(0) }
                if (invalidated.isNotEmpty()) exec("DELETE FROM skt_table_modification_log;")
                invalidated
            }
        }
        if (modified.isNotEmpty()) observers.notify(modified)
        result
    }

    /** Run a database read on read thread pool. */
    suspend inline fun <T> read(
        identity: Identity = defaultIdentity ?: error("Identity must be specified when writing"),
        crossinline block: Transaction.() -> T,
    ): T = withContext(dbReadDispatcher) {
        val conn = if (readConnections == 0) writeConnection
        else threadLocalReadOnlyConnection.get()
        Transaction(conn, identity).block()
    }

    inline fun <T> launchWrite(
        identity: Identity = defaultIdentity ?: error("Identity must be specified when writing"),
        crossinline block: WriteTransaction.() -> T,
    ): Job = writeScope.launch {
        write(identity, block)
    }

    /** Watches tables associated with a query for changes (this API is not complete yet.) */
    inline fun <T> watch(
        vararg tables: String,
        crossinline read: Transaction.() -> T,
    ): Flow<T> = flow {
        tables.forEach { trackChangesOn(it) }
        val tableIds = tables.map { tableIndices[it] ?: error("Table $it is not being tracked") }.toSet()
        val tableFlow = observers.forTables(tableIds)
        emit(read { read() })
        tableFlow.throttle(watchQueryThrottle).collect {
            emit(read { read() })
        }
    }

    /** Closes all read/write dispatchers, and their connections. */
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun close() {
        if (isClosed) return

        // Close dispatchers
        dbWriteDispatcher.close()
        dbReadDispatcher.close()
        writeScope.cancel()

        // Close read and write sqlite connections
        writeConnection.close()
        createdReadConnections.consume {
            while (!isEmpty) {
                tryReceive().getOrNull()?.close()
            }
        }
        onClose()
        isClosed = true
    }

    companion object {
        /**
         * Creates a new temporary database which will be deleted once the write connection closes.
         */
        @OptIn(ExperimentalPathApi::class)
        fun temporary(
            readConnections: Int = 4,
            init: WriteTransaction.() -> Unit = {},
        ): Database {
            val tempDir = createTempDirectory("sqlite_kt")
            val temporaryPath = tempDir / "test.db"
            return Database(
                temporaryPath.absolutePathString(),
                readConnections = readConnections,
                onClose = { tempDir.deleteRecursively() },
                init = init
            )
        }

        /**
         * Creates an in-memory database with a single read/write connection.
         */
        fun inMemorySingleConnection(): Database = Database(":memory:", readConnections = 0)
    }
}
