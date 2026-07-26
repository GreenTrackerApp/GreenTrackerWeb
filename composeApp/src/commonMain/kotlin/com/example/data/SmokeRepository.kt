package com.example.data

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.datetime.Clock

class SmokeRepository {
    private val settings = Settings()
    private val json = Json { ignoreUnknownKeys = true }

    private val _allSessions = MutableStateFlow<List<SmokeSession>>(loadSessions())
    val allSessions: StateFlow<List<SmokeSession>> = _allSessions.asStateFlow()

    private val _allStrains = MutableStateFlow<List<StrainEntry>>(loadStrains())
    val allStrains: StateFlow<List<StrainEntry>> = _allStrains.asStateFlow()

    private fun loadSessions(): List<SmokeSession> {
        val data = settings.getString("sessions_json", "[]")
        return try { json.decodeFromString<List<SmokeSession>>(data) } catch (e: Exception) { emptyList() }
    }

    private fun loadStrains(): List<StrainEntry> {
        val data = settings.getString("strains_json", "[]")
        return try { json.decodeFromString<List<StrainEntry>>(data) } catch (e: Exception) { emptyList() }
    }

    private fun saveSessions(sessions: List<SmokeSession>) {
        settings.putString("sessions_json", json.encodeToString(sessions))
        _allSessions.value = sessions
    }

    private fun saveStrains(strains: List<StrainEntry>) {
        settings.putString("strains_json", json.encodeToString(strains))
        _allStrains.value = strains
    }

    fun insertSession(session: SmokeSession) {
        val current = loadSessions().toMutableList()
        val newSession = if (session.id == 0L) session.copy(id = Clock.System.now().toEpochMilliseconds()) else session
        current.add(newSession)
        saveSessions(current)
    }

    fun updateSession(session: SmokeSession) {
        val current = loadSessions().map { if (it.id == session.id) session else it }
        saveSessions(current)
    }

    fun deleteSession(id: Long) {
        val current = loadSessions().map { if (it.id == id) it.copy(isDeleted = true, deletedAt = Clock.System.now().toEpochMilliseconds()) else it }
        saveSessions(current)
    }

    fun restoreSession(id: Long) {
        val current = loadSessions().map { if (it.id == id) it.copy(isDeleted = false, deletedAt = null) else it }
        saveSessions(current)
    }

    fun permanentlyDeleteSession(id: Long) {
        val current = loadSessions().filter { it.id != id }
        saveSessions(current)
    }

    fun insertStrain(strain: StrainEntry) {
        val current = loadStrains().toMutableList()
        val newStrain = if (strain.id == 0L) strain.copy(id = Clock.System.now().toEpochMilliseconds()) else strain
        current.add(newStrain)
        saveStrains(current)
    }

    fun updateStrain(strain: StrainEntry) {
        val current = loadStrains().map { if (it.id == strain.id) strain else it }
        saveStrains(current)
    }

    fun deleteStrain(id: Long) {
        val current = loadStrains().map { if (it.id == id) it.copy(isDeleted = true, deletedAt = Clock.System.now().toEpochMilliseconds()) else it }
        saveStrains(current)
    }

    fun restoreStrain(id: Long) {
        val current = loadStrains().map { if (it.id == id) it.copy(isDeleted = false, deletedAt = null) else it }
        saveStrains(current)
    }

    fun permanentlyDeleteStrain(id: Long) {
        val current = loadStrains().filter { it.id != id }
        saveStrains(current)
    }

    fun cleanExpiredTrash(cutoff: Long) {
        val sessions = loadSessions().filter { !it.isDeleted || (it.deletedAt ?: 0L) > cutoff }
        val strains = loadStrains().filter { !it.isDeleted || (it.deletedAt ?: 0L) > cutoff }
        saveSessions(sessions)
        saveStrains(strains)
    }

    fun emptyTrash() {
        val sessions = loadSessions().filter { !it.isDeleted }
        val strains = loadStrains().filter { !it.isDeleted }
        saveSessions(sessions)
        saveStrains(strains)
    }

    fun clearAll() {
        saveSessions(emptyList())
        saveStrains(emptyList())
    }

    fun importData(sessions: List<SmokeSession>, strains: List<StrainEntry>) {
        val existingSessions = loadSessions()
        val existingStrains = loadStrains()
        val newSessions = (existingSessions + sessions).distinctBy { "${it.timestamp}|${it.grams}|${it.strain.lowercase().trim()}" }
        val newStrains = (existingStrains + strains).distinctBy { "${it.strainName.lowercase().trim()}|${it.producerCultivar.lowercase().trim()}" }
        saveSessions(newSessions)
        saveStrains(newStrains)
    }
}
