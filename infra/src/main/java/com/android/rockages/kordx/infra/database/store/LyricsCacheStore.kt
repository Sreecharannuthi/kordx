package com.android.rockages.kordx.infra.database.store

import android.content.Context
import com.android.rockages.kordx.infra.database.adapters.SQLiteKeyValueDatabaseAdapter

class LyricsCacheStore(val context: Context) {
 private val adapter = SQLiteKeyValueDatabaseAdapter(
 SQLiteKeyValueDatabaseAdapter.Transformer.AsString(),
 SQLiteKeyValueDatabaseAdapter.CacheOpenHelper(context, "lyrics", 1)
 )

 fun get(key: String) = adapter.get(key)
 fun put(key: String, value: String) = adapter.put(key, value)
 fun delete(key: String) = adapter.delete(key)
 fun delete(keys: Collection<String>) = adapter.delete(keys)
 fun keys() = adapter.keys()
 fun all() = adapter.all()
 fun clear() = adapter.clear()
}
