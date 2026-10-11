package com.toolstack.io.domain.ai

import kotlinx.serialization.Serializable

/**
 * The parsed JSON body returned by the model for an AI import.
 * Field names mirror the response schema built in FirebaseAiRecipeImporter;
 * instructions are intentionally out of scope for v1.
 */
@Serializable
data class AiRecipeResult(
    val title: String? = null,
    val servings: String? = null,
    val ingredients: List<AiIngredient> = emptyList()
)

@Serializable
data class AiIngredient(
    val qty: String? = null,
    val unit: String? = null,
    val name: String? = null
)
