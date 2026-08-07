package com.android.rockages.kordx.services.i18n

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.InputStream

class Translation(container: _Container) : _Translation(container) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /**
         * `_Keys` is a map-backed class rather than a data class because the
         * translation set now exceeds the JVM/Dalvik method-descriptor limit
         * for a generated `copy()` (255+ explicit constructor parameters would
         * produce a `copy$default` with too many argument registers and fail
         * class verification at runtime).
         *
         * The loader builds a plain `Map<String, String>` from the JSON keys
         * object and passes it to the `_Keys` constructor. Missing keys fall
         * back to the empty string, matching the previous behavior.
         */
        @OptIn(ExperimentalSerializationApi::class)
        fun fromInputStream(input: InputStream): Translation {
            val root: JsonObject = json
                .parseToJsonElement(input.readBytes().decodeToString())
                .jsonObject
            val locale = _Locale(
                display = localeString(root, "display"),
                native = localeString(root, "native"),
                code = localeString(root, "code"),
                direction = localeString(root, "direction"),
            )
            val keysObj = root["keys"]?.jsonObject
                ?: error("Translation JSON: missing 'keys' object")
            val keys = buildKeys(keysObj)
            return Translation(_Container(locale, keys))
        }

        private fun localeString(root: JsonObject, field: String): String =
            root["locale"]?.jsonObject?.get(field)?.jsonPrimitive?.content
                ?: error("Translation JSON: missing locale.$field")

        private fun buildKeys(obj: JsonObject): _Keys {
            val map = obj.mapValues { it.value.jsonPrimitive.content }
            return _Keys(map)
        }
    }
}
