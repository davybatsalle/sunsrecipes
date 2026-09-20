package com.sunrecipes.app.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import android.util.Base64
import android.util.JsonReader
import java.io.File
import java.io.InputStreamReader
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

    suspend fun save(recipe: RecipeEntity) = dao.insert(recipe.withFingerprint())

    suspend fun update(recipe: RecipeEntity) = dao.update(recipe.withFingerprint())

    suspend fun delete(recipe: RecipeEntity) = dao.delete(recipe)

    suspend fun exportTo(uri: Uri) = withContext(Dispatchers.IO) {
        val documentUri = prepareBackupDocument(uri)
        val recipes = dao.observeAll().firstValue()
        val output = context.contentResolver.openOutputStream(documentUri)
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

    private fun prepareBackupDocument(treeUri: Uri): Uri {
        val resolver = context.contentResolver
        val treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocumentId)
        resolver.query(
            childrenUri,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == BACKUP_FILE_NAME) {
                    val existingUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(idIndex))
                    DocumentsContract.deleteDocument(resolver, existingUri)
                    break
                }
            }
        }
        return DocumentsContract.createDocument(
            resolver,
            DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocumentId),
            "application/json",
            BACKUP_FILE_NAME
        ) ?: error("Impossible de créer le fichier de backup")
    }

    suspend fun importFrom(uri: Uri): Int = importMutex.withLock {
        withContext(Dispatchers.IO) {
        val backupHash = uri.sha256()
        val importedBackups = importPreferences.getStringSet("sha256", emptySet()).orEmpty()
        if (backupHash in importedBackups) return@withContext 0

        var imported = 0
        val knownFingerprints = dao.all().map { it.fingerprint() }.toMutableSet()
        val file = context.contentResolver.openInputStream(uri)
            ?: error("Impossible de lire le fichier de backup")
        val reader = JsonReader(InputStreamReader(file, Charsets.UTF_8))
        reader.use {
                it.beginArray()
                while (it.hasNext()) {
                    it.beginObject()
                    var name = "Recette importée"
                    var nameFrench = ""
                    var family = "autres"
                    var familiesJson = "[]"
                    var ingredientOne = ""
                    var ingredientTwo = ""
                    var ingredientsJson = "[]"
                    var ingredientsFrenchJson = "[]"
                    var searchAliases = ""
                    var ocrText = ""
                    var encodedImage = ""
                    var createdAt = System.currentTimeMillis()
                    while (it.hasNext()) {
                        when (it.nextName()) {
                            "name" -> name = it.nextString()
                            "nameFrench" -> nameFrench = it.nextString()
                            "family" -> family = it.nextString()
                            "familiesJson" -> familiesJson = it.nextString()
                            "ingredientOne" -> ingredientOne = it.nextString()
                            "ingredientTwo" -> ingredientTwo = it.nextString()
                            "ingredientsJson" -> ingredientsJson = it.nextString()
                            "ingredientsFrenchJson" -> ingredientsFrenchJson = it.nextString()
                            "searchAliases" -> searchAliases = it.nextString()
                            "ocrText" -> ocrText = it.nextString()
                            "scanImageBase64" -> encodedImage = it.nextString()
                            "createdAt" -> createdAt = it.nextLong()
                            else -> it.skipValue()
                        }
                    }
                    it.endObject()
                    val fingerprint = fingerprintOf(name, nameFrench, ingredientsJson, ocrText)
                    if (fingerprint in knownFingerprints) continue
                    val imagePath = encodedImage.takeIf(String::isNotBlank)?.let { encoded ->
                        val imageFile = File(context.filesDir, "scans/${System.currentTimeMillis()}-$imported.jpg")
                        imageFile.parentFile?.mkdirs()
                        imageFile.outputStream().use { output ->
                            Base64.decode(encoded, Base64.DEFAULT).inputStream().use { input -> input.copyTo(output) }
                        }
                        imageFile.absolutePath
                    }
                    dao.insert(RecipeEntity(
                        name = name,
                        nameFrench = nameFrench,
                        family = family,
                        familiesJson = familiesJson,
                        ingredientOne = ingredientOne,
                        ingredientTwo = ingredientTwo,
                        ingredientsJson = ingredientsJson,
                        ingredientsFrenchJson = ingredientsFrenchJson,
                        searchAliases = searchAliases,
                        ocrText = ocrText,
                        scanImagePath = imagePath,
                        createdAt = createdAt,
                        recipeFingerprint = fingerprint
                    ))
                    imported++
                    knownFingerprints += fingerprint
                }
                it.endArray()
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

    private fun RecipeEntity.withFingerprint(): RecipeEntity = copy(recipeFingerprint = fingerprint())

    private fun RecipeEntity.fingerprint(): String = fingerprintOf(name, nameFrench, ingredientsJson, ocrText)

    private fun fingerprintOf(name: String, nameFrench: String, ingredientsJson: String, ocrText: String): String {
        val content = listOf(name, nameFrench, ingredientsJson, ocrText)
            .joinToString("\u001f") { normalize(it).trim() }
        return MessageDigest.getInstance("SHA-256")
            .digest(content.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun Uri.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val input = context.contentResolver.openInputStream(this)
            ?: error("Impossible de lire le fichier de backup")
        input.use { stream ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var count = stream.read(buffer)
            while (count >= 0) {
                if (count > 0) digest.update(buffer, 0, count)
                count = stream.read(buffer)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private companion object {
        const val BACKUP_FILE_NAME = "sun-recipes-backup.json"
    }
}
