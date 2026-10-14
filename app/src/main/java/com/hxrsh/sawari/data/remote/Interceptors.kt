package com.hxrsh.sawari.data.remote

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/** Adds the bearer token, if any. Interceptors run on OkHttp's worker threads, so the lookup may block. */
class AuthInterceptor(private val tokenProvider: () -> String?) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokenProvider()
        val request = if (token == null) chain.request()
        else chain.request().newBuilder().header("Authorization", "Bearer $token").build()
        return chain.proceed(request)
    }
}

/**
 * Retries only idempotent GETs, on IO failures and 502/503/504 (Render's free tier answers like that while waking up).
 * POSTs (login, booking, cancel) are never retried: replaying a booking must not be automatic.
 */
class RetryInterceptor(
    private val maxRetries: Int = 2,
    private val backoffMillis: Long = 300,
    private val sleeper: (Long) -> Unit = { Thread.sleep(it) },
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.method != "GET") return chain.proceed(request)
        var attempt = 0
        while (true) {
            try {
                val response = chain.proceed(request)
                if (response.code !in RETRYABLE || attempt >= maxRetries) return response
                response.close()
            } catch (e: IOException) {
                if (attempt >= maxRetries || chain.call().isCanceled()) throw e
            }
            sleeper(backoffMillis shl attempt)
            attempt++
        }
    }

    private companion object {
        val RETRYABLE = 502..504
    }
}
