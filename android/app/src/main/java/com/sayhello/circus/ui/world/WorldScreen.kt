package com.sayhello.circus.ui.world

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sayhello.circus.CircusApp
import com.sayhello.circus.ai.ClaudeClient
import com.sayhello.circus.ai.WorldSimulator
import com.sayhello.circus.character.CharacterRegistry
import com.sayhello.circus.character.UnlockManager
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WorldScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as CircusApp
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val unlockManager = remember { UnlockManager(app.preferences) }
    val simulator = remember {
        WorldSimulator(
            clientFactory = {
                ClaudeClient(
                    apiKeyProvider = { app.preferences.apiKey() },
                    modelProvider = { app.preferences.model() },
                )
            },
            unlockManager = unlockManager,
            store = app.worldEventStore,
        )
    }
    val events by app.worldEventStore.stream().collectAsState(initial = emptyList())
    var nudging by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Dünya Akışı", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Kimsenin onayını beklemeden sahne kuran kadronun günlüğü.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                enabled = !nudging,
                onClick = {
                    nudging = true
                    scope.launch {
                        runCatching { simulator.nudge("caine") }
                        nudging = false
                    }
                },
            ) { Text("Caine’a sahne aç") }
            Button(
                enabled = !nudging,
                onClick = {
                    nudging = true
                    scope.launch {
                        val unlocked = unlockManager.unlocked()
                        if (unlocked.isNotEmpty()) {
                            runCatching { simulator.nudge(unlocked.random().id) }
                        }
                        nudging = false
                    }
                },
            ) { Text("Rastgele oyuncu") }
        }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(events.asReversed()) { event ->
                val character = CharacterRegistry.byId(event.characterId)
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            text = "${character.displayName} — ${event.verb}",
                            style = MaterialTheme.typography.titleSmall,
                            color = character.accent,
                        )
                        Text(
                            text = SimpleDateFormat("d MMM HH:mm", Locale("tr")).format(Date(event.ts)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                        Text(
                            text = event.text,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
            if (events.isEmpty()) {
                item {
                    Text(
                        "Sahne henüz boş. Bir oyuncu davet etmeyi dene.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }
    }
}
