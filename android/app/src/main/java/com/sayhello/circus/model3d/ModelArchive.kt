package com.sayhello.circus.model3d

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/**
 * Sandboxes user-provided 3D model archives. The archive must contain at least
 * one .glb or .gltf file. Extraction:
 *   - writes only inside app-internal `filesDir/models/<characterId>/`
 *   - rejects zip-slip paths (".." traversal, absolute paths)
 *   - caps total extracted size to 64 MB and 200 entries
 */
class ModelArchive(private val context: Context) {

    data class Loaded(val rootDir: File, val entryFile: File)

    private val maxBytes = 64L * 1024 * 1024
    private val maxEntries = 200

    fun targetDir(characterId: String): File =
        File(context.filesDir, "models/$characterId").also { it.mkdirs() }

    fun existing(characterId: String): Loaded? {
        val dir = targetDir(characterId)
        val entry = dir.walkTopDown().firstOrNull {
            it.isFile && (it.name.endsWith(".glb", true) || it.name.endsWith(".gltf", true))
        } ?: return null
        return Loaded(dir, entry)
    }

    fun importFromUri(uri: Uri, characterId: String): Loaded {
        val dir = targetDir(characterId)
        dir.deleteRecursively()
        dir.mkdirs()

        val resolver = context.contentResolver
        val input = resolver.openInputStream(uri) ?: error("Zip açılamadı.")
        var written = 0L
        var entries = 0
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                entries++
                if (entries > maxEntries) error("Çok fazla dosya (max $maxEntries).")
                val safeName = entry.name.replace('\\', '/')
                if (safeName.startsWith("/") || safeName.contains("..")) {
                    error("Güvensiz dosya yolu: $safeName")
                }
                if (!entry.isDirectory) {
                    val outFile = File(dir, safeName)
                    val canonicalDir = dir.canonicalPath
                    if (!outFile.canonicalPath.startsWith(canonicalDir)) {
                        error("Zip-slip engellendi: $safeName")
                    }
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { fos ->
                        val buf = ByteArray(16 * 1024)
                        while (true) {
                            val n = zip.read(buf)
                            if (n <= 0) break
                            written += n
                            if (written > maxBytes) error("Zip çok büyük (max 64MB).")
                            fos.write(buf, 0, n)
                        }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        val loaded = existing(characterId)
            ?: error("Zip içinde .glb ya da .gltf bulunamadı.")
        return loaded
    }
}
