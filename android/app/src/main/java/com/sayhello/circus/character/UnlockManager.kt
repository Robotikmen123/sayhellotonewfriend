package com.sayhello.circus.character

import com.sayhello.circus.data.CircusPreferences
import java.util.concurrent.TimeUnit

/**
 * Calculates which performers are unlocked, based on the first-launch timestamp.
 * A new character unlocks once per 7-day window. Reset via preferences.
 */
class UnlockManager(private val preferences: CircusPreferences) {

    private val weekMs: Long = TimeUnit.DAYS.toMillis(7)

    suspend fun currentWeek(now: Long = System.currentTimeMillis()): Int {
        val start = preferences.firstLaunchAt() ?: now.also { preferences.setFirstLaunchAt(it) }
        val diff = (now - start).coerceAtLeast(0L)
        val week = (diff / weekMs).toInt()
        return week.coerceAtMost(CharacterRegistry.maxWeek())
    }

    suspend fun unlocked(now: Long = System.currentTimeMillis()): List<CircusCharacter> {
        val week = currentWeek(now)
        return CharacterRegistry.all.filter { it.unlockWeek <= week }
    }

    suspend fun nextUnlockAt(now: Long = System.currentTimeMillis()): Pair<CircusCharacter, Long>? {
        val start = preferences.firstLaunchAt() ?: return null
        val week = currentWeek(now)
        val next = CharacterRegistry.all.firstOrNull { it.unlockWeek > week } ?: return null
        val unlockTime = start + next.unlockWeek * weekMs
        return next to unlockTime
    }
}
