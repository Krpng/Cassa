package it.krpng.cassa.data.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CassaDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        CassaDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    @Throws(IOException::class)
    fun migrate1To2_preservesNumberingStateDefaultsInitializedFalseAndProductRow() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                """
                INSERT INTO products
                (name, normalizedName, printedName, category, priceCents,
                 automaticExtrasPricing, active, createdAt, updatedAt)
                VALUES
                ('Margherita', 'margherita', NULL, 'PIZZA', 700, 1, 1, 100, 100)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO numbering_state
                (businessDate, nextSequentialNumber, randomCycle, randomSeed,
                 randomPosition, updatedAt)
                VALUES
                ('2026-09-14', 5, 2, 42, 10, 1000)
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DB,
            2,
            true,
            CassaMigrations.MIGRATION_1_2,
        ).use { db ->
            db.query(
                """
                SELECT nextSequentialNumber, randomCycle, randomSeed, randomPosition,
                       randomSeedInitialized, updatedAt
                FROM numbering_state
                WHERE businessDate = '2026-09-14'
                """.trimIndent(),
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(5L, cursor.getLong(0))
                assertEquals(2, cursor.getInt(1))
                assertEquals(42L, cursor.getLong(2))
                assertEquals(10, cursor.getInt(3))
                assertEquals(0, cursor.getInt(4))
                assertEquals(1000L, cursor.getLong(5))
            }

            db.query(
                """
                SELECT name, priceCents
                FROM products
                WHERE normalizedName = 'margherita'
                """.trimIndent(),
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Margherita", cursor.getString(0))
                assertEquals(700L, cursor.getLong(1))
            }
        }
    }

    private companion object {
        const val TEST_DB = "cassa-migration-1-2-test"
    }
}
