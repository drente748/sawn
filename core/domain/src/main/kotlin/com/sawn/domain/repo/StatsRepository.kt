package com.sawn.domain.repo

import kotlinx.coroutines.flow.Flow

/** Derives the streak stats the UI shows, from the protection-day log. */
interface StatsRepository {
    fun currentStreak(): Flow<Int>
    fun longestStreak(): Flow<Int>
    fun isAtRisk(): Flow<Boolean>
}
