package com.handdrive.profiles

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.profileStore: DataStore<Preferences> by preferencesDataStore(name = "handdrive_profiles")

class ProfileRepository(private val context: Context) {

    private object Keys {
        val PROFILES_JSON = stringPreferencesKey("profiles_json")
        val ACTIVE_ID = stringPreferencesKey("active_profile_id")
    }

    val profilesFlow: Flow<List<GameProfile>> = context.profileStore.data.map { prefs ->
        parseProfiles(prefs[Keys.PROFILES_JSON])
    }

    val activeProfileIdFlow: Flow<String?> = context.profileStore.data.map { prefs ->
        prefs[Keys.ACTIVE_ID]
    }

    val activeProfileFlow: Flow<GameProfile?> = context.profileStore.data.map { prefs ->
        val list = parseProfiles(prefs[Keys.PROFILES_JSON])
        val id = prefs[Keys.ACTIVE_ID]
        list.find { it.id == id } ?: list.firstOrNull()
    }

    suspend fun create(name: String): GameProfile {
        val profile = GameProfile.create(name)
        mutate { list -> list + profile }
        setActive(profile.id)
        return profile
    }

    suspend fun update(profile: GameProfile) {
        val updated = profile.copy(updatedAtMs = System.currentTimeMillis())
        mutate { list -> list.map { if (it.id == updated.id) updated else it } }
    }

    suspend fun delete(id: String) {
        mutate { list -> list.filterNot { it.id == id } }
        context.profileStore.edit { prefs ->
            if (prefs[Keys.ACTIVE_ID] == id) {
                val remaining = parseProfiles(prefs[Keys.PROFILES_JSON])
                if (remaining.isNotEmpty()) {
                    prefs[Keys.ACTIVE_ID] = remaining.first().id
                } else {
                    prefs.remove(Keys.ACTIVE_ID)
                }
            }
        }
    }

    suspend fun duplicate(id: String): GameProfile? {
        var copy: GameProfile? = null
        mutate { list ->
            val src = list.find { it.id == id } ?: return@mutate list
            copy = src.copy(
                id = java.util.UUID.randomUUID().toString(),
                name = src.name + " (copy)",
                createdAtMs = System.currentTimeMillis(),
                updatedAtMs = System.currentTimeMillis()
            )
            list + copy!!
        }
        return copy
    }

    suspend fun setActive(id: String) {
        context.profileStore.edit { it[Keys.ACTIVE_ID] = id }
    }

    suspend fun resetCalibration(id: String) {
        mutate { list ->
            list.map {
                if (it.id == id) it.copy(
                    layout = ControlLayout(),
                    updatedAtMs = System.currentTimeMillis()
                ) else it
            }
        }
    }

    private suspend fun mutate(block: (List<GameProfile>) -> List<GameProfile>) {
        context.profileStore.edit { prefs ->
            val current = parseProfiles(prefs[Keys.PROFILES_JSON])
            val next = block(current)
            prefs[Keys.PROFILES_JSON] = serialize(next)
        }
    }

    private fun parseProfiles(json: String?): List<GameProfile> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    add(GameProfile.fromJson(arr.getJSONObject(i)))
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun serialize(list: List<GameProfile>): String {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        return arr.toString()
    }
}
