package com.campusverse.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.modulePreferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "campus_verse_module_prefs")

/**
 * Manages persistent module-specific pre-screen preferences and first-visit setup completion states.
 */
class ModulePreferencesManager(
    private val context: Context,
    private val dataStore: DataStore<Preferences> = context.modulePreferencesDataStore
) {
    companion object {
        @Volatile
        private var INSTANCE: ModulePreferencesManager? = null

        fun getInstance(context: Context): ModulePreferencesManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ModulePreferencesManager(context.applicationContext).also { INSTANCE = it }
            }
        }

        fun getInstanceOrNull(): ModulePreferencesManager? = INSTANCE
    }

    private fun setupCompletedKey(userId: String, moduleId: String) =
        booleanPreferencesKey("setup_completed_${userId}_${moduleId}")

    private fun prefsDataKey(userId: String, moduleId: String) =
        stringPreferencesKey("prefs_data_${userId}_${moduleId}")

    suspend fun isModuleSetupCompleted(userId: String, moduleId: String): Boolean {
        if (userId.isBlank() || moduleId.isBlank()) return false
        val key = setupCompletedKey(userId, moduleId)
        return try {
            dataStore.data
                .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
                .map { prefs -> prefs[key] ?: false }
                .first()
        } catch (e: Exception) {
            false
        }
    }

    suspend fun setModuleSetupCompleted(userId: String, moduleId: String, completed: Boolean) {
        if (userId.isBlank() || moduleId.isBlank()) return
        val key = setupCompletedKey(userId, moduleId)
        dataStore.edit { prefs ->
            prefs[key] = completed
        }
    }

    suspend fun saveModulePreferences(userId: String, moduleId: String, jsonString: String) {
        if (userId.isBlank() || moduleId.isBlank()) return
        val dataKey = prefsDataKey(userId, moduleId)
        val completedKey = setupCompletedKey(userId, moduleId)
        dataStore.edit { prefs ->
            prefs[dataKey] = jsonString
            prefs[completedKey] = true
        }
    }

    suspend fun getModulePreferences(userId: String, moduleId: String): String? {
        if (userId.isBlank() || moduleId.isBlank()) return null
        val dataKey = prefsDataKey(userId, moduleId)
        return try {
            dataStore.data
                .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
                .map { prefs -> prefs[dataKey] }
                .first()
        } catch (e: Exception) {
            null
        }
    }

    fun getModulePreferencesFlow(userId: String, moduleId: String): Flow<String?> {
        val dataKey = prefsDataKey(userId, moduleId)
        return dataStore.data
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .map { prefs -> prefs[dataKey] }
    }

    suspend fun resetModuleSetup(userId: String, moduleId: String) {
        if (userId.isBlank() || moduleId.isBlank()) return
        val dataKey = prefsDataKey(userId, moduleId)
        val completedKey = setupCompletedKey(userId, moduleId)
        dataStore.edit { prefs ->
            prefs.remove(dataKey)
            prefs.remove(completedKey)
        }
    }

    suspend fun updateAcademicPreferences(
        userId: String,
        degree: String? = null,
        semester: Int? = null,
        branch: String? = null,
        recentGpa: Double? = null
    ) {
        if (userId.isBlank()) return
        val existingJson = getModulePreferences(userId, "student_academics")
        val current = if (existingJson != null) {
            com.campusverse.app.data.model.StudentAcademicPreferences.fromJson(existingJson)
        } else {
            com.campusverse.app.data.model.StudentAcademicPreferences()
        }

        val updated = current.copy(
            degree = degree?.takeIf { it.isNotBlank() } ?: current.degree,
            semester = semester ?: current.semester,
            branch = branch?.takeIf { it.isNotBlank() } ?: current.branch,
            recentGpa = recentGpa ?: current.recentGpa
        )

        saveModulePreferences(userId, "student_academics", updated.toJson())
    }

    suspend fun clearUserData(userId: String) {
        if (userId.isBlank()) return
        dataStore.edit { prefs ->
            val keysToRemove = prefs.asMap().keys.filter { key ->
                key.name.contains("_${userId}_") || key.name.endsWith("_$userId")
            }
            keysToRemove.forEach { key ->
                prefs.remove(key)
            }
        }
    }
}
