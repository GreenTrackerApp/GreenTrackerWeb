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

    // Current Theme State
    private val _activeTheme = MutableStateFlow(loadSavedTheme())
    val activeTheme: StateFlow<CannabisTheme> = _activeTheme.asStateFlow()

    // Language State ("en" or "de")
    private val _language = MutableStateFlow(loadSavedLanguage())
    val language: StateFlow<String> = _language.asStateFlow()

    // Day Rhythm State (0-23 hours, default 4 AM)
    private val _dayRhythmHours = MutableStateFlow(loadSavedDayRhythm())
    val dayRhythmHours: StateFlow<Int> = _dayRhythmHours.asStateFlow()

    // Daily Goal
    private val _dailyGoalGrams = MutableStateFlow(settings.getFloat("daily_goal_grams", 2.0f).toDouble())
    val dailyGoalGrams: StateFlow<Double> = _dailyGoalGrams.asStateFlow()

    private val _widgetMaxDosage = MutableStateFlow(settings.getFloat("widget_max_dosage", 2.0f).toDouble())
    val widgetMaxDosage: StateFlow<Double> = _widgetMaxDosage.asStateFlow()

    // Quick Log Amount
    private val _quickLogGrams = MutableStateFlow(settings.getFloat("quick_track_grams", 0.2f).toDouble())
    val quickLogGrams: StateFlow<Double> = _quickLogGrams.asStateFlow()

    // Strain Review Reminder Days
    private val _reviewReminderDays = MutableStateFlow(settings.getInt("review_reminder_days", 7))
    val reviewReminderDays: StateFlow<Int> = _reviewReminderDays.asStateFlow()

    // App Icon Index
    private val _activeAppIconIndex = MutableStateFlow(settings.getInt("active_app_icon_index", 1))
    val activeAppIconIndex: StateFlow<Int> = _activeAppIconIndex.asStateFlow()

    // Streams
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
        checkPendingReviews()
    }

    private fun checkPendingReviews() {
        viewModelScope.launch {
            val now = Clock.System.now().toEpochMilliseconds()
            allStrains.value.filter { it.needsReview && it.reviewReminderTime != null && it.reviewReminderTime!! <= now }.forEach { strain ->
                NotificationHelper.notify(
                    "Time to review!".translate(_language.value),
                    "How do you like '${strain.strainName}'? Rate it now.".translate(_language.value)
                )
            }
        }
    }

    fun cleanExpiredTrash() {
        viewModelScope.launch {
            val cutoff = Clock.System.now().toEpochMilliseconds() - 7 * 24 * 60 * 60 * 1000L
            repository.cleanExpiredTrash(cutoff)
        }
    }

    // Stream today's sessions
    val sessionsToday: StateFlow<List<SmokeSession>> = combine(allSessions, dayRhythmHours) { list, rhythm ->
        try {
            val now = Clock.System.now().toEpochMilliseconds()
            val todayStart = getStartOfLogicalDay(now, rhythm)
            val todayEnd = todayStart + 24 * 60 * 60 * 1000L - 1L
            list.filter { it.timestamp in todayStart..todayEnd }.sortedByDescending { it.timestamp }
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
                stats.add(DayStat(labels[i], daySessions.size, daySessions.sumOf { it.grams }.roundToDecimals(1), dayStart))
            }
        } catch (e: Exception) {
            val fallbackLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
            fallbackLabels.forEach { stats.add(DayStat(it, 0, 0.0, 0L)) }
        }
        stats
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun logSession(grams: Double, strain: String, notes: String, timestamp: Long = Clock.System.now().toEpochMilliseconds()) {
        repository.insertSession(SmokeSession(timestamp = timestamp, grams = grams, strain = strain, notes = notes))
        NotificationHelper.notify("GreenTracker", "Smoke logged +${grams.format(1, _language.value)}g")
    }

    fun updateSession(session: SmokeSession) {
        repository.updateSession(session)
        NotificationHelper.notify("GreenTracker", "Eintrag aktualisiert".translate(_language.value))
    }
    fun deleteSession(id: Long) = repository.deleteSession(id)
    fun restoreSession(id: Long) = repository.restoreSession(id)
    fun permanentlyDeleteSession(id: Long) = repository.permanentlyDeleteSession(id)
    fun emptyAllTrash() = repository.emptyTrash()
    fun clearAll() = repository.clearAll()

    fun addStrain(name: String, producer: String, cat: String, thc: Double, cbd: Double, rat: String, notes: String, photo: String, needsReview: Boolean = false) {
        val now = Clock.System.now().toEpochMilliseconds()
        val reviewTime = if (needsReview) now + _reviewReminderDays.value * 24 * 60 * 60 * 1000L else null
        
        repository.insertStrain(StrainEntry(
            strainName = name, 
            producerCultivar = producer, 
            category = cat, 
            thcPercentage = thc, 
            cbdPercentage = cbd, 
            rating = rat, 
            notes = notes, 
            photoUri = photo, 
            needsReview = needsReview,
            reviewReminderTime = reviewTime,
            createdAt = now
        ))
        NotificationHelper.notify("GreenTracker", "Sorte hinzugefügt".translate(_language.value))
    }
    fun updateStrain(entry: StrainEntry) = repository.updateStrain(entry)
    fun deleteStrain(id: Long) = repository.deleteStrain(id)
    fun restoreStrain(id: Long) = repository.restoreStrain(id)
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
    fun setWidgetMaxDosage(grams: Double) {
        _widgetMaxDosage.value = grams
        settings.putFloat("widget_max_dosage", grams.toFloat())
    }
    fun setQuickLogGrams(grams: Double) {
        _quickLogGrams.value = grams
        settings.putFloat("quick_track_grams", grams.toFloat())
    }
    fun setAppIconIndex(index: Int) {
        _activeAppIconIndex.value = index
        settings.putInt("active_app_icon_index", index)
    }

    fun setReviewReminderDays(days: Int) {
        _reviewReminderDays.value = days
        settings.putInt("review_reminder_days", days)
    }

    private fun loadSavedTheme() = CannabisTheme.entries.find { it.id == settings.getString("active_theme_id", "") } ?: CannabisTheme.CLASSIC_HERBAL
    private fun loadSavedLanguage() = settings.getString("app_language", "de")
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
                "widget_max_dosage" to _widgetMaxDosage.value.toString(),
                "quick_track_grams" to _quickLogGrams.value.toString(),
                "active_app_icon_index" to _activeAppIconIndex.value.toString(),
                "review_reminder_days" to _reviewReminderDays.value.toString()
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
            data.settings["widget_max_dosage"]?.toDoubleOrNull()?.let { setWidgetMaxDosage(it) }
            data.settings["quick_track_grams"]?.toDoubleOrNull()?.let { setQuickLogGrams(it) }
            data.settings["active_app_icon_index"]?.toIntOrNull()?.let { setAppIconIndex(it) }
            data.settings["review_reminder_days"]?.toIntOrNull()?.let { setReviewReminderDays(it) }
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
    return ((this * multiplier).toInt().toDouble() / multiplier)
}

