package com.sayhello.circus.model3d

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/**
 * On first launch (or when a bundled asset is newer than the extracted copy),
 * unpack any `assets/models/<id>.zip` files into `filesDir/models/<id>/` so
 * the Stage tab can render them without a manual file-picker step.
 *
 * Safety rules mirror [ModelArchive]: extraction stays inside the app sandbox,
 * zip-slip is rejected, total size capped.
 */
class AssetExtractor(private val context: Context) {

    private val maxBytes = 64L * 1024 * 1024
    private val maxEntries = 200

    fun extractAllBundled() {
        val assets = context.assets
        val files = runCatching { assets.list("models") }.getOrNull().orEmpty()
        files.filter { it.endsWith(".zip", ignoreCase = true) }.forEach { name ->
            val characterId = name.removeSuffix(".zip").removeSuffix(".ZIP")
            extractOne(name, characterId)
        }
    }

    private fun extractOne(assetName: String, characterId: String) {
        val outDir = File(context.filesDir, "models/$characterId")
        val marker = File(outDir, ".bundled-${assetName.hashCode()}")
        if (marker.exists()) return

        outDir.deleteRecursively()
        outDir.mkdirs()

        var written = 0L
        var entries = 0
        context.assets.open("models/$assetName").use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    entries++
                    require(entries <= maxEntries) { "Zip'te çok fazla dosya." }
                    val safeName = entry.name.replace('\\', '/')
                    require(!safeName.startsWith("/") && !safeName.contains("..")) {
                        "Güvensiz dosya yolu: $safeName"
                    }
                    if (!entry.isDirectory) {
                        val outFile = File(outDir, safeName)
                        require(outFile.canonicalPath.startsWith(outDir.canonicalPath)) {
                            "Zip-slip engellendi: $safeName"
                        }
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { fos ->
                            val buf = ByteArray(32 * 1024)
                            while (true) {
                                val n = zip.read(buf)
                                if (n <= 0) break
                                written += n
                                require(written <= maxBytes) { "Zip 64MB sınırını aştı." }
                                fos.write(buf, 0, n)
                            }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
        marker.writeText("ok")
    }
}
