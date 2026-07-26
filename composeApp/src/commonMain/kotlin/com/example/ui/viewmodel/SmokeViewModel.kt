package com.example.ui.viewmodel

import com.example.data.SmokeRepository
import com.example.data.SmokeSession
import com.example.data.StrainEntry
import com.example.ui.theme.CannabisTheme
import com.example.util.NotificationHelper
import com.russhwolf.settings.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class BackupData(
    val sessions: List<SmokeSession>,
    val strains: List<StrainEntry>,
    val settings: Map<String, String>
)

class SmokeViewModel(
    private val repository: SmokeRepository
) {
    private val viewModelScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val settings: Settings = Settings()
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private val _activeTheme = MutableStateFlow(loadSavedTheme())
    val activeTheme: StateFlow<CannabisTheme> = _activeTheme.asStateFlow()

    private val _language = MutableStateFlow(loadSavedLanguage())
    val language: StateFlow<String> = _language.asStateFlow()

    private val _dayRhythmHours = MutableStateFlow(loadSavedDayRhythm())
    val dayRhythmHours: StateFlow<Int> = _dayRhythmHours.asStateFlow()

    private val _dailyGoalGrams = MutableStateFlow(settings.getFloat("daily_goal_grams", 2.0f).toDouble())
    val dailyGoalGrams: StateFlow<Double> = _dailyGoalGrams.asStateFlow()

    private val _reminderInterval = MutableStateFlow(settings.getInt("reminder_interval_hours", 0))
    val reminderInterval: StateFlow<Int> = _reminderInterval.asStateFlow()

    val allSessions: StateFlow<List<SmokeSession>> = repository.allSessions
        .map { list -> list.filter { !it.isDeleted } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trashedSessions: StateFlow<List<SmokeSession>> = repository.allSessions
        .map { list -> list.filter { it.isDeleted } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allStrains: StateFlow<List<StrainEntry>> = repository.allStrains
        .map { list -> list.filter { !it.isDeleted } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trashedStrains: StateFlow<List<StrainEntry>> = repository.allStrains
        .map { list -> list.filter { it.isDeleted } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        cleanExpiredTrash()
        startReminderTimer()
    }

    private fun startReminderTimer() {
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(60 * 60 * 1000L) // Check every hour
                val interval = _reminderInterval.value
                if (interval > 0) {
                    NotificationHelper.notify(
                        "GreenTracker".translate(_language.value), 
                        "Time to log your session! 🌿".translate(_language.value)
                    )
                }
            }
        }
    }

    fun cleanExpiredTrash() {
        viewModelScope.launch {
            val cutoff = Clock.System.now().toEpochMilliseconds() - 7 * 24 * 60 * 60 * 1000L
            repository.cleanExpiredTrash(cutoff)
        }
    }

    val sessionsToday: StateFlow<List<SmokeSession>> = combine(allSessions, dayRhythmHours) { list, rhythm ->
        try {
            val now = Clock.System.now().toEpochMilliseconds()
            val todayStart = getStartOfLogicalDay(now, rhythm)
            val todayEnd = todayStart + 24 * 60 * 60 * 1000L - 1L
            list.filter { it.timestamp in todayStart..todayEnd }
        } catch (e: Exception) { emptyList() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sessionsYesterday: StateFlow<List<SmokeSession>> = combine(allSessions, dayRhythmHours) { list, rhythm ->
        try {
            val now = Clock.System.now().toEpochMilliseconds()
            val todayStart = getStartOfLogicalDay(now, rhythm)
            val yesterdayStart = todayStart - 24 * 60 * 60 * 1000L
            val yesterdayEnd = todayStart - 1L
            list.filter { it.timestamp in yesterdayStart..yesterdayEnd }
        } catch (e: Exception) { emptyList() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val weeklyStats: StateFlow<List<DayStat>> = combine(allSessions, dayRhythmHours) { list, rhythm ->
        val stats = mutableListOf<DayStat>()
        try {
            val now = Clock.System.now().toEpochMilliseconds()
            val currentLogicalToday = getStartOfLogicalDay(now, rhythm)
            
            val todayInstant = Instant.fromEpochMilliseconds(currentLogicalToday)
            val todayDateTime = todayInstant.toLocalDateTime(TimeZone.currentSystemDefault())
            val dayOfWeek = todayDateTime.dayOfWeek 
            val daysToSubtract = if (dayOfWeek == DayOfWeek.SUNDAY) 6 else dayOfWeek.ordinal 
            
            val mondayStart = currentLogicalToday - daysToSubtract * 24 * 3600 * 1000L
            val labels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
            
            for (i in 0 until 7) {
                val dayStart = mondayStart + i * 24 * 60 * 60 * 1000L
                val dayEnd = dayStart + 24 * 60 * 60 * 1000L - 1L
                val daySessions = list.filter { it.timestamp in dayStart..dayEnd }
                stats.add(DayStat(labels[i], daySessions.size, daySessions.sumOf { it.grams }.roundToDecimals(2), dayStart))
            }
        } catch (e: Exception) {
            val fallbackLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
            fallbackLabels.forEach { stats.add(DayStat(it, 0, 0.0, 0L)) }
        }
        stats
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun logSession(grams: Double, strain: String, notes: String, timestamp: Long = Clock.System.now().toEpochMilliseconds()) {
        repository.insertSession(SmokeSession(timestamp = timestamp, grams = grams, strain = strain, notes = notes))
        NotificationHelper.notify("GreenTracker", "Smoke logged ;) +${grams}g")
    }

    fun updateSession(session: SmokeSession) = repository.updateSession(session)
    fun deleteSession(session: SmokeSession) = repository.deleteSession(session.id)
    fun restoreSession(session: SmokeSession) = repository.restoreSession(session.id)
    fun permanentlyDeleteSession(session: SmokeSession) = repository.permanentlyDeleteSession(session.id)
    fun emptyAllTrash() = repository.emptyTrash()
    fun clearAll() = repository.clearAll()

    fun addStrain(name: String, producer: String, cat: String, thc: Double, cbd: Double, rat: String, notes: String, photo: String) {
        repository.insertStrain(StrainEntry(strainName = name, producerCultivar = producer, category = cat, thcPercentage = thc, cbdPercentage = cbd, rating = rat, notes = notes, photoUri = photo, createdAt = Clock.System.now().toEpochMilliseconds()))
    }
    fun updateStrain(entry: StrainEntry) = repository.updateStrain(entry)
    fun deleteStrain(id: Long) = repository.deleteStrain(id)
    fun restoreStrain(entry: StrainEntry) = repository.restoreStrain(entry.id)
    fun permanentlyDeleteStrain(id: Long) = repository.permanentlyDeleteStrain(id)

    fun setTheme(theme: CannabisTheme) {
        _activeTheme.value = theme
        settings.putString("active_theme_id", theme.id)
    }
    fun setLanguage(lang: String) {
        _language.value = lang
        settings.putString("app_language", lang)
    }
    fun setDayRhythm(hours: Int) {
        _dayRhythmHours.value = hours
        settings.putInt("day_rhythm_hours", hours)
    }
    fun setDailyGoal(grams: Double) {
        _dailyGoalGrams.value = grams
        settings.putFloat("daily_goal_grams", grams.toFloat())
    }
    fun setReminderInterval(hours: Int) {
        _reminderInterval.value = hours
        settings.putInt("reminder_interval_hours", hours)
    }

    private fun loadSavedTheme() = CannabisTheme.entries.find { it.id == settings.getString("active_theme_id", "") } ?: CannabisTheme.CLASSIC_HERBAL
    private fun loadSavedLanguage() = settings.getString("app_language", "en")
    private fun loadSavedDayRhythm() = settings.getInt("day_rhythm_hours", 4)

    fun getStartOfLogicalDay(timestamp: Long, rhythmOffset: Int): Long {
        return try {
            val instant = Instant.fromEpochMilliseconds(timestamp - rhythmOffset * 3600 * 1000L)
            val dateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
            val startOfDay = LocalDateTime(dateTime.year, dateTime.month, dateTime.dayOfMonth, 0, 0, 0, 0)
            startOfDay.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds() + rhythmOffset * 3600 * 1000L
        } catch (e: Exception) { timestamp }
    }

    fun createBackupJson(): String {
        val data = BackupData(
            sessions = repository.allSessions.value,
            strains = repository.allStrains.value,
            settings = mapOf(
                "active_theme_id" to _activeTheme.value.id,
                "app_language" to _language.value,
                "day_rhythm_hours" to _dayRhythmHours.value.toString(),
                "daily_goal_grams" to _dailyGoalGrams.value.toString(),
                "reminder_interval_hours" to _reminderInterval.value.toString()
            )
        )
        return json.encodeToString(data)
    }

    fun importBackupJson(content: String): Boolean {
        return try {
            val data = json.decodeFromString<BackupData>(content)
            repository.importData(data.sessions, data.strains)
            data.settings["active_theme_id"]?.let { id -> CannabisTheme.entries.find { it.id == id }?.let { setTheme(it) } }
            data.settings["app_language"]?.let { setLanguage(it) }
            data.settings["day_rhythm_hours"]?.toIntOrNull()?.let { setDayRhythm(it) }
            data.settings["daily_goal_grams"]?.toDoubleOrNull()?.let { setDailyGoal(it) }
            data.settings["reminder_interval_hours"]?.toIntOrNull()?.let { setReminderInterval(it) }
            true
        } catch (e: Exception) {
            false
        }
    }
}

data class DayStat(val dayLabel: String, val sessionsCount: Int, val totalGrams: Double, val dateTimestamp: Long)

fun Double.roundToDecimals(decimals: Int): Double {
    var multiplier = 1.0
    repeat(decimals) { multiplier *= 10 }
    return (this * multiplier).toInt() / multiplier
}

fun Double.format(decimals: Int): String {
    val rounded = this.roundToDecimals(decimals).toString()
    return if (rounded.contains(".")) {
        val parts = rounded.split(".")
        if (parts[1].length < decimals) {
            rounded + "0".repeat(decimals - parts[1].length)
        } else rounded
    } else {
        rounded + "." + "0".repeat(decimals)
    }
}

fun String.translate(lang: String): String {
    if (lang != "de") return this
    return when (this) {
        "Home" -> "Start"
        "History" -> "Verlauf"
        "Stats" -> "Statistik"
        "Journal" -> "Journal"
        "Strain Journal" -> "Sorten-Journal"
        "Log and review your favorite cultivars" -> "Protokolliere & bewerte deine Lieblingssorten"
        "Search strains or producers..." -> "Suchen..."
        "All" -> "Alle"
        "No strain entries found." -> "Keine Einträge gefunden."
        "Tap + to add your first strain to the journal!" -> "Tippe auf +, um deinen ersten Eintrag hinzuzufügen!"
        "Add Strain Entry" -> "Eintrag hinzufügen"
        "Edit Strain Entry" -> "Eintrag bearbeiten"
        "Add Strain Photo" -> "Foto hinzufügen"
        "Camera" -> "Kamera"
        "Gallery" -> "Galerie"
        "Strain Name *" -> "Name der Sorte *"
        "Producer / Cultivar" -> "Hersteller / Anbauer"
        "Category" -> "Kategorie"
        "Rating" -> "Bewertung"
        "Recommended" -> "Empfohlen"
        "Not Recommended" -> "Nicht empfohlen"
        "Neutral" -> "Neutral"
        "Strain Notes" -> "Notizen"
        "Settings" -> "Einstellungen"
        "Application Settings" -> "Anwendungseinstellungen"
        "Today's Consumption" -> "Heutiger Verbrauch"
        "Sessions" -> "Sitzungen"
        "Logging" -> "Logging"
        "Slide to set log amount" -> "Schieberegler zum Einstellen"
        "Session Dosage:" -> "Sitzungsdosis:"
        "Today's Logs Preview" -> "Vorschau"
        "Today" -> "Heute"
        "Yesterday" -> "Gestern"
        "Earlier Logs" -> "Frühere Logs"
        "Overall Consumption" -> "Gesamtverbrauch"
        "Weekly Trends (Last 7 Days)" -> "Wöchentliche Trends"
        "Summary Stats" -> "Statistiken"
        "Total Sessions" -> "Gesamte Sitzungen"
        "Total Grams" -> "Gesamtgramm"
        "Usage Summary" -> "Nutzungsübersicht"
        "Consumption Analytics" -> "Verbrauchs-Analyse"
        "Daily Average" -> "Tagesdurchschnitt"
        "Amount (Grams):" -> "Menge (Gramm):"
        "Strain / Variety" -> "Sorte / Varietät"
        "Session Notes" -> "Notizen"
        "Save Log" -> "Speichern"
        "Edit Time" -> "Zeit bearbeiten"
        "Time of Session:" -> "Uhrzeit:"
        "Backup & Restore" -> "Backup & Wiederherstellung"
        "Save Backup" -> "Backup speichern"
        "Import Backup" -> "Backup importieren"
        "Trash" -> "Papierkorb"
        "Deleted Session Logs" -> "Gelöschte Sitzungen"
        "Deleted Journal Entries" -> "Gelöschte Journal-Einträge"
        "Deleted items are automatically removed after 7 days." -> "Gelöschte Elemente werden nach 7 Tagen automatisch entfernt."
        "Empty Trash" -> "Papierkorb leeren"
        "Empty All Trash" -> "Gesamten Papierkorb leeren"
        "Restore" -> "Wiederherstellen"
        "Delete Permanently" -> "Endgültig löschen"
        "Delete Entry?" -> "Eintrag löschen?"
        "Do you really want to delete this strain journal entry?" -> "Möchten Sie diesen Journal-Eintrag wirklich löschen?"
        "Delete" -> "Löschen"
        "Cancel" -> "Abbrechen"
        "Language Settings" -> "Spracheinstellungen"
        "Theme Settings" -> "Theme-Einstellungen"
        "App Icon" -> "App-Icon"
        "Daily Dosage Limit" -> "Tageslimit"
        "Day Rhythm" -> "Tagesrhythmus"
        "Start of Day" -> "Tagesbeginn"
        "Configure when your logging day starts (e.g. 4:00 AM)." -> "Konfigurieren Sie, wann Ihr Protokolltag beginnt (z. B. 04:00 Uhr)."
        "Widget Dosage Range Setup" -> "Widget Dosierungs-Setup"
        "Widget Max Dosage" -> "Widget Max. Dosis"
        "Widget Dosage Step" -> "Widget Dosis-Schritt"
        "Push Notification Reminders" -> "Push-Benachrichtigungen"
        "Save" -> "Speichern"
        "Timestamp" -> "Zeitpunkt"
        "Sun" -> "So"
        "Mon" -> "Mo"
        "Tue" -> "Di"
        "Wed" -> "Mi"
        "Thu" -> "Do"
        "Fri" -> "Fr"
        "Sat" -> "Sa"
        "Jan" -> "Jan"
        "Feb" -> "Feb"
        "Mar" -> "Mär"
        "Apr" -> "Apr"
        "May" -> "Mai"
        "Jun" -> "Jun"
        "Jul" -> "Jul"
        "Aug" -> "Aug"
        "Sep" -> "Sep"
        "Oct" -> "Okt"
        "Nov" -> "Nov"
        "Dec" -> "Dez"
        "Midnight Haze" -> "Midnight Haze"
        "Purple Haze" -> "Purple Haze"
        "Golden Mary" -> "Golden Mary"
        "Diskret Dark" -> "Diskret Dark"
        "Diskret White" -> "Diskret White"
        "Pride" -> "Pride"
        "Classic Herbal" -> "Classic Herbal"
        "Time to log your session! 🌿" -> "Zeit, deine Sitzung zu protokollieren! 🌿"
        else -> this
    }
}
