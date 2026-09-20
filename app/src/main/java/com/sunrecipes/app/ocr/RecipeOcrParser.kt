package com.sunrecipes.app.ocr

import android.content.Context
import com.sunrecipes.app.data.RecipeContent
import com.sunrecipes.app.data.RecipeEntity
import com.sunrecipes.app.data.RecipeFamilies
import com.sunrecipes.app.data.RecipeIngredient
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer

class RecipeOcrParser(context: Context) {
    private val dictionary = RecipeDictionary.load(context)

    fun parse(text: String, imagePath: String?): List<RecipeEntity> {
        val sections = text.split(Regex("\\n\\s*\\n|(?m)^#{2,}\\s*"))
            .map(String::trim).filter { it.length > 15 }.ifEmpty { listOf(text.trim()) }
        return sections.mapIndexed { index, section ->
            val lines = section.lines().map(String::trim).filter(String::isNotBlank)
            val name = lines.firstOrNull()?.takeIf { it.length < 80 } ?: "Recette scannée ${index + 1}"
            val content = extractContent(lines)
            val cleanName = name.replace(Regex("^[#*\\- ]+"), "").trim()
            createRecipe(cleanName, familyFor(section), content.ingredients, imagePath)
        }
    }

    fun parseStructured(
        name: String,
        nameFrench: String = name,
        family: String,
        families: List<String> = listOf(family),
        ingredientOne: String,
        ingredientTwo: String,
        ingredients: List<RecipeIngredient> = listOfNotNull(
            ingredientOne.takeIf(String::isNotBlank)?.let(::RecipeIngredient),
            ingredientTwo.takeIf(String::isNotBlank)?.let(::RecipeIngredient)
        ),
        frenchIngredients: List<RecipeIngredient> = ingredients.map { RecipeIngredient(translateToFrench(it.name)) },
        ocrText: String,
        imagePath: String?
    ): RecipeEntity = createRecipe(
        name = name.ifBlank { "Recette scannée" },
        nameFrench = nameFrench,
        family = validatedFamily(family, ocrText),
        families = families,
        ingredients = validatedIngredients(ingredients, ocrText),
        imagePath = imagePath,
        frenchIngredients = frenchIngredients
    )

    fun reconcileVisionResult(result: RecipeEntity, ocrText: String, imagePath: String?): RecipeEntity {
        val ingredients = RecipeContent.decodeIngredients(result.ingredientsJson)
        return parseStructured(
            name = result.name,
            family = result.family,
            ingredientOne = result.ingredientOne,
            ingredientTwo = result.ingredientTwo,
            ingredients = ingredients,
            ocrText = ocrText,
            imagePath = imagePath
        )
    }

    private fun validatedFamily(modelFamily: String, text: String): String {
        val detected = familyFor(text)
        return if (detected != "Autres") detected else if (modelFamily.equals("Œufs", ignoreCase = true) || dictionary.families.keys.any { it.equals(modelFamily, ignoreCase = true) }) modelFamily else "Autres"
    }

    private fun validatedIngredients(modelIngredients: List<RecipeIngredient>, text: String): List<RecipeIngredient> {
        val detected = dictionary.ingredients.filter { (french, english) ->
            containsTerm(normalize(text), french) || containsTerm(normalize(text), english)
        }.map { (_, english) -> RecipeIngredient(english) }.distinctBy { normalize(it.name) }
        return (detected.ifEmpty { modelIngredients }).take(2)
    }

    private fun createRecipe(name: String, family: String, ingredients: List<RecipeIngredient>, imagePath: String?, nameFrench: String = name, frenchIngredients: List<RecipeIngredient> = ingredients.map { RecipeIngredient(translateToFrench(it.name)) }, families: List<String> = listOf(family)): RecipeEntity {
        val mainIngredients = ingredients.take(2)
        val first = mainIngredients.getOrNull(0)?.name ?: "Ingrédient principal"
        val second = mainIngredients.getOrNull(1)?.name.orEmpty()
        val cleanText = RecipeContent.cleanText(name, mainIngredients)
        return RecipeEntity(
            name = name,
            nameFrench = nameFrench.ifBlank { name },
            family = canonicalFamily(familyFor((listOf(name, nameFrench) + mainIngredients.map { it.name }).joinToString(" ")).let { detected ->
                if (detected != "Autres") detected else family
            }),
            familiesJson = RecipeFamilies.encode(families.map(::canonicalFamily)),
            ingredientOne = first,
            ingredientTwo = second,
            ingredientsJson = RecipeContent.encodeIngredients(mainIngredients),
            ingredientsFrenchJson = RecipeContent.encodeIngredients(frenchIngredients.take(2)),
            searchAliases = aliasesFor(cleanText, name, mainIngredients),
            ocrText = cleanText,
            scanImagePath = imagePath
        )
    }

