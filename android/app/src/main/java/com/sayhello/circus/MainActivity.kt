package com.sayhello.circus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.sayhello.circus.ui.cast.CastScreen
import com.sayhello.circus.ui.chat.ChatScreen
import com.sayhello.circus.ui.settings.SettingsScreen
import com.sayhello.circus.ui.stage.StageScreen
import com.sayhello.circus.ui.theme.DigitalCircusTheme
import com.sayhello.circus.ui.world.WorldScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DigitalCircusTheme { CircusRoot() }
        }
    }
}

private enum class Tab(val label: String) {
    Chat("Sohbet"), World("Dünya"), Cast("Kadro"), Stage("Sahne"), Settings("Ayarlar"),
}

@Composable
private fun CircusRoot() {
    var tab by remember { mutableStateOf(Tab.Chat) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = tab == entry,
                        onClick = { tab = entry },
                        label = { Text(entry.label) },
                        icon = {
                            Icon(
                                when (entry) {
                                    Tab.Chat -> Icons.Filled.Forum
                                    Tab.World -> Icons.Filled.AutoAwesome
                                    Tab.Cast -> Icons.Filled.Groups
                                    Tab.Stage -> Icons.Filled.ViewInAr
                                    Tab.Settings -> Icons.Filled.Settings
                                },
                                contentDescription = entry.label,
                            )
                        },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                Tab.Chat -> ChatScreen()
                Tab.World -> WorldScreen()
                Tab.Cast -> CastScreen(onPick = { tab = Tab.Chat })
                Tab.Stage -> StageScreen()
                Tab.Settings -> SettingsScreen()
            }
        }
    }
}
