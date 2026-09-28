package com.felipeb.nap.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.felipeb.nap.model.SleepSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

private val Context.dataStore by preferencesDataStore(name = "sleep")
private val SESSIONS = stringPreferencesKey("sessions")

/** Guarda as sessões como "inicioMillis,fimMillis;..." — simples e suficiente para o volume do app. */
class SleepRepository(private val context: Context) {

    val sessions: Flow<List<SleepSession>> = context.dataStore.data.map { decode(it[SESSIONS]) }

    suspend fun add(session: SleepSession) = update { it + session }

    suspend fun remove(session: SleepSession) = update { it - session }

    private suspend fun update(transform: (List<SleepSession>) -> List<SleepSession>) {
        context.dataStore.edit { prefs ->
            prefs[SESSIONS] = encode(transform(decode(prefs[SESSIONS])).sortedBy { it.start })
        }
    }

    private fun encode(list: List<SleepSession>) =
        list.joinToString(";") { "${it.start.toEpochMilli()},${it.end.toEpochMilli()}" }

    private fun decode(raw: String?): List<SleepSession> =
        raw.orEmpty().split(";").mapNotNull { entry ->
            val parts = entry.split(",")
            if (parts.size != 2) return@mapNotNull null
            val start = parts[0].toLongOrNull() ?: return@mapNotNull null
            val end = parts[1].toLongOrNull() ?: return@mapNotNull null
            SleepSession(Instant.ofEpochMilli(start), Instant.ofEpochMilli(end))
        }
}
