package com.android.rockages.kordx.infra.database.adapters

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Source-level regression tests for [SQLiteKeyValueDatabaseAdapter].
 *
 * The adapter touches Android SQLite APIs, so behaviour-level JVM tests would
 * need a mocked/fake database. The project's existing regression-test style
 * pins the safety-critical implementation choices in source text instead.
 */
class SQLiteKeyValueDatabaseAdapterTest {

    private fun loadSource(relativePath: String): String {
        val candidates = listOf(
            File(relativePath),
            File("../$relativePath"),
        )
        for (candidate in candidates) {
            if (candidate.exists() && candidate.isFile) {
                return candidate.readText()
            }
        }
        throw IllegalStateException(
            "Could not locate source file $relativePath in ${System.getProperty("user.dir")}"
        )
    }

    @Test
    fun putUsesConflictReplace() {
        val source = loadSource(
            "infra/src/main/java/com/android/rockages/kordx/infra/database/adapters/SQLiteKeyValueDatabaseAdapter.kt"
        )
        assertTrue(
            source.contains("insertWithOnConflict"),
            "SQLiteKeyValueDatabaseAdapter.put must use insertWithOnConflict so existing keys are updated"
        )
        assertTrue(
            source.contains("SQLiteDatabase.CONFLICT_REPLACE"),
            "SQLiteKeyValueDatabaseAdapter.put must pass CONFLICT_REPLACE so updates overwrite existing rows"
        )
    }

    @Test
    fun putNoLongerUsesPlainInsert() {
        val source = loadSource(
            "infra/src/main/java/com/android/rockages/kordx/infra/database/adapters/SQLiteKeyValueDatabaseAdapter.kt"
        )
        val putBody = source.substringAfter("fun put(key: String, value: T): Boolean {")
            .substringBefore("\n }")
        assertFalse(
            putBody.contains("writableDatabase.insert(name, null, values)"),
            "SQLiteKeyValueDatabaseAdapter.put must not use the plain insert(...) overload — it silently fails on PRIMARY KEY conflicts"
        )
    }
}
