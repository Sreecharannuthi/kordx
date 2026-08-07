package com.android.rockages.kordx.infra.database.store

import android.content.Context
import com.android.rockages.kordx.infra.database.adapters.FileTreeDatabaseAdapter
import java.nio.file.Paths

class ArtworkCacheStore(val context: Context) {
 private val adapter = FileTreeDatabaseAdapter(
 Paths
 .get(context.dataDir.absolutePath, "covers")
 .toFile()
 )

 fun get(key: String) = adapter.get(key)
 fun all() = adapter.list()
 fun clear() = adapter.clear()
}
