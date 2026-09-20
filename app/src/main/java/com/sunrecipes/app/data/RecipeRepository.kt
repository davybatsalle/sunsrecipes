package com.sunrecipes.app.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import android.util.Base64
import java.io.File
import java.text.Normalizer
import org.json.JSONArray

class RecipeRepository(private val context: Context) {
    private val dao = RecipeDatabase.get(context).recipeDao()

    fun observeRecipes(query: String): Flow<List<RecipeEntity>> =
        if (query.isBlank()) dao.observeAll() else dao.search(normalize(query.trim()))

    suspend fun save(recipe: RecipeEntity) = dao.insert(recipe)

    suspend fun update(recipe: RecipeEntity) = dao.update(recipe)

    suspend fun delete(recipe: RecipeEntity) = dao.delete(recipe)

    suspend fun exportTo(uri: Uri) {
        val recipes = dao.observeAll().firstValue()
        context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
            it.write(recipes.joinToString(prefix = "[\n", postfix = "\n]") { recipe ->
                val image = recipe.scanImagePath?.let { path ->
                    File(path).takeIf(File::isFile)?.readBytes()?.let { Base64.encodeToString(it, Base64.NO_WRAP) }
                } ?: ""
                """  {"name":"${recipe.name.jsonEscaped()}","nameFrench":"${recipe.nameFrench.jsonEscaped()}","family":"${recipe.family.jsonEscaped()}","familiesJson":"${recipe.familiesJson.jsonEscaped()}","ingredientOne":"${recipe.ingredientOne.jsonEscaped()}","ingredientTwo":"${recipe.ingredientTwo.jsonEscaped()}","ingredientsJson":"${recipe.ingredientsJson.jsonEscaped()}","ingredientsFrenchJson":"${recipe.ingredientsFrenchJson.jsonEscaped()}","searchAliases":"${recipe.searchAliases.jsonEscaped()}","ocrText":"${recipe.ocrText.jsonEscaped()}","scanImageBase64":"$image","createdAt":${recipe.createdAt}}"""
            })
        }
    }

    suspend fun importFrom(uri: Uri): Int {
        val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: error("Impossible de lire le fichier de backup")
        val recipes = JSONArray(json)
        var imported = 0
        repeat(recipes.length()) { index ->
            val item = recipes.getJSONObject(index)
            val imagePath = item.optString("scanImageBase64").takeIf(String::isNotBlank)?.let { encoded ->
                val file = File(context.filesDir, "scans/${System.currentTimeMillis()}-$index.jpg")
                file.parentFile?.mkdirs()
                file.writeBytes(Base64.decode(encoded, Base64.DEFAULT))
                file.absolutePath
            }
            dao.insert(RecipeEntity(
                name = item.optString("name", "Recette importée"),
                nameFrench = item.optString("nameFrench"),
                family = item.optString("family", "autres"),
                familiesJson = item.optString("familiesJson", "[]"),
                ingredientOne = item.optString("ingredientOne"),
                ingredientTwo = item.optString("ingredientTwo"),
                ingredientsJson = item.optString("ingredientsJson", "[]"),
                ingredientsFrenchJson = item.optString("ingredientsFrenchJson", "[]"),
                searchAliases = item.optString("searchAliases"),
                ocrText = item.optString("ocrText"),
                scanImagePath = imagePath,
                createdAt = item.optLong("createdAt", System.currentTimeMillis())
            ))
            imported++
        }
        return imported
    }

    private suspend fun <T> Flow<T>.firstValue(): T = first()

    private fun String.jsonEscaped() = replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")

    private fun normalize(value: String): String = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
        .replace("\\p{Mn}+".toRegex(), "")
}
