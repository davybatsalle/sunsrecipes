package com.sunrecipes.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Update
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeDao {
    @Query("SELECT * FROM recipes ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<RecipeEntity>>

    @Query("SELECT * FROM recipes")
    suspend fun all(): List<RecipeEntity>

    @Query("SELECT * FROM recipes WHERE name LIKE '%' || :term || '%' OR family LIKE '%' || :term || '%' OR familiesJson LIKE '%' || :term || '%' OR ingredientsJson LIKE '%' || :term || '%' OR ingredientsFrenchJson LIKE '%' || :term || '%' OR searchAliases LIKE '%' || :term || '%' ORDER BY name COLLATE NOCASE ASC")
    fun search(term: String): Flow<List<RecipeEntity>>

    @Insert
    suspend fun insert(recipe: RecipeEntity): Long

    @Update
    suspend fun update(recipe: RecipeEntity)

    @Delete
    suspend fun delete(recipe: RecipeEntity)
}
