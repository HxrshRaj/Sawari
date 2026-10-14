package com.hxrsh.sawari.data.remote

import com.hxrsh.sawari.domain.AppError
import com.hxrsh.sawari.domain.AppResult
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import retrofit2.HttpException
import java.io.IOException

private val errorJson = Json { ignoreUnknownKeys = true; isLenient = true }

/** Runs a Retrofit call and maps every failure mode to an [AppError]. Cancellation is never swallowed. */
suspend fun <T> apiCall(block: suspend () -> T): AppResult<T> = try {
    AppResult.Success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: HttpException) {
    AppResult.Failure(e.toAppError())
} catch (e: IOException) {
    AppResult.Failure(AppError.Network(e))
} catch (e: SerializationException) {
    AppResult.Failure(AppError.Unknown(e))
}

internal fun HttpException.toAppError(): AppError {
    val detail = runCatching { response()?.errorBody()?.string() }.getOrNull()?.let(::extractDetail)
    return when (code()) {
        401 -> AppError.Unauthorized(detail)
        404 -> AppError.NotFound(detail ?: "Not found")
        409 -> AppError.Conflict(detail ?: "That conflicts with an existing booking")
        400, 422 -> AppError.Validation(detail ?: "Invalid request")
        in 500..599 -> AppError.Server(code(), detail)
        else -> AppError.Unknown(this)
    }
}

/** FastAPI errors look like {"detail": "text"} or, for validation, {"detail": [{"msg": "..."}]}. */
internal fun extractDetail(body: String): String? = runCatching {
    when (val detail = (errorJson.parseToJsonElement(body) as? JsonObject)?.get("detail")) {
        is JsonPrimitive -> detail.contentOrNull
        is JsonArray -> detail.firstNotNullOfOrNull { ((it as? JsonObject)?.get("msg") as? JsonPrimitive)?.contentOrNull }
        else -> null
    }
}.getOrNull()
