package com.toolstack.io.data.ai

import com.toolstack.io.domain.ai.AiRecipeResult
import com.toolstack.io.domain.model.IngredientItem

/** Imports a recipe's ingredient list from a JPEG image. */
interface AiRecipeImporter {
    suspend fun import(jpeg: ByteArray): AiImportResult
}

/**
 * Result of a single AI import attempt. A free-import credit is spent only on
 * [Success] — every other variant is refunded by the caller.
 */
sealed interface AiImportResult {

    /** Parsed ingredients, plus the raw recipe fields (title/servings) for prefill. */
    data class Success(
        val recipe: AiRecipeResult,
        val ingredients: List<IngredientItem>
    ) : AiImportResult

    /** Model ran but found no readable ingredients (or returned an empty list). */
    data object NothingFound : AiImportResult

    /** Transport-level failure: no connectivity, timeout, or HTTP 5xx. */
    data object NetworkError : AiImportResult

    /** Blocked by safety filters, either the prompt/image or the response. */
    data object Blocked : AiImportResult

    /** The project's API quota was exhausted — distinct from the app's per-user caps. */
    data object RateLimited : AiImportResult

    /** Any other failure; [cause] is logged in debug builds only. */
    data class Failed(val cause: Throwable? = null) : AiImportResult
}
