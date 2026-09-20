package com.sunrecipes.app.ai

import android.util.Base64
import com.sunrecipes.app.data.RecipeEntity
import com.sunrecipes.app.data.RecipeFamilies
import com.sunrecipes.app.ocr.RecipeOcrParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class GeminiRecipeAnalyzer(
    private val parser: RecipeOcrParser
) {
    suspend fun analyze(file: File, imagePath: String, apiKey: String): List<RecipeEntity> = withContext(Dispatchers.IO) {
        var lastModelError: Exception? = null
        for (modelName in MODEL_NAMES) {
            try {
                return@withContext analyzeWithModel(file, imagePath, apiKey, modelName)
            } catch (error: GeminiHttpException) {
                if (error.code !in setOf(400, 403, 404)) throw error
                lastModelError = error
            }
        }
        throw lastModelError ?: IllegalStateException("Aucun modèle Gemini disponible")
    }

    private fun analyzeWithModel(file: File, imagePath: String, apiKey: String, modelName: String): List<RecipeEntity> {
            val image = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
            val body = JSONObject().apply {
                put("contents", JSONArray().put(JSONObject().apply {
                    put("parts", JSONArray()
                        .put(JSONObject().put("inline_data", JSONObject().apply {
                            put("mime_type", "image/jpeg")
                            put("data", image)
                        }))
                        .put(JSONObject().put("text", PROMPT)))
                }))
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.1)
                    put("responseMimeType", "application/json")
                })
            }
            val connection = (URL("https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 90_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
            connection.outputStream.use { it.write(body.toString().toByteArray()) }
            val responseText = (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream)
                .bufferedReader().use { it.readText() }
            if (connection.responseCode !in 200..299) {
                val detail = responseText.take(300).replace(Regex("\\s+"), " ")
                throw GeminiHttpException(connection.responseCode, "Gemini HTTP ${connection.responseCode}: $detail")
            }
            val generated = JSONObject(responseText)
                .getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts").getJSONObject(0)
                .getString("text")
            val recipes = JSONArray(generated.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim())
            return List(recipes.length()) { index ->
                val item = recipes.getJSONObject(index)
                val ingredients = item.optJSONArray("ingredients")?.let { values ->
                    List(values.length()) { RecipeEntityIngredient(values.getString(it)) }.map { it.value }
                } ?: emptyList()
                val main = ingredients.take(2)
                parser.parseStructured(
                    name = item.optString("name"),
                    nameFrench = item.optString("nameFrench"),
                    family = item.optString("family"),
                    families = item.optJSONArray("families")?.let { values -> List(values.length()) { values.getString(it) } } ?: listOf(item.optString("family")),
                    ingredients = main.map { com.sunrecipes.app.data.RecipeIngredient(it) },
                    frenchIngredients = item.optJSONArray("ingredientsFrench")?.let { values ->
                        List(values.length()) { com.sunrecipes.app.data.RecipeIngredient(values.getString(it)) }
                    } ?: main.map { com.sunrecipes.app.data.RecipeIngredient(it) },
                    ocrText = "",
                    imagePath = imagePath
                )
                }
    }

            private class GeminiHttpException(val code: Int, message: String) : Exception(message)

    private data class RecipeEntityIngredient(val value: String)

    companion object {
        private val MODEL_NAMES = listOf("gemini-2.5-flash", "gemini-3.5-flash-lite")
        private const val PROMPT = """
Analyze this recipe page image. Return only a JSON array with objects containing name, nameFrench, family, families, ingredients, and ingredientsFrench.
nameFrench must be the French translation of the title. ingredientsFrench must contain only the French names of at most two main ingredients, with no quantities.
Preserve the original values in name and ingredients internally, but the user-facing fields will use only nameFrench and ingredientsFrench.
Infer one or more families from the title and the main ingredients. families must be an array containing only these exact lowercase values: viandes, légumes, desserts, poissons, soupes, œufs, salades, autres. Set family to the first selected value. Do not use unrelated decorative text.
Return at most two main ingredient names, without quantities or preparation steps.
Ignore decorative text, slogans, captions and unrelated page content.
Preserve the recipe language. Do not translate.
"""
    }
}
