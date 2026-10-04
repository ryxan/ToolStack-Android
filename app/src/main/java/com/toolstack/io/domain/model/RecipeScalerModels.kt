package com.toolstack.io.domain.model

data class IngredientItem(
    val id: String,
    val qtyString: String,
    val unit: String,
    val name: String
)

data class UnitOption(
    val value: String,
    val label: String
)

data class ScaledIngredient(
    val id: String,
    val originalQty: Double,
    val scaledQty: Double,
    val unit: String,
    val name: String,
    val displayText: String
)

data class SavedRecipe(
    val name: String,
    val servings: String,
    val ingredients: List<SavedRecipeIngredient>
)

data class SavedRecipeIngredient(
    val qtyString: String,
    val unit: String,
    val name: String
)
