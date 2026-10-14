package com.hxrsh.sawari.di

import android.content.Context
import androidx.room.Room
import com.hxrsh.sawari.BuildConfig
import com.hxrsh.sawari.data.local.BookingDao
import com.hxrsh.sawari.data.local.CarDao
import com.hxrsh.sawari.data.local.DataStoreSessionStore
import com.hxrsh.sawari.data.local.SawariDatabase
import com.hxrsh.sawari.data.local.SessionStore
import com.hxrsh.sawari.data.local.token
import com.hxrsh.sawari.data.remote.AuthInterceptor
import com.hxrsh.sawari.data.remote.RetryInterceptor
import com.hxrsh.sawari.data.remote.SawariApi
import com.hxrsh.sawari.data.repository.AuthRepositoryImpl
import com.hxrsh.sawari.data.repository.BookingRepositoryImpl
import com.hxrsh.sawari.data.repository.CarRepositoryImpl
import com.hxrsh.sawari.domain.repository.AuthRepository
import com.hxrsh.sawari.domain.repository.BookingRepository
import com.hxrsh.sawari.domain.repository.CarRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.time.Clock
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides @Singleton
    fun json(): Json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Provides @Singleton
    fun okHttp(sessions: SessionStore): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS) // generous: Render's free tier cold-starts slowly
        .writeTimeout(15, TimeUnit.SECONDS)
        .addInterceptor(AuthInterceptor { runBlocking { sessions.token() } })
        .addInterceptor(RetryInterceptor())
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            }
        }
        .build()

    @Provides @Singleton
    fun retrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides @Singleton
    fun api(retrofit: Retrofit): SawariApi = retrofit.create(SawariApi::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun database(@ApplicationContext context: Context): SawariDatabase =
        Room.databaseBuilder(context, SawariDatabase::class.java, "sawari.db").build()

    @Provides fun carDao(db: SawariDatabase): CarDao = db.carDao()
    @Provides fun bookingDao(db: SawariDatabase): BookingDao = db.bookingDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun sessionStore(impl: DataStoreSessionStore): SessionStore
    @Binds abstract fun auth(impl: AuthRepositoryImpl): AuthRepository
    @Binds abstract fun cars(impl: CarRepositoryImpl): CarRepository
    @Binds abstract fun bookings(impl: BookingRepositoryImpl): BookingRepository
}

@Module
@InstallIn(SingletonComponent::class)
object ClockModule {
    @Provides fun clock(): Clock = Clock.systemDefaultZone()
}
