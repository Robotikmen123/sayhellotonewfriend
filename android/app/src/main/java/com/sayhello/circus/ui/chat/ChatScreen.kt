package com.sayhello.circus.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun ChatScreen(vm: ChatViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Surface(color = state.active.accent.copy(alpha = 0.18f)) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(state.active.displayName, style = MaterialTheme.typography.titleLarge)
                Text(state.active.tagline, style = MaterialTheme.typography.bodyMedium)
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
        ) {
            items(state.messages) { msg ->
                Bubble(message = msg, accent = state.active.accent)
            }
            if (state.sending) {
                item {
                    Bubble(
                        message = ChatMessage("assistant", "..."),
                        accent = state.active.accent,
                    )
                }
            }
            state.error?.let {
                item {
                    Text(
                        text = "Hata: $it",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Sahneye bir replik at...") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    vm.send(draft); draft = ""
                }),
                singleLine = false,
                maxLines = 4,
            )
            Button(
                onClick = { vm.send(draft); draft = "" },
                enabled = draft.isNotBlank() && !state.sending,
                modifier = Modifier.padding(start = 8.dp),
            ) { Text("Gönder") }
        }
    }
}

@Composable
private fun Bubble(message: ChatMessage, accent: androidx.compose.ui.graphics.Color) {
    val isUser = message.role == "user"
    val bg = if (isUser) MaterialTheme.colorScheme.surfaceVariant else accent.copy(alpha = 0.22f)
    val align = if (isUser) Alignment.End else Alignment.Start
    Box(Modifier.fillMaxWidth()) {
        Surface(
            color = bg,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .align(if (isUser) Alignment.CenterEnd else Alignment.CenterStart)
                .widthIn(max = 320.dp)
                .padding(2.dp),
        ) {
            Text(
                text = message.text,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
