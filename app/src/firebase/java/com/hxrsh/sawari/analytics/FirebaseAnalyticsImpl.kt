package com.hxrsh.sawari.analytics

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real Firebase Analytics + Crashlytics. If app/google-services.json was not supplied at build time,
 * FirebaseApp never initialises; every call then degrades to Logcat instead of crashing.
 */
@Singleton
class FirebaseAnalyticsImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : Analytics {
    private val ready: Boolean = FirebaseApp.getApps(context).isNotEmpty()

    init {
        if (!ready) Log.w(TAG, "google-services.json missing: Firebase disabled, logging to Logcat only")
    }

    override fun logEvent(name: String, params: Map<String, String>) {
        if (!ready) return Unit.also { Log.d(TAG, "event $name $params") }
        val bundle = Bundle().apply { params.forEach { (k, v) -> putString(k, v) } }
        FirebaseAnalytics.getInstance(context).logEvent(name, bundle)
    }

    override fun setUser(id: String?) {
        if (!ready) return
        FirebaseAnalytics.getInstance(context).setUserId(id)
        FirebaseCrashlytics.getInstance().setUserId(id.orEmpty())
    }

    override fun recordNonFatal(throwable: Throwable) {
        if (!ready) return Unit.also { Log.w(TAG, "non-fatal", throwable) }
        FirebaseCrashlytics.getInstance().recordException(throwable)
    }

    override fun crashForTest() {
        throw RuntimeException("Sawari test crash from the debug menu")
    }

    private companion object { const val TAG = "SawariAnalytics" }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AnalyticsModule {
    @Binds abstract fun analytics(impl: FirebaseAnalyticsImpl): Analytics
}
