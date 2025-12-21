package com.example.aichat.console.util

import kotlinx.serialization.json.*

/**
 * Utility functions for converting between Map and JsonObject
 */
object JsonUtils {
    
    /**
     * Converts a Map to JsonObject
     */
    fun mapToJsonObject(map: Map<String, Any>): JsonObject {
        return JsonObject(map.mapValues { convertToJsonElement(it.value) })
    }
    
    /**
     * Converts a JsonObject to Map
     */
    fun jsonObjectToMap(jsonObject: JsonObject?): Map<String, Any>? {
        if (jsonObject == null) return null
        return jsonObject.entries.associate { (it.key to convertFromJsonElement(it.value)) as Pair<String, Any> }
    }
    
    /**
     * Converts any value to JsonElement
     */
    private fun convertToJsonElement(value: Any?): JsonElement {
        return when (value) {
            null -> JsonNull
            is String -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            is Map<*, *> -> {
                val map = value.mapKeys { it.key.toString() }
                    .mapValues { convertToJsonElement(it.value) }
                JsonObject(map)
            }
            is List<*> -> JsonArray(value.map { convertToJsonElement(it) })
            else -> JsonPrimitive(value.toString())
        }
    }
    
    /**
     * Converts JsonElement to Any
     */
    private fun convertFromJsonElement(element: JsonElement): Any? {
        return when (element) {
            is JsonPrimitive -> {
                when {
                    element.isString -> element.content
                    element.booleanOrNull != null -> element.boolean
                    element.longOrNull != null -> element.long
                    element.doubleOrNull != null -> element.double
                    else -> element.content
                }
            }
            is JsonObject -> {
                element.entries.associate { it.key to convertFromJsonElement(it.value) }
            }
            is JsonArray -> {
                element.map { convertFromJsonElement(it) }
            }
            is JsonNull -> null
        }
    }
}
