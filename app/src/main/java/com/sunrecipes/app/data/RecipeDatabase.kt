package com.sunrecipes.app.data

import android.content.Context
import androidx.room.migration.Migration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [RecipeEntity::class], version = 9, exportSchema = false)
abstract class RecipeDatabase : RoomDatabase() {
    abstract fun recipeDao(): RecipeDao

    companion object {
        @Volatile private var instance: RecipeDatabase? = null

        fun get(context: Context): RecipeDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                RecipeDatabase::class.java,
                "sun-recipes.db"
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9).build().also { instance = it }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE recipes ADD COLUMN searchAliases TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE recipes_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        family TEXT NOT NULL,
                        ingredientOne TEXT NOT NULL,
                        ingredientTwo TEXT NOT NULL,
                        searchAliases TEXT NOT NULL,
                        ocrText TEXT NOT NULL,
                        scanImagePath TEXT,
                        createdAt INTEGER NOT NULL
                    )
                """.trimIndent())
                database.execSQL("""
                    INSERT INTO recipes_new (id, name, family, ingredientOne, ingredientTwo, searchAliases, ocrText, createdAt)
                    SELECT id, name, family, ingredientOne, ingredientTwo, searchAliases, ocrText, createdAt FROM recipes
                """.trimIndent())
                database.execSQL("DROP TABLE recipes")
                database.execSQL("ALTER TABLE recipes_new RENAME TO recipes")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE recipes ADD COLUMN ingredientsJson TEXT NOT NULL DEFAULT '[]'")
                database.execSQL("ALTER TABLE recipes ADD COLUMN preparationSteps TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE recipes_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        family TEXT NOT NULL,
                        ingredientOne TEXT NOT NULL,
                        ingredientTwo TEXT NOT NULL,
                        ingredientsJson TEXT NOT NULL,
                        searchAliases TEXT NOT NULL,
                        ocrText TEXT NOT NULL,
                        scanImagePath TEXT,
                        createdAt INTEGER NOT NULL
                    )
                """.trimIndent())
                database.execSQL("""
                    INSERT INTO recipes_new (id, name, family, ingredientOne, ingredientTwo, ingredientsJson, searchAliases, ocrText, scanImagePath, createdAt)
                    SELECT id, name, family, ingredientOne, ingredientTwo, ingredientsJson, searchAliases, ocrText, scanImagePath, createdAt FROM recipes
                """.trimIndent())
                database.execSQL("DROP TABLE recipes")
                database.execSQL("ALTER TABLE recipes_new RENAME TO recipes")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE recipes ADD COLUMN ingredientsFrenchJson TEXT NOT NULL DEFAULT '[]'")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE recipes ADD COLUMN nameFrench TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE recipes ADD COLUMN familiesJson TEXT NOT NULL DEFAULT '[]'")
                database.execSQL("UPDATE recipes SET familiesJson = '[\"' || family || '\"]'")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE recipes ADD COLUMN recipeFingerprint TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}
