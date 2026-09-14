package it.krpng.cassa.data.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object CassaMigrations {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                ALTER TABLE numbering_state
                ADD COLUMN randomSeedInitialized INTEGER NOT NULL DEFAULT 0
                """.trimIndent(),
            )
        }
    }
}
