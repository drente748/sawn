package com.sawn.domain.repo

import com.sawn.domain.block.BlockingEngineType
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** Recorded days where protection was effectively active — the single source of truth for streaks. */
interface ProtectedDaysRepository {
    /** Idempotent — safe to call repeatedly for the same day. */
    suspend fun upsert(date: LocalDate, source: String)

    /**
     * Idempotent — safe to call repeatedly for the same day.
     *
     * [engines] records *which* engines were enforcing the protection that day. The day
     * counts as protected when at least one engine was active, so the streak follows the
     * engines that were actually running rather than a single hard-coded source.
     */
    suspend fun upsert(date: LocalDate, engines: Set<BlockingEngineType>)

    /** Live stream of all recorded protection days. */
    fun observeProtectedDays(): Flow<Set<LocalDate>>

    suspend fun allDays(): List<LocalDate>
}
