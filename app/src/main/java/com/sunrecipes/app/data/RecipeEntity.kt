package com.sunrecipes.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recipes")
data class RecipeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val family: String,
    val ingredientOne: String,
    val ingredientTwo: String = "",
    val searchAliases: String = "",
    val ocrText: String,
    val scanImage: ByteArray? = null,
    val createdAt: Long = System.currentTimeMillis()
)
