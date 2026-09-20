package com.sunrecipes.app.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import android.util.Base64
import java.io.File
import java.security.MessageDigest
import java.text.Normalizer
import org.json.JSONArray
import org.json.JSONObject

class RecipeRepository(private val context: Context) {
    private val dao = RecipeDatabase.get(context).recipeDao()
    private val importPreferences = context.getSharedPreferences("recipe_imports", Context.MODE_PRIVATE)
    private val importMutex = Mutex()

    fun observeRecipes(query: String): Flow<List<RecipeEntity>> =
        if (query.isBlank()) dao.observeAll() else dao.search(normalize(query.trim()))

    suspend fun save(recipe: RecipeEntity) = dao.insert(recipe)

    suspend fun update(recipe: RecipeEntity) = dao.update(recipe)

    suspend fun delete(recipe: RecipeEntity) = dao.delete(recipe)

    suspend fun exportTo(uri: Uri) = withContext(Dispatchers.IO) {
        val recipes = dao.observeAll().firstValue()
        val output = context.contentResolver.openOutputStream(uri)
            ?: error("Impossible d’ouvrir le fichier de destination")
        output.bufferedWriter().use { writer ->
            writer.write("[\n")
            recipes.forEachIndexed { index, recipe ->
                val image = recipe.scanImagePath?.let { path ->
                    File(path).takeIf(File::isFile)?.readBytes()?.let { Base64.encodeToString(it, Base64.NO_WRAP) }
                } ?: ""
                if (index > 0) writer.write(",\n")
                writer.write("  {\"name\":\"${recipe.name.jsonEscaped()}\",\"nameFrench\":\"${recipe.nameFrench.jsonEscaped()}\",\"family\":\"${recipe.family.jsonEscaped()}\",\"familiesJson\":\"${recipe.familiesJson.jsonEscaped()}\",\"ingredientOne\":\"${recipe.ingredientOne.jsonEscaped()}\",\"ingredientTwo\":\"${recipe.ingredientTwo.jsonEscaped()}\",\"ingredientsJson\":\"${recipe.ingredientsJson.jsonEscaped()}\",\"ingredientsFrenchJson\":\"${recipe.ingredientsFrenchJson.jsonEscaped()}\",\"searchAliases\":\"${recipe.searchAliases.jsonEscaped()}\",\"ocrText\":\"${recipe.ocrText.jsonEscaped()}\",\"scanImageBase64\":\"$image\",\"createdAt\":${recipe.createdAt}}")
            }
            writer.write("\n]")
        }
    }

    suspend fun importFrom(uri: Uri): Int = importMutex.withLock {
        withContext(Dispatchers.IO) {
        val backupBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Impossible de lire le fichier de backup")
        val backupHash = backupBytes.sha256()
        val importedBackups = importPreferences.getStringSet("sha256", emptySet()).orEmpty()
        if (backupHash in importedBackups) return@withContext 0

        val content = backupBytes.toString(Charsets.UTF_8)
        var imported = 0
        var lineIndex = 0
        content.lineSequence().forEach { rawLine ->
            val line = rawLine.trim().removePrefix("\uFEFF")
            if (!line.startsWith("{")) return@forEach
            val item = JSONObject(line.removeSuffix(",").trim())
            val imagePath = item.optString("scanImageBase64").takeIf(String::isNotBlank)?.let { encoded ->
                val file = File(context.filesDir, "scans/${System.currentTimeMillis()}-$lineIndex.jpg")
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
            lineIndex++
        }
        if (imported == 0) error("Le fichier ne contient aucune recette compatible")
        importPreferences.edit()
            .putStringSet("sha256", importedBackups + backupHash)
            .apply()
        imported
        }
    }

    private suspend fun <T> Flow<T>.firstValue(): T = first()

    private fun String.jsonEscaped() = replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")

    private fun normalize(value: String): String = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
        .replace("\\p{Mn}+".toRegex(), "")

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString("") { byte -> "%02x".format(byte) }
}
