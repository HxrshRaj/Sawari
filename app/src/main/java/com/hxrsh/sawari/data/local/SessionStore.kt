package com.hxrsh.sawari.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hxrsh.sawari.domain.model.Session
import com.hxrsh.sawari.domain.model.User
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

interface SessionStore {
    val session: Flow<Session?>
    suspend fun save(session: Session)
    suspend fun clear()
}

suspend fun SessionStore.token(): String? = session.first()?.token

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "session")

@Singleton
class DataStoreSessionStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : SessionStore {
    private val token = stringPreferencesKey("token")
    private val userId = intPreferencesKey("user_id")
    private val name = stringPreferencesKey("name")
    private val email = stringPreferencesKey("email")

    override val session: Flow<Session?> = context.sessionDataStore.data.map { p ->
        val t = p[token]
        val id = p[userId]
        if (t == null || id == null) null
        else Session(t, User(id, p[name].orEmpty(), p[email].orEmpty()))
    }

    override suspend fun save(session: Session) {
        context.sessionDataStore.edit {
            it[token] = session.token
            it[userId] = session.user.id
            it[name] = session.user.name
            it[email] = session.user.email
        }
    }

    override suspend fun clear() {
        context.sessionDataStore.edit { it.clear() }
    }
}
