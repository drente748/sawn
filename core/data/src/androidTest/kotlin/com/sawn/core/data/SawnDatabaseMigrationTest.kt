package com.sawn.core.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/**
 * The migration from the previous schema to the current one, against the **real**
 * exported schema.
 *
 * This is the instrumented test (it needs a device): `./gradlew connectedAndroidTest`.
 * The JVM twin in `src/test` runs the same migration on every `./gradlew test`, so a
 * broken migration is caught before it ever reaches a phone.
 *
 * The schema files are the ones Room exported (`core/data/schemas`), copied into the
 * test assets by the build, so this test checks the *real* schema — the same file the
 * shipped build uses — and not a hand-written copy that could drift away from it.
 */
@RunWith(AndroidJUnit4::class)
class SawnDatabaseMigrationTest {

    private val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        SawnDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migratesFrom4To5_addingTheEngineFlags() {
        val db = helper.createDatabase(TEST_DB, 4)
        // A day protected before the migration — the DNS filter was the only engine.
        db.execSQL(
            "INSERT INTO protected_days (date, source, recordedAt) VALUES ('2026-01-05', 'VPN', 1000)",
        )
        db.close()

        val migrated = helper.runMigrationsAndValidate(
            TEST_DB,
            5,
            true,
            SawnDatabase.MIGRATION_4_5,
        )

        migrated.query("SELECT date, source, hasDnsProtection, hasBrowserProtection, hasAppProtection FROM protected_days").use { c ->
            assertTrue("the protected day must survive the migration", c.moveToFirst())
            assertEquals("2026-01-05", c.getString(0))
            assertEquals("VPN", c.getString(1))
            // The DNS filter was the engine back then, so the day still counts as protected.
            assertEquals(1, c.getInt(2))
            assertEquals(0, c.getInt(3))
            assertEquals(0, c.getInt(4))
        }
        migrated.close()
    }

    @Test
    fun migratedDay_isStillCountedAsProtected() {
        val db = helper.createDatabase(TEST_DB, 4)
        db.execSQL(
            "INSERT INTO protected_days (date, source, recordedAt) VALUES ('2026-01-06', 'VPN', 1000)",
        )
        db.close()

        val migrated = helper.runMigrationsAndValidate(
            TEST_DB,
            5,
            true,
            SawnDatabase.MIGRATION_4_5,
        )
        migrated.query("SELECT COUNT(*) FROM protected_days WHERE hasDnsProtection = 1 OR hasBrowserProtection = 1 OR hasAppProtection = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1, c.getInt(0))
        }
        migrated.close()
    }

    @Test
    fun migratedSchema_acceptsTheNewFlags() {
        val db = helper.createDatabase(TEST_DB, 4)
        db.close()

        val migrated = helper.runMigrationsAndValidate(
            TEST_DB,
            5,
            true,
            SawnDatabase.MIGRATION_4_5,
        )
        // The new columns must be writable — this is what the app does every day.
        migrated.execSQL(
            "INSERT INTO protected_days " +
                "(date, source, recordedAt, hasDnsProtection, hasBrowserProtection, hasAppProtection) " +
                "VALUES ('2026-01-07', 'BROWSER', 2000, 0, 1, 0)",
        )
        migrated.query("SELECT hasBrowserProtection FROM protected_days WHERE date = '2026-01-07'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1, c.getInt(0))
        }
        migrated.close()
    }

    @Test
    fun migrationsFromTheOlderSchemas_stillApply() {
        // The whole chain must work: a user who has not opened the app for several
        // versions must land on the current schema with their data intact.
        val db = helper.createDatabase(TEST_DB, 3)
        db.execSQL(
            "INSERT INTO protected_days (date, source, recordedAt) VALUES ('2026-01-08', 'VPN', 1000)",
        )
        db.close()

        val migrated = helper.runMigrationsAndValidate(
            TEST_DB,
            5,
            true,
            SawnDatabase.MIGRATION_3_4,
            SawnDatabase.MIGRATION_4_5,
        )
        migrated.query("SELECT date FROM protected_days").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("2026-01-08", c.getString(0))
        }
        migrated.close()
    }

    companion object {
        private const val TEST_DB = "migration-test"
    }
}
