package com.sunrecipes.app.data

import org.json.JSONArray
import org.json.JSONObject

data class RecipeIngredient(val name: String)

object RecipeContent {
    fun encodeIngredients(ingredients: List<RecipeIngredient>): String = JSONArray().apply {
        ingredients.filter { it.name.isNotBlank() }.forEach { ingredient ->
            put(ingredient.name)
        }
    }.toString()

    fun decodeIngredients(value: String): List<RecipeIngredient> = runCatching {
        val json = JSONArray(value)
        List(json.length()) { index ->
            val item = json.get(index)
            if (item is JSONObject) RecipeIngredient(item.optString("name")) else RecipeIngredient(item.toString())
        }.filter { it.name.isNotBlank() }
    }.getOrDefault(emptyList())

    fun cleanText(name: String, ingredients: List<RecipeIngredient>): String = buildString {
        if (name.isNotBlank()) appendLine(name.trim())
        ingredients.forEach { ingredient ->
            appendLine(ingredient.name.trim())
        }
    }.trim()
}