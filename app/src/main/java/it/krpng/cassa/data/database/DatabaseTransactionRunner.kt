package it.krpng.cassa.data.database

import androidx.room.withTransaction

interface DatabaseTransactionRunner {
    suspend fun <T> runInTransaction(block: suspend () -> T): T
}

class RoomDatabaseTransactionRunner(
    private val database: CassaDatabase,
) : DatabaseTransactionRunner {
    override suspend fun <T> runInTransaction(block: suspend () -> T): T =
        database.withTransaction(block)
}

internal object ImmediateDatabaseTransactionRunner : DatabaseTransactionRunner {
    override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
}
