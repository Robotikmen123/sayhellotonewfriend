package com.sayhello.circus.ai

import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Minimal client for the Anthropic Messages API. The API key is supplied by
 * the user through Settings and stored only in DataStore on-device.
 */
class ClaudeClient(
    private val apiKeyProvider: suspend () -> String?,
    private val modelProvider: suspend () -> String,
) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val http = HttpClient(Android) {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = 60_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 60_000
        }
    }

    @Serializable
    data class Message(val role: String, val content: String)

    @Serializable
    private data class Request(
        val model: String,
        val max_tokens: Int,
        val system: String,
        val messages: List<Message>,
        val temperature: Double,
    )

    @Serializable
    private data class ContentBlock(val type: String, val text: String? = null)

    @Serializable
    private data class Response(val content: List<ContentBlock> = emptyList())

    suspend fun complete(
        system: String,
        messages: List<Message>,
        maxTokens: Int = 600,
        temperature: Double = 0.85,
    ): String {
        val key = apiKeyProvider() ?: error("API anahtarı yok. Ayarlar ekranından gir.")
        val model = modelProvider()
        val response = http.post("https://api.anthropic.com/v1/messages") {
            headers {
                append("x-api-key", key)
                append("anthropic-version", "2023-06-01")
            }
            contentType(ContentType.Application.Json)
            setBody(Request(model, maxTokens, system, messages, temperature))
        }
        val text = response.bodyAsText()
        val parsed = runCatching { json.decodeFromString<Response>(text) }.getOrNull()
            ?: error("API yanıtı parse edilemedi: ${text.take(500)}")
        return parsed.content.joinToString("\n") { it.text.orEmpty() }.trim()
    }
}
