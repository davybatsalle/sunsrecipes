package com.sunrecipes.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recipes")
data class RecipeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val nameFrench: String = "",
    val family: String,
    val familiesJson: String = "[]",
    val ingredientOne: String,
    val ingredientTwo: String = "",
    val ingredientsJson: String = "[]",
    val ingredientsFrenchJson: String = "[]",
    val searchAliases: String = "",
    val ocrText: String,
    val scanImagePath: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
