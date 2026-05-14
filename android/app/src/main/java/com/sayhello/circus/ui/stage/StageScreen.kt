package com.sayhello.circus.ui.stage

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sayhello.circus.CircusApp
import com.sayhello.circus.character.CharacterRegistry
import com.sayhello.circus.model3d.ModelArchive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Stage tab: hosts the 3D viewer placeholder. SceneView wiring is intentionally
 * lightweight: we just point at an extracted model file. Drop the user-supplied
 * Caine zip into Settings > "3D model yükle" and a .glb is decoded from it.
 */
@Composable
fun StageScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as CircusApp
    val scope = rememberCoroutineScope()
    val archive = remember { ModelArchive(context) }

    var activeId by remember { mutableStateOf("caine") }
    var loaded by remember { mutableStateOf<ModelArchive.Loaded?>(null) }
    var statusText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(activeId) {
        activeId = app.preferences.activeCharacterId()
        loaded = withContext(Dispatchers.IO) { archive.existing(activeId) }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            statusText = "Açılıyor..."
            runCatching {
                withContext(Dispatchers.IO) { archive.importFromUri(uri, activeId) }
            }.onSuccess {
                loaded = it
                statusText = "Yüklendi: ${it.entryFile.name}"
            }.onFailure {
                statusText = "Hata: ${it.message}"
            }
        }
    }

    val character = CharacterRegistry.byId(activeId)

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Sahne — ${character.displayName}", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Karakterin .glb / .gltf içerikli .zip'ini buradan yükle. " +
                "Yalnızca uygulamanın özel klasörüne çıkarılır.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp)
                .background(
                    character.accent.copy(alpha = 0.18f),
                    RoundedCornerShape(16.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            val l = loaded
            if (l == null) {
                Text(
                    "Model henüz yok.\n.zip seç → otomatik çıkarılır.",
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                // SceneView entegrasyonu burada devreye girer.
                // Şimdilik dosya yolu gösteriliyor; SceneView Compose entegrasyonu
                // app modülünde Scene composable'ı eklendiğinde model bu yola
                // bağlanır (model3d/SceneHost.kt'i kendine göre uyarlayabilirsin).
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Yüklü model:", style = MaterialTheme.typography.labelMedium)
                    Text(l.entryFile.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        l.rootDir.absolutePath.removePrefix(context.filesDir.absolutePath),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
            }
        }

        Button(
            onClick = { picker.launch(arrayOf("application/zip")) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("${character.displayName} için .zip seç") }

        statusText?.let {
            Text(it, style = MaterialTheme.typography.bodySmall)
        }

        Text(
            "Henüz SceneView render adımı bağlı değil — model dosyaları " +
                "kullanılabilir konumda duruyor. SceneView entegrasyonu, modeli " +
                "elinde olduğunda Compose içinde tek bir `Scene { Model(filePath) }` " +
                "çağrısıyla eklenecek.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
    }
}
