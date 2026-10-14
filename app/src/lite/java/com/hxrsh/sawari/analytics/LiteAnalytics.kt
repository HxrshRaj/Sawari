package com.hxrsh.sawari.analytics

import android.util.Log
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/** No Firebase in this flavour: events go to Logcat only. */
@Singleton
class LogAnalytics @Inject constructor() : Analytics {
    override fun logEvent(name: String, params: Map<String, String>) {
        Log.d(TAG, "event $name $params")
    }

    override fun setUser(id: String?) = Unit

    override fun recordNonFatal(throwable: Throwable) {
        Log.w(TAG, "non-fatal", throwable)
    }

    override fun crashForTest() {
        throw RuntimeException("Sawari test crash (lite flavour, no Crashlytics)")
    }

    private companion object { const val TAG = "SawariAnalytics" }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AnalyticsModule {
    @Binds abstract fun analytics(impl: LogAnalytics): Analytics
}
