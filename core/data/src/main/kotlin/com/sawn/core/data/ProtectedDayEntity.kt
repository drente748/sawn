package com.sawn.core.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * One row per protected calendar day (ISO yyyy-MM-dd). Presence = protected; absence = missed.
 * The table is tiny (days in a decade ≤ 3 650 rows) so reads stay trivial.
 *
 * The three flags record **which engines were enforcing** the protection that day. A day
 * counts as protected when at least one of them is on, so the streak follows the engines
 * that were actually running: a day protected only by the browser engine still counts, and
 * a day protected only by the DNS filter still counts.
 *
 * The SQL defaults matter: they are what lets a row be inserted without naming every
 * engine (a restore from a backup, for one), and they are what the migration adds.
 *
 * @property source a human-readable label of the main engine ("VPN", "BROWSER", …). Kept
 * for the backup format and for display; the flags are the source of truth.
 */
@Entity(tableName = "protected_days")
data class ProtectedDayEntity(
    @PrimaryKey val date: String,
    val source: String,
    val recordedAt: Long,
    @ColumnInfo(defaultValue = "0") val hasDnsProtection: Boolean = false,
    @ColumnInfo(defaultValue = "0") val hasBrowserProtection: Boolean = false,
    @ColumnInfo(defaultValue = "0") val hasAppProtection: Boolean = false,
) {
    /** True when at least one engine was enforcing the protection on this day. */
    val isProtected: Boolean
        get() = hasDnsProtection || hasBrowserProtection || hasAppProtection

    companion object {
        fun of(date: LocalDate, source: String): ProtectedDayEntity =
            ProtectedDayEntity(date = date.toString(), source = source, recordedAt = System.currentTimeMillis())
    }
}
