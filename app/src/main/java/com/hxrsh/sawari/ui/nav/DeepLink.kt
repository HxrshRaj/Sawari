package com.hxrsh.sawari.ui.nav

import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

/** Where a deep link wants to go. Pure Kotlin so the parser is unit-testable on the JVM. */
sealed interface DeepLink {
    data class OpenCar(val carId: Int) : DeepLink
    data object OpenBookings : DeepLink
    /** A link that looks like ours but is malformed (bad id, unknown path). The UI shows an error instead of crashing. */
    data class Invalid(val reason: String) : DeepLink
}

object DeepLinkParser {
    const val SCHEME = "sawari"

    /**
     * Host for the https equivalent. It is declared in the manifest as a plain (unverified) link:
     * verified App Links need https://<host>/.well-known/assetlinks.json, which is NOT set up.
     */
    const val WEB_HOST = "sawari-api-1krd.onrender.com"
    const val WEB_PATH_PREFIX = "/app"

    fun bookingsUri() = "$SCHEME://bookings"
    fun carUri(id: Int) = "$SCHEME://car/$id"

    /** Returns null when [raw] is not a Sawari link at all (a normal launch); [DeepLink.Invalid] when it is but is broken. */
    fun parse(raw: String?): DeepLink? {
        if (raw.isNullOrBlank()) return null
        val uri = try {
            URI(raw.trim())
        } catch (_: URISyntaxException) {
            return null
        }
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
        val host = uri.host?.lowercase(Locale.ROOT)
        val segments = uri.path.orEmpty().split('/').filter { it.isNotEmpty() }
        return when (scheme) {
            SCHEME -> route(host, segments)
            "https" -> {
                if (host != WEB_HOST || segments.firstOrNull() != WEB_PATH_PREFIX.trim('/')) return null
                route(segments.getOrNull(1), segments.drop(2))
            }
            else -> null
        }
    }

    private fun route(name: String?, args: List<String>): DeepLink = when (name) {
        "bookings" -> if (args.isEmpty()) DeepLink.OpenBookings else DeepLink.Invalid("Unexpected path after bookings")
        "car" -> {
            val id = args.singleOrNull()?.takeIf { it.all(Char::isDigit) }?.toIntOrNull()
            if (id != null && id > 0) DeepLink.OpenCar(id) else DeepLink.Invalid("Car id must be a positive number")
        }
        else -> DeepLink.Invalid("Unknown link")
    }
}
