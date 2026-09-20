package com.sunrecipes.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sunrecipes.app.data.RecipeEntity
import com.sunrecipes.app.data.RecipeRepository
import com.sunrecipes.app.ocr.OcrAnalyzer
import com.sunrecipes.app.ocr.RecipeOcrParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class RecipeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = RecipeRepository(application)
    private val ocrParser = RecipeOcrParser(application)
    private val query = MutableStateFlow("")
    val searchQuery: StateFlow<String> = query
    val recipes: StateFlow<List<RecipeEntity>> = query.flatMapLatest(repository::observeRecipes)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val isScanning = MutableStateFlow(false)
    val scanMessage = MutableStateFlow<String?>(null)

    fun updateQuery(value: String) { query.value = value }

    fun processScan(file: File) = viewModelScope.launch {
        isScanning.value = true
        scanMessage.value = "Lecture du texte…"
        val text = OcrAnalyzer.recognize(file)
        if (text.isBlank()) {
            scanMessage.value = "Aucun texte détecté. Réessayez avec une image plus nette."
        } else {
            val saved = ocrParser.parse(text, file.readBytes())
            saved.forEach(repository::save)
            scanMessage.value = "${saved.size} recette${if (saved.size > 1) "s" else ""} ajoutée${if (saved.size > 1) "s" else ""}."
        }
        isScanning.value = false
    }

    fun clearMessage() { scanMessage.value = null }
    fun delete(recipe: RecipeEntity) = viewModelScope.launch { repository.delete(recipe) }
    fun export(uri: android.net.Uri) = viewModelScope.launch { repository.exportTo(uri) }
}
