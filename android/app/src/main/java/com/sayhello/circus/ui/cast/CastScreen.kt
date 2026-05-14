package com.sayhello.circus.ui.cast

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sayhello.circus.CircusApp
import com.sayhello.circus.character.CharacterRegistry
import com.sayhello.circus.character.CircusCharacter
import com.sayhello.circus.character.UnlockManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CastScreen(onPick: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as CircusApp
    val manager = remember { UnlockManager(app.preferences) }
    var unlockedIds by remember { mutableStateOf(setOf<String>()) }
    var nextLine by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        unlockedIds = manager.unlocked().map { it.id }.toSet()
        val next = manager.nextUnlockAt()
        nextLine = next?.let { (c, ts) ->
            val fmt = SimpleDateFormat("d MMM, HH:mm", Locale("tr"))
            "${c.displayName} sahneye çıkacak: ${fmt.format(Date(ts))}"
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Kadro", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Her hafta yeni bir oyuncu sahneye çıkıyor. Aşağıdaki sırayla.",
            style = MaterialTheme.typography.bodyMedium,
        )
        nextLine?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(CharacterRegistry.all) { character ->
                val unlocked = character.id in unlockedIds
                CharacterCard(
                    character = character,
                    unlocked = unlocked,
                    onPick = {
                        if (unlocked) {
                            val vm = ChatBridge(app)
                            vm.activate(character.id, onPick)
                        }
                    },
                )
            }
        }
    }
}

private class ChatBridge(private val app: CircusApp) {
    fun activate(id: String, then: () -> Unit) {
        kotlinx.coroutines.runBlocking {
            app.preferences.setActiveCharacter(id)
        }
        then()
    }
}

@Composable
private fun CharacterCard(
    character: CircusCharacter,
    unlocked: Boolean,
    onPick: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(character.accent.copy(alpha = if (unlocked) 0.9f else 0.25f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = character.displayName.first().toString(),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(character.displayName, style = MaterialTheme.typography.titleMedium)
                Text(
                    character.tagline,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                )
                Text(
                    "Hafta ${character.unlockWeek}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            }
            if (unlocked) {
                TextButton(onClick = onPick) { Text("Konuş") }
            } else {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = "Kilitli",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            }
        }
    }
}
