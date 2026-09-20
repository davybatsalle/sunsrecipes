package com.sunrecipes.app.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.text.Normalizer

class RecipeRepository(private val context: Context) {
    private val dao = RecipeDatabase.get(context).recipeDao()

    fun observeRecipes(query: String): Flow<List<RecipeEntity>> =
        if (query.isBlank()) dao.observeAll() else dao.search(normalize(query.trim()))

    suspend fun save(recipe: RecipeEntity) = dao.insert(recipe)

    suspend fun delete(recipe: RecipeEntity) = dao.delete(recipe)

    suspend fun exportTo(uri: Uri) {
        val recipes = dao.observeAll().firstValue()
        context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
            it.write(recipes.joinToString(prefix = "[\n", postfix = "\n]") { recipe ->
                val image = recipe.scanImage?.let { Base64.encodeToString(it, Base64.NO_WRAP) } ?: ""
                """  {"name":"${recipe.name.jsonEscaped()}","family":"${recipe.family.jsonEscaped()}","ingredientOne":"${recipe.ingredientOne.jsonEscaped()}","ingredientTwo":"${recipe.ingredientTwo.jsonEscaped()}","ocrText":"${recipe.ocrText.jsonEscaped()}","scanImageBase64":"$image","createdAt":${recipe.createdAt}}"""
            })
        }
    }

    private suspend fun <T> Flow<T>.firstValue(): T = first()

    private fun String.jsonEscaped() = replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")

    private fun normalize(value: String): String = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
        .replace("\\p{Mn}+".toRegex(), "")
}
