package com.android.rockages.kordx.core.utils

import android.net.Uri
import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * JVM unit tests for [RoomConvertors].
 *
 * The converters are used by Room to persist core types (Uri, string sets,
 * string lists, LocalDate) into SQLite. These tests pin the round-trip
 * behaviour so a future change to the serialization format is caught early.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class RoomConvertorsTest {

    private val convertors = RoomConvertors()

    @Test
    fun uriRoundTrip() {
        val uri = Uri.parse("content://media/external/audio/media/42")
        val serialized = convertors.serializeUri(uri)
        val deserialized = convertors.deserializeUri(serialized)
        assertEquals(uri, deserialized)
    }

    @Test
    fun emptyStringSetRoundTrip() {
        val set = emptySet<String>()
        val serialized = convertors.serializeStringSet(set)
        val deserialized = convertors.deserializeStringSet(serialized)
        assertEquals(set, deserialized)
    }

    @Test
    fun nonEmptyStringSetRoundTrip() {
        val set = setOf("a", "b", "c")
        val serialized = convertors.serializeStringSet(set)
        val deserialized = convertors.deserializeStringSet(serialized)
        assertEquals(set, deserialized)
    }

    @Test
    fun stringListRoundTrip() {
        val list = listOf("one", "two", "three")
        val serialized = convertors.serializeStringList(list)
        val deserialized = convertors.deserializeStringList(serialized)
        assertEquals(list, deserialized)
    }

    @Test
    fun localDateRoundTrip() {
        val date = LocalDate.of(2024, 8, 3)
        val serialized = convertors.serializeLocalDate(date)
        val deserialized = convertors.deserializeLocalDate(serialized)
        assertEquals(date, deserialized)
    }
}
