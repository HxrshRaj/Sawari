package com.hxrsh.sawari.domain

/** Everything that can go wrong talking to the backend, already mapped away from HTTP/IO exceptions. */
sealed interface AppError {
    data class Network(val cause: Throwable? = null) : AppError
    data class Unauthorized(val detail: String? = null) : AppError
    data class Conflict(val detail: String) : AppError
    data class Validation(val detail: String) : AppError
    data class NotFound(val detail: String) : AppError
    data class Server(val code: Int, val detail: String? = null) : AppError
    data class Unknown(val cause: Throwable? = null) : AppError
}

fun AppError.userMessage(): String = when (this) {
    is AppError.Network -> "Can't reach Sawari. Check your connection."
    is AppError.Unauthorized -> detail ?: "Your session expired. Please sign in again."
    is AppError.Conflict -> detail
    is AppError.Validation -> detail
    is AppError.NotFound -> detail
    is AppError.Server -> "Server error ($code). Please try again."
    is AppError.Unknown -> "Something went wrong."
}

sealed interface AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>
}

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(data))
    is AppResult.Failure -> this
}

inline fun <T> AppResult<T>.onSuccess(block: (T) -> Unit): AppResult<T> {
    if (this is AppResult.Success) block(data)
    return this
}

inline fun <T> AppResult<T>.onFailure(block: (AppError) -> Unit): AppResult<T> {
    if (this is AppResult.Failure) block(error)
    return this
}

fun <T> AppResult<T>.errorOrNull(): AppError? = (this as? AppResult.Failure)?.error
