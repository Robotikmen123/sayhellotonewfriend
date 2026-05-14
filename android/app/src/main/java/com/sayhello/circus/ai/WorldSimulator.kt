package com.sayhello.circus.ai

import com.sayhello.circus.character.CharacterRegistry
import com.sayhello.circus.character.UnlockManager
import com.sayhello.circus.data.WorldEventStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext
import kotlin.random.Random

/**
 * Drives the autonomous "what is the cast doing right now" feed. Picks a
 * random unlocked character every interval, asks the agent for a tiny scene
 * beat, and stores it in [WorldEventStore]. Nothing it does leaves the app
 * — it is purely a journal of imagined events.
 */
class WorldSimulator(
    private val clientFactory: () -> ClaudeClient,
    private val unlockManager: UnlockManager,
    private val store: WorldEventStore,
) {
    /**
     * Suspend forever, emitting beats roughly every [minutesBetween] minutes.
     * Cancel the surrounding coroutine to stop.
     */
    suspend fun run(minutesBetween: LongRange = 6L..14L) {
        val client = clientFactory()
        while (coroutineContext.isActive) {
            val cast = unlockManager.unlocked()
            if (cast.isEmpty()) {
                delay(60_000)
                continue
            }
            val character = cast.random()
            val verb = character.autonomousVerbs.random()
            val agent = CharacterAgent(client, character)
            val text = runCatching { agent.autonomousBeat(verb) }.getOrElse { e ->
                "*sahne arkasında tıkırtı*\n\n(${e.message ?: "sessizlik"})"
            }
            store.append(
                WorldEventStore.Event(
                    ts = System.currentTimeMillis(),
                    characterId = character.id,
                    verb = verb,
                    text = text,
                )
            )
            val minutes = Random.nextLong(minutesBetween.first, minutesBetween.last + 1)
            delay(minutes * 60_000)
        }
    }

    /** Force one beat right now from a specific (already unlocked) character. */
    suspend fun nudge(characterId: String) {
        val character = CharacterRegistry.byId(characterId)
        val unlocked = unlockManager.unlocked().any { it.id == characterId }
        if (!unlocked) return
        val client = clientFactory()
        val agent = CharacterAgent(client, character)
        val verb = character.autonomousVerbs.random()
        val text = runCatching { agent.autonomousBeat(verb) }.getOrElse {
            "*sahne kararır*"
        }
        store.append(
            WorldEventStore.Event(
                ts = System.currentTimeMillis(),
                characterId = characterId,
                verb = verb,
                text = text,
            )
        )
    }
}
