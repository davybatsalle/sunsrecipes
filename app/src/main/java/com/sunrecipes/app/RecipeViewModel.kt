package com.sunrecipes.app

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sunrecipes.app.data.RecipeEntity
import com.sunrecipes.app.ai.GeminiApiKeyStore
import com.sunrecipes.app.ai.GeminiRecipeAnalyzer
import com.sunrecipes.app.data.RecipeRepository
import com.sunrecipes.app.ocr.RecipeOcrParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.io.File
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class RecipeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = RecipeRepository(application)
    private val parser = RecipeOcrParser(application)
    private val geminiKeyStore = GeminiApiKeyStore(application)
    private val geminiAnalyzer = GeminiRecipeAnalyzer(parser)
    private val query = MutableStateFlow("")
    val searchQuery: StateFlow<String> = query
    val recipes: StateFlow<List<RecipeEntity>> = query.flatMapLatest(repository::observeRecipes)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val isScanning = MutableStateFlow(false)
    val scanMessage = MutableStateFlow<String?>(null)
    val pendingRecipes = MutableStateFlow<List<RecipeEntity>>(emptyList())
    val hasGeminiKey = MutableStateFlow(geminiKeyStore.get().isNullOrBlank().not())

    fun updateQuery(value: String) { query.value = value }

    fun processScan(file: File) = viewModelScope.launch {
        isScanning.value = true
        scanMessage.value = "Chargement du modèle IA et analyse de l’image…"
        try {
            val apiKey = geminiKeyStore.get()
            if (apiKey.isNullOrBlank()) {
                scanMessage.value = "Configurez votre clé Gemini dans les paramètres avant de scanner."
                return@launch
            }
            val scanFile = File(getApplication<Application>().filesDir, "scans/${UUID.randomUUID()}.jpg")
            scanFile.parentFile?.mkdirs()
            file.copyTo(scanFile, overwrite = true)
            try {
                pendingRecipes.value = geminiAnalyzer.analyze(scanFile, scanFile.absolutePath, apiKey)
                if (pendingRecipes.value.isEmpty()) {
                    scanMessage.value = "Gemini n’a pas reconnu de recette dans cette image."
                }
            } catch (error: Exception) {
                pendingRecipes.value = emptyList()
                scanMessage.value = "Erreur Gemini : ${error.message ?: "requête refusée"}"
            }
        } finally {
            isScanning.value = false
        }
    }

    fun clearMessage() { scanMessage.value = null }
    fun saveGeminiKey(value: String) {
        geminiKeyStore.save(value.trim())
        hasGeminiKey.value = value.trim().isNotBlank()
    }
    fun clearGeminiKey() {
        geminiKeyStore.clear()
        hasGeminiKey.value = false
    }
    fun confirmRecipes(recipes: List<RecipeEntity>) = viewModelScope.launch {
        recipes.forEach { recipe -> repository.save(recipe) }
        pendingRecipes.value = emptyList()
        scanMessage.value = "${recipes.size} recette${if (recipes.size > 1) "s" else ""} ajoutée${if (recipes.size > 1) "s" else ""}."
    }
    fun updateRecipe(recipe: RecipeEntity) = viewModelScope.launch { repository.update(recipe) }
    fun cropScan(recipe: RecipeEntity, bitmap: Bitmap, onSaved: (RecipeEntity) -> Unit) = viewModelScope.launch {
        runCatching {
            withContext(Dispatchers.IO) {
                val file = File(getApplication<Application>().filesDir, "scans/${UUID.randomUUID()}-crop.jpg")
                file.parentFile?.mkdirs()
                file.outputStream().use { output ->
                    check(bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output)) { "Impossible d’enregistrer le recadrage" }
                }
                val updated = recipe.copy(scanImagePath = file.absolutePath)
                repository.update(updated)
                updated
            }
        }.onSuccess(onSaved)
            .onFailure { error -> Log.e("SunRecipesCrop", "Recadrage du scan échoué", error) }
    }
    fun cancelPendingScan() { pendingRecipes.value = emptyList() }
    fun delete(recipe: RecipeEntity) = viewModelScope.launch { repository.delete(recipe) }
    fun export(uri: android.net.Uri) = viewModelScope.launch {
        runCatching { repository.exportTo(uri) }
            .onSuccess { scanMessage.value = "Backup créé avec succès." }
            .onFailure { scanMessage.value = "Backup impossible : ${it.message ?: "erreur d’écriture"}" }
    }
    fun importRecipes(uri: android.net.Uri) = viewModelScope.launch {
        runCatching { repository.importFrom(uri) }
            .onSuccess {
                scanMessage.value = if (it == 0) {
                    "Ce backup a déjà été importé."
                } else {
                    "$it recette${if (it > 1) "s" else ""} importée${if (it > 1) "s" else ""}."
                }
            }
            .onFailure {
                Log.e("SunRecipesImport", "Import du backup échoué", it)
                scanMessage.value = "Import impossible : ${it.message ?: "fichier invalide"}"
            }
    }
}
