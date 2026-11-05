package com.sawn.core.data

import com.sawn.domain.ProtectionDayPolicy
import com.sawn.domain.repo.ProtectedDaysRepository
import com.sawn.domain.repo.StatsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class RoomProtectedDaysRepository(private val dao: ProtectedDaysDao) : ProtectedDaysRepository {

    override suspend fun upsert(date: LocalDate, source: String) {
        dao.upsert(ProtectedDayEntity.of(date, source))
    }

    override fun observeProtectedDays(): Flow<Set<LocalDate>> =
        dao.observe().map { rows -> rows.map(LocalDate::parse).toSet() }

    override suspend fun allDays(): List<LocalDate> =
        dao.all().map(LocalDate::parse).sorted()
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
        days.observeProtectedDays().map(ProtectionDayPolicy::longestStreak)

    override fun isAtRisk(): Flow<Boolean> =
        days.observeProtectedDays().map { ProtectionDayPolicy.isAtRisk(it, LocalDate.now()) }
}
