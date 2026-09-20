package com.sunrecipes.app.data

import org.json.JSONArray

object RecipeFamilies {
    val allowed = listOf("viandes", "légumes", "desserts", "poissons", "soupes", "œufs", "autres")

    fun encode(values: List<String>): String = JSONArray(values.filter { it in allowed }.distinct()).toString()

    fun decode(value: String, fallback: String): List<String> = runCatching {
        val result = List(JSONArray(value).length()) { JSONArray(value).getString(it) }
            .map { canonical(it) }.filter { it in allowed }.distinct()
        result.ifEmpty { listOf(canonical(fallback)) }
    }.getOrDefault(listOf(canonical(fallback)))

    fun canonical(value: String): String = allowed.firstOrNull { it.equals(value, ignoreCase = true) }
        ?: when {
            value.equals("legumes", ignoreCase = true) -> "légumes"
            value.equals("oeufs", ignoreCase = true) || value.equals("eggs", ignoreCase = true) -> "œufs"
            else -> "autres"
        }
}
