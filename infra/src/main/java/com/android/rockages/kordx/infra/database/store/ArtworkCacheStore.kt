package com.android.rockages.kordx.infra.database.store

import android.content.Context
import com.android.rockages.kordx.infra.database.adapters.FileTreeDatabaseAdapter
import java.io.File
import java.nio.file.Paths

class ArtworkCacheStore(val context: Context) {
 companion object {
  const val DEFAULT_TARGET_BYTES = 100L * 1024L * 1024L // 100 MB
 }

 private val adapter = FileTreeDatabaseAdapter(
  Paths
   .get(context.dataDir.absolutePath, "covers")
   .toFile()
 )

 fun get(key: String): File = adapter.get(key)
 fun all() = adapter.list()
 fun clear() = adapter.clear()

 fun totalSizeBytes(): Long =
  adapter.tree.listFiles()?.sumOf { it.length() } ?: 0L

 fun evictToTarget(targetBytes: Long = DEFAULT_TARGET_BYTES) {
  val files = adapter.tree.listFiles() ?: return
  var total = files.sumOf { it.length() }
  if (total <= targetBytes) return

  val sorted = files.asSequence()
   .filter { it.isFile }
   .sortedBy { it.lastModified() }
  for (file in sorted) {
   if (total <= targetBytes) break
   val size = file.length()
   if (file.delete()) {
    total -= size
   }
  }
 }

 fun touch(key: String) {
  try {
   adapter.get(key).setLastModified(System.currentTimeMillis())
  } catch (_: Exception) {
   // Best-effort; eviction still works without accurate access times.
  }
 }
}
