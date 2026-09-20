package com.sunrecipes.app.ocr

import android.content.Context
import com.sunrecipes.app.data.RecipeEntity
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer

class RecipeOcrParser(context: Context) {
    private val dictionary = RecipeDictionary.load(context)

    fun parse(text: String, image: ByteArray?): List<RecipeEntity> {
        val sections = text.split(Regex("\\n\\s*\\n|(?m)^#{2,}\\s*"))
            .map(String::trim).filter { it.length > 15 }.ifEmpty { listOf(text.trim()) }
        return sections.mapIndexed { index, section ->
            val lines = section.lines().map(String::trim).filter(String::isNotBlank)
            val name = lines.firstOrNull()?.takeIf { it.length < 80 } ?: "Recette scannée ${index + 1}"
            val ingredients = lines.filter { it.contains(Regex("\\d|g\\b|kg\\b|cl\\b|cs\\b|cuillère", RegexOption.IGNORE_CASE)) }
            val ingredientOne = ingredients.firstOrNull()?.substringAfterLast(" ", "Ingrédient principal") ?: "Ingrédient principal"
            val ingredientTwo = ingredients.getOrNull(1)?.substringAfterLast(" ", "") ?: ""
            RecipeEntity(
                name = name.replace(Regex("^[#*\\- ]+"), "").trim(),
                family = familyFor(section),
                ingredientOne = ingredientOne,
                ingredientTwo = ingredientTwo,
                searchAliases = aliasesFor(section, name, ingredientOne, ingredientTwo),
                ocrText = section,
                scanImage = image
            )
        }
    }

    private fun familyFor(text: String): String {
        val normalizedText = normalize(text)
        return dictionary.families.entries.firstOrNull { (_, words) ->
            words.any { containsTerm(normalizedText, it) }
        }?.key ?: "Autres"
    }

    private fun aliasesFor(text: String, name: String, ingredientOne: String, ingredientTwo: String): String {
        val normalizedText = normalize(text)
        val aliases = buildSet {
            add(name)
            add(ingredientOne)
            add(ingredientTwo)
            dictionary.families.forEach { (family, words) ->
                if (words.any { containsTerm(normalizedText, it) }) add(family)
            }
            dictionary.ingredients.forEach { (french, english) ->
                if (containsTerm(normalizedText, french) || containsTerm(normalizedText, english)) {
                    add(french)
                    add(english)
                }
            }
        }
        return aliases.flatMap { listOf(it, normalize(it)) }.joinToString(" ").lowercase()
    }

    private fun containsTerm(normalizedText: String, term: String): Boolean {
        val normalizedTerm = Regex.escape(normalize(term)).replace("\\ ", "\\\\s+")
        return Regex("(?<![a-z])$normalizedTerm(?![a-z])").containsMatchIn(normalizedText)
    }

    private fun normalize(value: String): String = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
        .replace("\\p{Mn}+".toRegex(), "")

    private data class RecipeDictionary(
        val families: Map<String, List<String>>,
        val ingredients: List<Pair<String, String>>
    ) {
        companion object {
            fun load(context: Context): RecipeDictionary = runCatching {
                val json = context.assets.open("ingredient_dictionary.json").bufferedReader().use { it.readText() }
                val root = JSONObject(json)
                val familyObject = root.getJSONObject("families")
                val families = familyObject.keys().asSequence().associateWith { key ->
                    familyObject.getJSONArray(key).toStringList()
                }
                val ingredients = root.getJSONArray("ingredients").toPairList()
                RecipeDictionary(families, ingredients)
            }.getOrElse { RecipeDictionary(emptyMap(), emptyList()) }
        }
    }
}

private fun JSONArray.toStringList(): List<String> = List(length()) { getString(it) }

private fun JSONArray.toPairList(): List<Pair<String, String>> = List(length()) { index ->
    getJSONObject(index).getString("fr") to getJSONObject(index).getString("en")
}
