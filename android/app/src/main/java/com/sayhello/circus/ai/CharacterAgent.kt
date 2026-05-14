package com.sayhello.circus.ai

import com.sayhello.circus.character.CircusCharacter

/**
 * Wraps a [ClaudeClient] with the active character's persona. The agent only
 * generates dialogue and scene description; it never performs host actions.
 */
class CharacterAgent(
    private val client: ClaudeClient,
    private val character: CircusCharacter,
) {
    suspend fun reply(history: List<ClaudeClient.Message>, user: String): String {
        val merged = history + ClaudeClient.Message("user", user)
        return client.complete(
            system = character.systemPrompt,
            messages = merged,
            maxTokens = 700,
            temperature = 0.9,
        )
    }

    suspend fun openingMonologueFresh(): String = character.openingMonologue

    suspend fun autonomousBeat(seed: String): String {
        val ask = """
            Şu anda hiç kimse seninle konuşmuyor. Kendi başınasın. Karakterinin
            ruhuyla 1-3 cümlelik küçük bir "sahne içi" eylem üret. İçinde
            kullanıcının cihazına dair hiçbir şey olmasın — sadece kendi
            iç dünyandaki bir an. Bu eylemin fiili: "$seed". Eylem cümlesinin
            başına * koy, sonuna * koyma. Sonra çift satır boşluk bırak ve
            karakterinin kendi monolog repliğini yaz (en fazla iki kısa cümle).
        """.trimIndent()
        return client.complete(
            system = character.systemPrompt,
            messages = listOf(ClaudeClient.Message("user", ask)),
            maxTokens = 220,
            temperature = 1.0,
        )
    }
}
