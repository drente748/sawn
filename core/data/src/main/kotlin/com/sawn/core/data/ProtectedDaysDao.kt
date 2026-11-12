package com.sawn.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ProtectedDaysDao {

    /**
     * Inserts the day when it is not there yet. A day that already exists is left alone,
     * so the engines that already enforced the protection are not lost.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(day: ProtectedDayEntity)

    /**
     * Merges the engines into the day: a flag that is already on stays on. A day protected
     * first by the DNS filter and later by the browser engine keeps both.
     */
    @Query(
        """
        UPDATE protected_days SET
            hasDnsProtection = (hasDnsProtection | :dns),
            hasBrowserProtection = (hasBrowserProtection | :browser),
            hasAppProtection = (hasAppProtection | :app)
        WHERE date = :date
        """,
    )
    suspend fun mergeEngines(date: String, dns: Boolean, browser: Boolean, app: Boolean)

    /** Only days where at least one engine was enforcing the protection. */
    @Query(
        """
        SELECT date FROM protected_days
        WHERE hasDnsProtection = 1 OR hasBrowserProtection = 1 OR hasAppProtection = 1
        """,
    )
    suspend fun all(): List<String>

    /** Only days where at least one engine was enforcing the protection. */
    @Query(
        """
        SELECT date FROM protected_days
        WHERE hasDnsProtection = 1 OR hasBrowserProtection = 1 OR hasAppProtection = 1
        """,
    )
    fun observe(): Flow<List<String>>

    /**
     * Records a protected day and merges its engines into the row that may already exist.
     *
     * The two steps must run together: a day that is already there must keep the flags
     * of the engines that protected it earlier.
     */
    @Transaction
    suspend fun record(day: ProtectedDayEntity) {
        insertIfAbsent(day)
        mergeEngines(
            date = day.date,
            dns = day.hasDnsProtection,
            browser = day.hasBrowserProtection,
            app = day.hasAppProtection,
        )
    }
}
