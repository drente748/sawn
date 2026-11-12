package com.sawn.core.data

import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The 4 → 5 migration, checked on the JVM.
 *
 * This is the twin of the instrumented `SawnDatabaseMigrationTest` in `src/androidTest`.
 * The instrumented one is the official check (it validates against the exported Room
 * schema); this one runs on every `./gradlew test`, so a broken migration is caught
 * here rather than on a phone.
 *
 * It drives the *same* [SawnDatabase.MIGRATION_4_5] object over a real SQLite database
 * that starts at the previous schema, so the SQL that ships is the SQL that is tested.
 */
@RunWith(RobolectricTestRunner::class)
class SawnDatabaseMigrationSqlTest {

    /** The `protected_days` table as it was before the migration. */
    private val OLD_SCHEMA = """
        CREATE TABLE IF NOT EXISTS protected_days (
            date TEXT PRIMARY KEY NOT NULL,
            source TEXT NOT NULL,
            recordedAt INTEGER NOT NULL
        )
    """.trimIndent()

    @Test
    fun theMigration_addsTheThreeEngineFlags() {
        val db = openDatabase()
        db.execSQL(OLD_SCHEMA)
        db.execSQL(
            "INSERT INTO protected_days (date, source, recordedAt) VALUES ('2026-01-05', 'VPN', 1000)",
        )

        SawnDatabase.MIGRATION_4_5.migrate(db)

        db.query("SELECT date, source, hasDnsProtection, hasBrowserProtection, hasAppProtection FROM protected_days").use { c ->
            assertTrue("the protected day must survive the migration", c.moveToFirst())
            assertEquals("2026-01-05", c.getString(0))
            assertEquals("VPN", c.getString(1))
            // The DNS filter was the engine back then, so the day still counts as protected.
            assertEquals(1, c.getInt(2))
            assertEquals(0, c.getInt(3))
            assertEquals(0, c.getInt(4))
        }
        db.close()
    }

    @Test
    fun theMigratedDay_isStillCountedAsProtected() {
        val db = openDatabase()
        db.execSQL(OLD_SCHEMA)
        db.execSQL(
            "INSERT INTO protected_days (date, source, recordedAt) VALUES ('2026-01-06', 'VPN', 1000)",
        )

        SawnDatabase.MIGRATION_4_5.migrate(db)

        db.query(
            "SELECT COUNT(*) FROM protected_days " +
                "WHERE hasDnsProtection = 1 OR hasBrowserProtection = 1 OR hasAppProtection = 1",
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1, c.getInt(0))
        }
        db.close()
    }

    @Test
    fun theMigratedSchema_acceptsTheNewFlags() {
        val db = openDatabase()
        db.execSQL(OLD_SCHEMA)

        SawnDatabase.MIGRATION_4_5.migrate(db)

        // The new columns must be writable — this is what the app does every day.
        db.execSQL(
            "INSERT INTO protected_days " +
                "(date, source, recordedAt, hasDnsProtection, hasBrowserProtection, hasAppProtection) " +
                "VALUES ('2026-01-07', 'BROWSER', 2000, 0, 1, 0)",
        )
        db.query("SELECT hasBrowserProtection FROM protected_days WHERE date = '2026-01-07'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1, c.getInt(0))
        }
        db.close()
    }

    @Test
    fun theMigration_leavesTheOtherTablesAlone() {
        val db = openDatabase()
        db.execSQL(OLD_SCHEMA)
        db.execSQL("CREATE TABLE IF NOT EXISTS blocked_items (id INTEGER PRIMARY KEY, type TEXT NOT NULL)")
        db.execSQL("INSERT INTO blocked_items (id, type) VALUES (7, 'WEBSITE')")

        SawnDatabase.MIGRATION_4_5.migrate(db)

        db.query("SELECT type FROM blocked_items WHERE id = 7").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("WEBSITE", c.getString(0))
        }
        db.close()
    }

    @Test
    fun theMigration_leavesTheExistingRowsAlone() {
        // The migration is additive: a row that was there before must keep its values,
        // and the day must still count as protected.
        val db = openDatabase()
        db.execSQL(OLD_SCHEMA)
        db.execSQL(
            "INSERT INTO protected_days (date, source, recordedAt) VALUES ('2026-01-09', 'FOCUS', 3000)",
        )

        SawnDatabase.MIGRATION_4_5.migrate(db)

        db.query("SELECT source, recordedAt, hasDnsProtection FROM protected_days").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("FOCUS", c.getString(0))
            assertEquals(3000, c.getInt(1))
            assertEquals(1, c.getInt(2))
        }
        db.close()
    }

    /** A fresh, empty SQLite database the migration can run against. */
    private fun openDatabase(): SupportSQLiteDatabase {
        val callback = object : SupportSQLiteOpenHelper.Callback(4) {
            override fun onCreate(db: SupportSQLiteDatabase) = Unit
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        val config = SupportSQLiteOpenHelper.Configuration.builder(
            ApplicationProvider.getApplicationContext(),
        )
            .name(null)
            .callback(callback)
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase
    }
}