    private fun extractContent(lines: List<String>): ParsedContent {
        val body = lines.drop(1)
        val preparationIndex = body.indexOfFirst(::isPreparationHeader)
        val ingredientArea = if (preparationIndex >= 0) body.take(preparationIndex) else body
        val ingredientLines = ingredientArea.filter(::isIngredientLine)
        val firstStepIndex = if (preparationIndex >= 0) preparationIndex + 1 else {
            body.indexOfFirst { line -> !isIngredientLine(line) && line.length > 24 }
                .takeIf { it >= 0 } ?: body.size
        }
        val ingredients = ingredientLines.mapNotNull(::parseIngredientLine)
        return ParsedContent(ingredients)
    }

    private fun isPreparationHeader(line: String): Boolean = normalize(line).matches(
        Regex(".*\\b(preparation|preparations|instructions|directions|method|etapes|recette|preparation)\\b.*")
    )

    private fun isIngredientLine(line: String): Boolean {
        val normalized = normalize(line)
        return QUANTITY_PATTERN.containsMatchIn(line) ||
            (line.matches(Regex("^[*\\-•].*")) && dictionary.families.values.flatten().any { containsTerm(normalized, it) })
    }

    private fun parseIngredientLine(line: String): RecipeIngredient? {
        val cleaned = line.replace(Regex("^[*\\-• ]+"), "").trim()
        val withoutQuantity = cleaned.replace(Regex("^(to taste|à volonté|a volonté|[0-9]+(?:[.,][0-9]+)?\\s*(?:g|kg|mg|ml|cl|l|oz|lb|lbs|cup|cups|tbsp|tsp|tablespoon[s]?|teaspoon[s]?|cuillère[s]?|c\u00e0s|càc)?)\\s+", RegexOption.IGNORE_CASE), "")
        return withoutQuantity.takeIf { it.isNotBlank() }?.let(::RecipeIngredient)
    }

    private fun isDecoration(line: String): Boolean = line.length < 2 || line.matches(Regex("^[^A-Za-zÀ-ÿ]+$"))

    private fun familyFor(text: String): String {
        val normalizedText = normalize(text)
        if (listOf("oeuf", "œuf", "oeufs", "œufs", "egg", "eggs").any { containsTerm(normalizedText, it) }) return "Œufs"
        return dictionary.families.entries.firstOrNull { (_, words) ->
            words.any { containsTerm(normalizedText, it) }
        }?.key ?: "Autres"
    }

    private fun aliasesFor(text: String, name: String, ingredients: List<RecipeIngredient>): String {
        val normalizedText = normalize(text)
        val aliases = buildSet {
            add(name)
            ingredients.forEach { add(it.name) }
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

    private fun translateToFrench(name: String): String {
        dictionary.ingredients.firstOrNull { (_, english) -> normalize(english) == normalize(name) }?.let { return it.first }
        return name
    }

    private fun canonicalFamily(value: String): String {
        val normalized = normalize(value)
        return when {
            normalized == "viandes" -> "viandes"
            normalized == "legumes" -> "légumes"
            normalized == "desserts" -> "desserts"
            normalized == "poissons" -> "poissons"
            normalized == "soupes" -> "soupes"
            normalized == "oeufs" -> "œufs"
            normalized == "salades" || normalized == "salade" -> "salades"
            else -> "autres"
        }
    }

    private fun containsTerm(normalizedText: String, term: String): Boolean {
        val normalizedTerm = Regex.escape(normalize(term)).replace("\\ ", "\\\\s+")
        return Regex("(?<![a-z])$normalizedTerm(?![a-z])").containsMatchIn(normalizedText)
    }

    private fun normalize(value: String): String = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
        .replace("\\p{Mn}+".toRegex(), "")

    private data class ParsedContent(val ingredients: List<RecipeIngredient>)

    companion object {
        private val QUANTITY_PATTERN = Regex("(?:^|\\s)(?:[0-9]+(?:[.,][0-9]+)?\\s*(?:g|kg|mg|ml|cl|l|oz|lb|lbs|cup|cups|tbsp|tsp|tablespoon|teaspoon|cuillère|cà?s)|to taste|à volonté|a volonté)(?:\\s|$)", RegexOption.IGNORE_CASE)
    }

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
                val ingredients = buildList {
                    root.optJSONArray("extraIngredients")?.let { addAll(it.toPairList()) }
                    addAll(root.getJSONArray("ingredients").toPairList())
                }
                RecipeDictionary(families, ingredients)
            }.getOrElse { RecipeDictionary(emptyMap(), emptyList()) }
        }
    }
}

private fun JSONArray.toStringList(): List<String> = List(length()) { getString(it) }

private fun JSONArray.toPairList(): List<Pair<String, String>> = List(length()) { index ->
    getJSONObject(index).getString("fr") to getJSONObject(index).getString("en")
}
