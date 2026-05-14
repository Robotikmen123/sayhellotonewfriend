package com.sayhello.circus.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sayhello.circus.CircusApp
import com.sayhello.circus.ai.CharacterAgent
import com.sayhello.circus.ai.ClaudeClient
import com.sayhello.circus.character.CharacterRegistry
import com.sayhello.circus.character.CircusCharacter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatMessage(val role: String, val text: String)

data class ChatUiState(
    val active: CircusCharacter = CharacterRegistry.byId("caine"),
    val messages: List<ChatMessage> = emptyList(),
    val sending: Boolean = false,
    val error: String? = null,
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as CircusApp
    private val prefs = app.preferences

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private val client = ClaudeClient(
        apiKeyProvider = { prefs.apiKey() },
        modelProvider = { prefs.model() },
    )

    init {
        viewModelScope.launch {
            val active = CharacterRegistry.byId(prefs.activeCharacterId())
            _state.update {
                it.copy(
                    active = active,
                    messages = listOf(ChatMessage("assistant", active.openingMonologue)),
                )
            }
        }
    }

    fun switchTo(id: String) {
        viewModelScope.launch {
            prefs.setActiveCharacter(id)
            val active = CharacterRegistry.byId(id)
            _state.update {
                it.copy(
                    active = active,
                    messages = listOf(ChatMessage("assistant", active.openingMonologue)),
                    error = null,
                )
            }
        }
    }

    fun send(text: String) {
        if (text.isBlank() || _state.value.sending) return
        val user = ChatMessage("user", text.trim())
        _state.update { it.copy(messages = it.messages + user, sending = true, error = null) }
        viewModelScope.launch {
            val character = _state.value.active
            val agent = CharacterAgent(client, character)
            val history = _state.value.messages.map { ClaudeClient.Message(it.role, it.text) }
            runCatching {
                agent.reply(history.dropLast(1), user.text)
            }.onSuccess { reply ->
                _state.update {
                    it.copy(
                        messages = it.messages + ChatMessage("assistant", reply),
                        sending = false,
                    )
                }
            }.onFailure { e ->
                _state.update { it.copy(sending = false, error = e.message ?: "Bilinmeyen hata") }
            }
        }
    }
}
