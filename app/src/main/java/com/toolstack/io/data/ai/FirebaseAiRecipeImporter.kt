package com.toolstack.io.data.ai

import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.ContentBlockedException
import com.google.firebase.ai.type.FinishReason
import com.google.firebase.ai.type.FirebaseAIException
import com.google.firebase.ai.type.GenerateContentResponse
import com.google.firebase.ai.type.PromptBlockedException
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.ai.type.RequestTimeoutException
import com.google.firebase.ai.type.ResponseStoppedException
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.ServerException
import com.google.firebase.ai.type.ThinkingLevel
import com.google.firebase.ai.type.UnknownException
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.thinkingConfig
import com.toolstack.io.BuildConfig
import com.toolstack.io.domain.ai.AiImportConfig
import com.toolstack.io.domain.ai.AiRecipeMapper
import com.toolstack.io.domain.ai.AiRecipeResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firebase AI Logic implementation of [AiRecipeImporter] using the Gemini
 * Developer API backend.
 *
 * The only file that imports `com.google.firebase.ai` — swapping providers or
 * faking imports in ViewModel tests never touches Firebase types. Logs nothing
 * outside debug builds.
 */
@Singleton
class FirebaseAiRecipeImporter @Inject constructor() : AiRecipeImporter {

    private val json = Json { ignoreUnknownKeys = true }

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = AiImportConfig.MODEL,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = RECIPE_SCHEMA
                thinkingConfig = thinkingConfig {
                    thinkingLevel = ThinkingLevel.LOW
                }
            },
            systemInstruction = content { text(AiImportConfig.SYSTEM_PROMPT) }
        )
    }

    override suspend fun import(jpeg: ByteArray): AiImportResult {
        val totalStartedAt = SystemClock.elapsedRealtime()
        val decodeStartedAt = SystemClock.elapsedRealtime()
        val bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
        val decodeMs = SystemClock.elapsedRealtime() - decodeStartedAt
        if (bitmap == null) {
            val cause = IllegalArgumentException("Undecodable JPEG (${jpeg.size} bytes)")
            logFailure(cause)
            logTiming(totalStartedAt, decodeMs, null, null, jpeg.size, null, cause.javaClass.simpleName)
            return AiImportResult.Failed(cause)
        }
        val requestStartedAt = SystemClock.elapsedRealtime()
        return try {
            val response = withTimeout(AiImportConfig.REQUEST_TIMEOUT_MS) {
                model.generateContent(
                    content {
                        image(bitmap)
                        text(USER_PROMPT)
                    }
                )
            }
            val requestMs = SystemClock.elapsedRealtime() - requestStartedAt
            val mappingStartedAt = SystemClock.elapsedRealtime()
            val result = mapResponse(response)
            logTiming(
                totalStartedAt,
                decodeMs,
                requestMs,
                SystemClock.elapsedRealtime() - mappingStartedAt,
                jpeg.size,
                "${bitmap.width}x${bitmap.height}",
                result.javaClass.simpleName
            )
            result
        } catch (e: TimeoutCancellationException) {
            logTiming(
                totalStartedAt,
                decodeMs,
                SystemClock.elapsedRealtime() - requestStartedAt,
                null,
                jpeg.size,
                "${bitmap.width}x${bitmap.height}",
                "Timeout"
            )
            AiImportResult.NetworkError
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logFailure(e)
            val result = mapException(e)
            logTiming(
                totalStartedAt,
                decodeMs,
                SystemClock.elapsedRealtime() - requestStartedAt,
                null,
                jpeg.size,
                "${bitmap.width}x${bitmap.height}",
                result.javaClass.simpleName
            )
            result
        }.logResult()
    }

    private fun mapResponse(response: GenerateContentResponse): AiImportResult {
        val finishReason = response.candidates.firstOrNull()?.finishReason
        if (finishReason != null && finishReason != FinishReason.STOP) {
            if (BuildConfig.DEBUG) Log.w(TAG, "unexpected finishReason: $finishReason")
            return if (finishReason in BLOCKED_FINISH_REASONS) {
                AiImportResult.Blocked
            } else {
                AiImportResult.Failed()
            }
        }
        val text = response.text ?: return AiImportResult.NothingFound
        val recipe = try {
            json.decodeFromString<AiRecipeResult>(text)
        } catch (e: Exception) {
            logFailure(e)
            return AiImportResult.Failed(e)
        }
        val items = AiRecipeMapper.map(recipe)
        return if (items.isEmpty()) {
            AiImportResult.NothingFound
        } else {
            AiImportResult.Success(recipe, items)
        }.logResult()
    }

    private fun mapException(e: Exception): AiImportResult = when (e) {
        is PromptBlockedException, is ContentBlockedException -> AiImportResult.Blocked
        is ResponseStoppedException -> {
            val reason = e.response.candidates.firstOrNull()?.finishReason
            if (reason in BLOCKED_FINISH_REASONS) AiImportResult.Blocked else AiImportResult.Failed(e)
        }
        is QuotaExceededException -> AiImportResult.RateLimited
        // An App Check attestation failure surfaces as a ServerException but
        // is a config problem, not connectivity — report as Failed so the UI
        // doesn't claim "check your internet".
        is ServerException -> if (e.message?.contains("app check", ignoreCase = true) == true) {
            AiImportResult.Failed(e)
        } else {
            AiImportResult.NetworkError
        }
        is RequestTimeoutException, is IOException -> AiImportResult.NetworkError
        else -> if (e.cause is IOException) AiImportResult.NetworkError else AiImportResult.Failed(e)
    }

    private fun logTiming(
        totalStartedAt: Long,
        decodeMs: Long,
        requestMs: Long?,
        mappingMs: Long?,
        jpegBytes: Int,
        dimensions: String?,
        result: String
    ) {
        if (BuildConfig.DEBUG) {
            Log.d(
                TAG,
                "AI timing: decode=${decodeMs}ms request=${requestMs ?: "n/a"}ms " +
                    "mapping=${mappingMs ?: "n/a"}ms total=${SystemClock.elapsedRealtime() - totalStartedAt}ms " +
                    "dimensions=${dimensions ?: "unknown"} bytes=$jpegBytes result=$result model=${AiImportConfig.MODEL}"
            )
        }
    }

    private fun logFailure(e: Throwable) {
        if (BuildConfig.DEBUG) Log.w(TAG, "AI import failed", e)
    }

    /** Debug-logs any non-success result so silent failure modes stay visible. */
    private fun AiImportResult.logResult(): AiImportResult {
        if (BuildConfig.DEBUG && this !is AiImportResult.Success) {
            Log.w(TAG, "AI import result: $this")
        }
        return this
    }

    private companion object {
        const val TAG = "AiRecipeImporter"
        const val USER_PROMPT = "Extract the ingredient list from this recipe image."

        val BLOCKED_FINISH_REASONS = setOf(
            FinishReason.SAFETY,
            FinishReason.IMAGE_SAFETY,
            FinishReason.BLOCKLIST,
            FinishReason.PROHIBITED_CONTENT,
            FinishReason.IMAGE_PROHIBITED_CONTENT,
            FinishReason.SPII
        )

        val RECIPE_SCHEMA: Schema = Schema.obj(
            properties = mapOf(
                "title" to Schema.string(description = "Recipe title"),
                "servings" to Schema.string(
                    description = "Serving count or yield as written (e.g. \"4\" or \"12 cookies\")"
                ),
                "ingredients" to Schema.array(
                    Schema.obj(
                        properties = mapOf(
                            "qty" to Schema.string(
                                description = "Quantity as ASCII text: \"2\", \"1/2\", \"1 1/2\", \"0.5\""
                            ),
                            "unit" to Schema.enumeration(AiImportConfig.ALLOWED_UNITS),
                            "name" to Schema.string(
                                description = "Ingredient name only — no quantity, unit, or section headers"
                            )
                        ),
                        optionalProperties = listOf("qty", "unit")
                    ),
                    description = "One entry per ingredient line"
                )
            ),
            optionalProperties = listOf("title", "servings")
        )
    }
}
