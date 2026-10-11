package com.toolstack.io.domain.ai

import com.toolstack.io.domain.calculator.RecipeScalerCalculator

/**
 * Tuning constants for the AI recipe import feature
 * (see docs/recipe-scaler-ai-import-plan.md).
 *
 * Pure Kotlin — no Firebase or Android imports. The Firebase-facing code lives
 * in `data/ai`; Phase 5's Remote Config values default to what's defined here.
 */
object AiImportConfig {

    /** Gemini model used for imports. Benchmark "gemini-3.5-flash-lite" before launch. */
    const val MODEL = "gemini-3.8-flash"

    /** Lifetime AI imports granted to a free user. */
    const val FREE_LIFETIME_LIMIT = 2

    /** Hidden per-day cap for premium users — cost guardrail, not advertised. */
    const val PREMIUM_DAILY_CAP = 30

    /** Longest image edge sent to the model; larger images are downscaled first. */
    const val MAX_IMAGE_EDGE_PX = 1600

    /** JPEG compression quality for the uploaded image. */
    const val JPEG_QUALITY = 80

    /** Hard timeout for a single import request. */
    const val REQUEST_TIMEOUT_MS = 30_000L

    /** Maximum ingredient rows accepted from a single response. */
    const val MAX_INGREDIENTS = 50

    /** Field length caps applied by [AiRecipeMapper]. */
    const val MAX_NAME_LENGTH = 120
    const val MAX_QTY_LENGTH = 20

    /** Canonical unit keys exposed to the model as the response enum. */
    val ALLOWED_UNITS: List<String> = RecipeScalerCalculator.ALL_UNITS.map { it.first }

    /** Prompt sent as the system instruction on every import request. */
    val SYSTEM_PROMPT = """
        You extract the ingredient list from a photo of a recipe and reply with JSON only.

        Rules:
        - Extract only what is visible in the image. Never invent or guess quantities, units, or names.
        - Write quantities as ASCII text: fractions as "1/2" or "1 1/2" (never a single unicode glyph), decimals as "0.5".
        - "unit" must be one of the allowed enum values. If the recipe uses a unit not in the enum, use "none" and keep the original unit word at the start of "name".
        - Omit "qty" and "unit" when the ingredient has none (e.g. "salt to taste") rather than guessing.
        - "name" is the ingredient name only: no quantities, units, section headers ("For the sauce:"), bullet markers, or list numbers. Short preparation notes may stay in "name" (e.g. "butter, melted").
        - Ignore instructions, steps, and any other non-ingredient text in the image.
        - Set "title" and "servings" only when they are visible in the image.
        - If the image has no readable ingredient list, return an empty "ingredients" array.
    """.trimIndent()
}
