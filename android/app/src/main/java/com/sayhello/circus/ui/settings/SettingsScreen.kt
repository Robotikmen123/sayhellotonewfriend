package com.sayhello.circus.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.sayhello.circus.CircusApp
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as CircusApp
    val scope = rememberCoroutineScope()

    var apiKey by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("claude-opus-4-7") }
    var status by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        apiKey = app.preferences.apiKey().orEmpty()
        model = app.preferences.model()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Ayarlar", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Anahtarın yalnızca telefonda saklanıyor. Yedeklere dâhil edilmiyor.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )

        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text("Anthropic API anahtarı") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
        )

        OutlinedTextField(
            value = model,
            onValueChange = { model = it },
            label = { Text("Model") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        Button(
            onClick = {
                scope.launch {
                    app.preferences.setApiKey(apiKey.trim())
                    app.preferences.setModel(model.trim())
                    status = "Kaydedildi."
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Kaydet") }

        TextButton(
            onClick = {
                scope.launch {
                    app.preferences.resetProgression()
                    status = "İlk açılış tarihi sıfırlandı; kilitler yeniden açılacak."
                }
            },
        ) { Text("Haftalık kilitleri sıfırla") }

        status?.let { Text(it, color = MaterialTheme.colorScheme.secondary) }

        Text(
            text = """
                Karakterler "uygulama dışı izin" istediğinde bu sadece sahne
                gereği. Uygulama hiçbir Intent göndermez, dosya okuyup yazmaz,
                ses kaydı almaz, kişilerine bakmaz. Tek host izni: İnternet
                (Anthropic API'ya konuşmak için).
            """.trimIndent(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}
