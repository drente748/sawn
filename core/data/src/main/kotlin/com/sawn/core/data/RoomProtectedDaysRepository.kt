package com.sawn.core.data

import com.sawn.domain.block.BlockingEngineType
import com.sawn.domain.DayCell
import com.sawn.domain.ProtectionDayPolicy
import com.sawn.domain.repo.ProtectedDaysRepository
import com.sawn.domain.repo.StatsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class RoomProtectedDaysRepository(private val dao: ProtectedDaysDao) : ProtectedDaysRepository {

    override suspend fun upsert(date: LocalDate, source: String) {
        // The legacy label carries no engine information. Keep the row's meaning intact by
        // treating a "VPN" label as the DNS engine and anything else as unknown-but-set.
        val engines = when (source.uppercase()) {
            "VPN", "DNS" -> setOf(BlockingEngineType.DNS)
            else -> setOf(BlockingEngineType.DNS)
        }
        upsert(date, engines)
    }

    override suspend fun upsert(date: LocalDate, engines: Set<BlockingEngineType>) {
        // The insert and the merge must run together: a day that is already there must
        // keep the flags of the engines that protected it earlier.
        dao.record(
            ProtectedDayEntity(
                date = date.toString(),
                source = sourceLabel(engines),
                recordedAt = System.currentTimeMillis(),
                hasDnsProtection = BlockingEngineType.DNS in engines,
                hasBrowserProtection = BlockingEngineType.BROWSER in engines,
                hasAppProtection = BlockingEngineType.APP in engines,
            ),
        )
    }

    override fun observeProtectedDays(): Flow<Set<LocalDate>> =
        dao.observe().map { rows ->
            // A corrupt row must never take every stats collector (and the Home screen)
            // down with it: skip unparseable dates, keep the valid ones.
            rows.mapNotNull { row -> runCatching { LocalDate.parse(row) }.getOrNull() }.toSet()
        }

    override suspend fun allDays(): List<LocalDate> =
        dao.all().mapNotNull { row -> runCatching { LocalDate.parse(row) }.getOrNull() }.sorted()

    /** The label of the engines that protected the day — for display and for backups. */
    private fun sourceLabel(engines: Set<BlockingEngineType>): String = when {
        BlockingEngineType.DNS in engines -> "VPN"
        BlockingEngineType.BROWSER in engines -> "BROWSER"
        BlockingEngineType.APP in engines -> "APP"
        else -> "NONE"
    }
}

/**
 * Derives streak stats live from the protected-day log. Note: `currentStreak`/`isAtRisk`
 * recompute at collection time from `LocalDate.now()` — callers collecting across midnight
 * get the value for the day they collected it.
 */
class RoomStatsRepository(private val days: ProtectedDaysRepository) : StatsRepository {

    override fun currentStreak(): Flow<Int> =
        days.observeProtectedDays().map { ProtectionDayPolicy.currentStreak(it, LocalDate.now()) }

    override fun longestStreak(): Flow<Int> =
        days.observeProtectedDays().map {
            // Cut future dates here too: a corrupted/clock-skewed row must not inflate history.
            ProtectionDayPolicy.longestStreak(it, LocalDate.now())
        }

    override fun isAtRisk(): Flow<Boolean> =
        days.observeProtectedDays().map { ProtectionDayPolicy.isAtRisk(it, LocalDate.now()) }

    override fun totalProtectedDays(): Flow<Int> =
        days.observeProtectedDays().map { ProtectionDayPolicy.totalProtectedDays(it, LocalDate.now()) }

    override fun recentDays(pastDays: Int): Flow<List<DayCell>> =
        days.observeProtectedDays().map { ProtectionDayPolicy.heatmap(it, LocalDate.now(), pastDays) }
}