fun Double.format(decimals: Int, lang: String = "de"): String {
    val roundedVal = this.roundToDecimals(decimals)
    val formatted = if (decimals == 0) {
        roundedVal.toInt().toString()
    } else {
        val rounded = roundedVal.toString()
        if (rounded.contains(".")) {
            val parts = rounded.split(".")
            if (parts[1].length < decimals) {
                rounded + "0".repeat(decimals - parts[1].length)
            } else rounded
        } else {
            rounded + "." + "0".repeat(decimals)
        }
    }
    return if (lang == "de") formatted.replace(".", ",") else formatted
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
        "Custom Range" -> "Zeitraum wählen"
        "From: " -> "Von: "
        "To: " -> "Bis: "
        "Select Start Date" -> "Beginn wählen"
        "Select End Date" -> "Ende wählen"
        "Earlier Logs" -> "Frühere Logs"
        "Overall Consumption" -> "Gesamtverbrauch"
        "Weekly Trends (Last 7 Days)" -> "Wöchentliche Trends"
        "Summary Stats" -> "Statistiken"
        "Total Sessions" -> "Gesamte Sitzungen"
        "Total Grams" -> "Gesamtgramm"
        "Daily Average" -> "Tagesdurchschnitt"
        "Usage Summary" -> "Nutzungsübersicht"
        "Consumption Analytics" -> "Verbrauchs-Analyse"
        "Amount (Grams):" -> "Menge (Gramm):"
        "Strain / Variety" -> "Sorte / Varietät"
        "Session Notes" -> "Notizen"
        "Save Log" -> "Speichern"
        "Privacy Policy" -> "Datenschutzerklärung"
        "Install App" -> "App installieren"
        "Download GreenTracker" -> "GreenTracker herunterladen"
        "How to install GreenTracker on your device:" -> "So installierst du GreenTracker auf deinem Gerät:"
        "1. Tap the Share button (square with arrow up)" -> "1. Tippe auf den Teilen-Button (Quadrat mit Pfeil nach oben)"
        "2. Scroll down and select 'Add to Home Screen'" -> "2. Scrolle nach unten und wähle 'Zum Home-Bildschirm'"
        "This enables features like Push Notifications on iOS." -> "Dies aktiviert Funktionen wie Push-Benachrichtigungen auf iOS."
        "Time to review!" -> "Zeit für eine Bewertung!"
        "Edit Time" -> "Zeit bearbeiten"
        "Time of Session:" -> "Uhrzeit:"
        "Changelog" -> "Versionsverlauf"
        "Permissions" -> "Berechtigungen"
        "Enable browser notifications to receive status updates. Note: Notifications are only supported when GreenTracker is used as a WebApp (added to the Home Screen)." -> "Aktiviere Browser-Benachrichtigungen, um Status-Updates zu erhalten. Hinweis: Benachrichtigungen werden nur unterstützt, wenn GreenTracker als WebApp (zum Home-Bildschirm hinzugefügt) verwendet wird."
        "Test Notification" -> "Test-Benachrichtigung"
        "Danger Zone" -> "Daten löschen"
        "Backup & Restore" -> "Backup & Wiederherstellung"
        "Save Backup" -> "Backup speichern"
        "Import Backup" -> "Backup importieren"
        "Trash" -> "Papierkorb"
        "Deleted Session Logs" -> "Gelöschte Sitzungen"
        "Deleted Journal Entries" -> "Gelöschte Journal-Einträge"
        "Empty Trash" -> "Papierkorb leeren"
        "Are you sure?" -> "Bist du sicher?"
        "Confirm Delete" -> "Löschen bestätigen"
        "Empty All Trash" -> "Gesamten Papierkorb leeren"
        "Restore" -> "Wiederherstellen"
        "Delete Permanently" -> "Endgültig löschen"
        "Delete Entry?" -> "Eintrag löschen?"
        "Do you really want to delete this strain journal entry?" -> "Möchten Sie diesen Journal-Eintrag wirklich löschen?"
        "Delete" -> "Löschen"
        "Cancel" -> "Abbrechen"
        "Language Settings" -> "Spracheinstellungen"
        "Theme Settings" -> "Design & Farben"
        "App Icon" -> "App-Icon"
        "Choose your preferred App Icon. Changes apply automatically across the web dashboard." -> "Wähle dein bevorzugtes App-Icon. Änderungen werden automatisch im Dashboard übernommen."
        "Daily Dosage Limit" -> "Heutiges Limit"
        "Day Rhythm" -> "Tagesrhythmus"
        "Start of Day" -> "Tagesbeginn"
        "Configure when your logging day starts (e.g. 4:00 AM)." -> "Konfigurieren Sie, wann Ihr Protokolltag beginnt (z. B. 04:00 Uhr)."
        "Slider Max Range" -> "Slider-Maximum"
        "Widget Dosage Range Setup" -> "Widget-Dosierung"
        "Widget Max Dosage" -> "Widget Max. Dosis"
        "Widget Dosage Step" -> "Widget Dosis-Schritt"
        "Push Notification Reminders" -> "Erinnerungen & Push"
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
        "Time to log your session!" -> "Zeit, deine Sitzung zu protokollieren!"
        "Quick Log Amount" -> "Schnell-Log Menge"
        "App Maintenance" -> "Wartung"
        "Check for updates or refresh the app cache." -> "Nach Updates suchen oder Cache aktualisieren."
        "Check for Updates / Refresh" -> "Nach Updates suchen / Aktualisieren"
        "All Ratings" -> "Alle Bewertungen"
        "Avoid" -> "Nicht empfohlen"
        "Edit Session" -> "Eintrag bearbeiten"
        "Amount:" -> "Menge:"
        "Strain" -> "Sorte"
        "Add Strain" -> "Sorte hinzufügen"
        "Edit Strain" -> "Sorte bearbeiten"
        "Photo Attached" -> "Foto beigefügt"
        "Photo" -> "Foto"
        "Producer" -> "Hersteller"
        "Name *" -> "Name *"
        "THC %" -> "THC %"
        "CBD %" -> "CBD %"
        "Delete Session?" -> "Sitzung löschen?"
        "Move this log to the trash?" -> "Eintrag in den Papierkorb verschieben?"
        "Permanently Delete?" -> "Endgültig löschen?"
        "This will be gone forever!" -> "Dies wird für immer gelöscht!"
        "Delete Forever" -> "Endgültig löschen"
        "Clear All Data?" -> "Alle Daten löschen?"
        "This will permanently delete all logs and entries. This action cannot be undone!" -> "Dies wird alle Protokolle und Einträge dauerhaft löschen. Dies kann nicht rückgängig gemacht werden!"
        "Delete Everything" -> "Alles löschen"
        "Excess" -> "Überschreitung"
        "Logged" -> "Protokolliert"
        "Days" -> "Tage"
        "Usage" -> "Verbrauch"
        "Widget" -> "Widget"
        "Quick logged from Widget" -> "Schnell-Log über Widget"
        "Set your preferred application language: English or German." -> "Wähle deine bevorzugte Sprache: Englisch oder Deutsch."
        "Deleted items are automatically removed after 7 days." -> "Gelöschte Elemente werden nach 7 Tagen automatisch entfernt."
        "No deleted session logs." -> "Keine gelöschten Logs vorhanden."
        "No deleted strain entries." -> "Keine gelöschten Einträge vorhanden."
        "Rly?" -> "Sicher?"
        "Smart History: Auto-collapse past days" -> "Smart-Historie: Vergangene Tage einklappen"
        "Excess Counter on Home screen" -> "Limit-Anzeige auf dem Home-Screen"
        "Natural German localization update" -> "Natürliche deutsche Lokalisierung"
        "Compact UI navigation bars" -> "Kompaktere Navigationsleisten"
        "Integer-based tracking" -> "Ganzzahl-basiertes Tracking"
        "Interactive Changelog in Settings" -> "Interaktiver Versionsverlauf"
        "Improved Stats: Added Logged-Days timeframe" -> "Bessere Statistik: Anzeige der protokollierten Tage"
        "Full ZIP Backup & Restore" -> "Vollständiges ZIP-Backup & Wiederherstellung"
        "Improved data integrity" -> "Verbesserte Datensicherheit"
        "Home Screen Widgets" -> "Home-Screen Widgets"
        "Notification Reminders" -> "Erinnerungs-Benachrichtigungen"
        "Theme Engine (Midnight, Pride, etc.)" -> "Theme-Engine (Midnight, Pride, etc.)"
        "Custom Launcher Icons" -> "Eigene App-Icons"
        "Initial Release: Core tracking" -> "Erste Version: Basis-Tracking"
        "Restored 0.1g logging precision" -> "0,1g Logging-Präzision wiederhergestellt"
        "Fixed German 'Heute/Gestern' headers" -> "Heute/Gestern Header in der Historie korrigiert"
        "Fixed deletion state bug in lists" -> "Fehler beim Lösch-Status in Listen behoben"
        "Platform Modernization: Android 16 (API 36)" -> "Plattform-Modernisierung: Android 16 (API 36)"
        "Java 17 Update for performance & security" -> "Java 17 Update für Performance & Sicherheit"
        "Met 2026 Google Play Store technical requirements" -> "Technische Anforderungen für Google Play 2026 erfüllt"
        "Review Later" -> "Später bewerten"
        "Remind me to rate this strain in a few days" -> "Erinnere mich in ein paar Tagen an die Bewertung"
        "Needs Review" -> "Offene Bewertungen"
        "Review Reminder Period" -> "Zeitraum für Bewertung"
        "Review Later: Mark strains for subsequent rating" -> "Später bewerten: Markiere Sorten für spätere Bewertung"
        "Rating Reminders: Configurable alerts (1-14 days)" -> "Bewertungs-Erinnerung: Einstellbarer Zeitraum (1-14 Tage)"
        "New Filter: Quickly find entries needing review" -> "Neuer Filter: Finde Einträge, die noch bewertet werden müssen"
        "Compact Settings: Streamlined notification selection" -> "Kompakte Einstellungen: Optimierte Benachrichtigungsauswahl"
        "Full localization: 100% German UI coverage and bug fixes" -> "Vollständige Lokalisierung: 100% deutsche Benutzeroberfläche und Fehlerbehebungen"
        "iPhone Notification Fix: Improved reliability for iOS" -> "iPhone Benachrichtigungs-Fix: Höhere Zuverlässigkeit für iOS"
        "Journal UI: New 'Review Later' button logic and compact layout" -> "Journal-Design: Neuer 'Später bewerten' Button und kompaktes Layout"
        "Improved Backup: Trash items and review states are now included" -> "Bessere Sicherung: Papierkorb-Elemente und Review-Status integriert"
        "Privacy Transparency: Refined Privacy Policy with clear sections and direct contact details" -> "Datenschutz-Update: Klare Struktur & direkter Kontakt"
        "Language Settings" -> "Spracheinstellungen"
        "Theme Settings" -> "Design & Farben"
        "Widget Dosage Range Setup" -> "Widget-Dosierung"
        "Push Notification Reminders" -> "Erinnerungen & Push"
        "Backup & Restore" -> "Sicherung & Wiederherstellung"
        "Clear All Tracking Logs" -> "Alle Daten unwiderruflich löschen"
        "Off" -> "Aus"
        "1h" -> "1 Std."
        "2h" -> "2 Std."
        "4h" -> "4 Std."
        "8h" -> "8 Std."
        "How do you like '%s'? Rate it now." -> "Wie gefällt dir '%s'? Bewerte die Sorte jetzt."
        "Max.:" -> "Max.:"
        "Eintrag aktualisiert" -> "Eintrag aktualisiert"
        "Sorte hinzugefügt" -> "Sorte hinzugefügt"
        "Privacy Policy / Datenschutzerklärung" -> "Datenschutzerklärung"
        else -> this
    }
}
