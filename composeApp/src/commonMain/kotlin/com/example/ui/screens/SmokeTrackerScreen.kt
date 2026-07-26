package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.SmokeSession
import com.example.data.StrainEntry
import com.example.ui.theme.CannabisTheme
import com.example.ui.viewmodel.DayStat
import com.example.ui.viewmodel.SmokeViewModel
import com.example.ui.viewmodel.translate
import com.example.ui.viewmodel.format
import com.example.util.Base64
import kotlinx.coroutines.launch
import kotlinx.datetime.*
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLAnchorElement
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag
import org.w3c.dom.url.URL
import org.w3c.dom.HTMLInputElement
import org.w3c.files.FileReader
import org.w3c.files.get
import kotlin.js.*

enum class AppTab {
    HOME, HISTORY, STATS, JOURNAL, SETTINGS
}

enum class HistoryFilter {
    ALL, WEEK, MONTH, YEAR
}

@JsFun("""() => { 
    if ('serviceWorker' in navigator) {
        navigator.serviceWorker.getRegistrations().then(function(registrations) {
            for(let registration of registrations) { registration.unregister(); }
            if ('caches' in window) {
                caches.keys().then(function(names) {
                    for (let name of names) caches.delete(name);
                });
            }
            window.location.reload(true);
        });
    } else {
        window.location.reload(true);
    }
}""")
external fun forceAppUpdate(): Unit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmokeTrackerScreen(
    viewModel: SmokeViewModel,
    modifier: Modifier = Modifier
) {
    val activeTheme by viewModel.activeTheme.collectAsState()
    val activeLanguage by viewModel.language.collectAsState()
    
    val trashedSessions by viewModel.trashedSessions.collectAsState()
    val trashedStrains by viewModel.trashedStrains.collectAsState()
    val allSessions by viewModel.allSessions.collectAsState()
    val allStrains by viewModel.allStrains.collectAsState()
    val sessionsToday by viewModel.sessionsToday.collectAsState()
    val weeklyStats by viewModel.weeklyStats.collectAsState()
    val dailyGoalGrams by viewModel.dailyGoalGrams.collectAsState()

    var currentTab by remember { mutableStateOf(AppTab.HOME) }
    var showAddManualDialog by remember { mutableStateOf(false) }
    var manualAddInitialGrams by remember { mutableDoubleStateOf(0.2) }
    var showAddStrainDialog by remember { mutableStateOf(false) }
    var editingStrain by remember { mutableStateOf<StrainEntry?>(null) }
    var zoomedPhoto by remember { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = if (activeTheme == CannabisTheme.PRIDE) Color(0xFF0F0F1A) else MaterialTheme.colorScheme.background,
        topBar = {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.Spa, null, modifier = Modifier.size(36.dp).clip(CircleShape), tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = "GreenTracker",
                            style = if (activeTheme == CannabisTheme.PRIDE) {
                                MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    brush = Brush.linearGradient(colors = listOf(Color(0xFFFF0000), Color(0xFFFF8C00), Color(0xFFFFD700), Color(0xFF32CD32), Color(0xFF1E90FF), Color(0xFF8A2BE2), Color(0xFFFF0000))),
                                    letterSpacing = (-1).sp
                                )
                            } else {
                                MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = (-0.5).sp)
                            }
                        )
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 8.dp,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
                    BottomNavItem(Icons.Default.Home, "Home".translate(activeLanguage), currentTab == AppTab.HOME) { currentTab = AppTab.HOME }
                    BottomNavItem(Icons.Default.List, "History".translate(activeLanguage), currentTab == AppTab.HISTORY) { currentTab = AppTab.HISTORY }
                    BottomNavItem(Icons.Default.Assessment, "Stats".translate(activeLanguage), currentTab == AppTab.STATS) { currentTab = AppTab.STATS }
                    BottomNavItem(Icons.Default.MenuBook, "Journal".translate(activeLanguage), currentTab == AppTab.JOURNAL) { currentTab = AppTab.JOURNAL }
                    val totalTrashedCount = trashedSessions.size + trashedStrains.size
                    BottomNavItem(Icons.Default.Settings, "Settings".translate(activeLanguage), currentTab == AppTab.SETTINGS, totalTrashedCount) { currentTab = AppTab.SETTINGS }
                }
            }
        },
        floatingActionButton = {
            if (currentTab == AppTab.HISTORY || currentTab == AppTab.JOURNAL) {
                FloatingActionButton(
                    onClick = {
                        if (currentTab == AppTab.HISTORY) { manualAddInitialGrams = 0.2; showAddManualDialog = true }
                        else { editingStrain = null; showAddStrainDialog = true }
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.Add, null)
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier.fillMaxSize()
                .then(if (activeTheme == CannabisTheme.PRIDE) Modifier.background(Brush.verticalGradient(listOf(Color(0xFF0F0F1A), Color(0xFF1E1B4B), Color(0xFF312E81)))) else Modifier)
                .padding(paddingValues)
        ) {
            when (currentTab) {
                AppTab.HOME -> HomeScreen(viewModel, activeTheme, sessionsToday, dailyGoalGrams, activeLanguage)
                AppTab.HISTORY -> HistoryScreen(allSessions, activeLanguage, viewModel)
                AppTab.STATS -> StatsScreen(weeklyStats, allSessions, activeLanguage, viewModel)
                AppTab.JOURNAL -> JournalScreen(allStrains, activeLanguage, viewModel, onEdit = { editingStrain = it; showAddStrainDialog = true }, onZoom = { zoomedPhoto = it })
                AppTab.SETTINGS -> SettingsScreen(viewModel, activeTheme, dailyGoalGrams, activeLanguage, trashedSessions, trashedStrains)
            }
        }
    }

    if (showAddManualDialog) {
        val maxDosage by viewModel.widgetMaxDosage.collectAsState()
        AddManualSessionDialog(manualAddInitialGrams, activeLanguage, maxDosage, activeTheme, onDismiss = { showAddManualDialog = false }) { g, s, n, t ->
            viewModel.logSession(g, s, n, t)
            showAddManualDialog = false
        }
    }

    if (showAddStrainDialog) {
        AddEditStrainDialog(editingStrain, activeLanguage, onDismiss = { showAddStrainDialog = false }) { n, p, c, thc, cbd, r, nt, photo ->
            if (editingStrain == null) viewModel.addStrain(n, p, c, thc, cbd, r, nt, photo)
            else viewModel.updateStrain(editingStrain!!.copy(strainName = n, producerCultivar = p, category = c, thcPercentage = thc, cbdPercentage = cbd, rating = r, notes = nt, photoUri = photo))
            showAddStrainDialog = false
        }
    }

    if (zoomedPhoto != null) {
        ZoomedPhotoDialog(zoomedPhoto!!, onDismiss = { zoomedPhoto = null })
    }
}

@Composable
fun ZoomedPhotoDialog(photoUri: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.9f)).clickable { onDismiss() }, contentAlignment = Alignment.Center) {
            val imageBitmap = remember(photoUri) {
                try {
                    val base64Data = if (photoUri.contains(",")) photoUri.split(",")[1] else photoUri
                    val bytes = com.example.util.Base64.decode(base64Data)
                    org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()
                } catch (e: Exception) { null }
            }
            if (imageBitmap != null) {
                Image(bitmap = imageBitmap, contentDescription = null, modifier = Modifier.fillMaxWidth(0.95f).clip(RoundedCornerShape(12.dp)))
            }
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
                Icon(Icons.Default.Close, null, tint = Color.White)
            }
        }
    }
}

@Composable
fun BottomNavItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean, badgeCount: Int = 0, onClick: () -> Unit) {
    val contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    val backgroundColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent

    Column(
        modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).background(backgroundColor).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        BadgedBox(badge = { if (badgeCount > 0) Badge(containerColor = MaterialTheme.colorScheme.error) { Text("$badgeCount", fontSize = 9.sp) } }) {
            Icon(imageVector = icon, contentDescription = label, tint = contentColor, modifier = Modifier.size(24.dp))
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, fontSize = 10.sp, color = contentColor))
    }
}

@Composable
fun HomeScreen(viewModel: SmokeViewModel, activeTheme: CannabisTheme, sessionsToday: List<SmokeSession>, dailyGoalGrams: Double, lang: String) {
    val totalTodayGrams = sessionsToday.sumOf { it.grams }
    val progress = if (dailyGoalGrams > 0.0) (totalTodayGrams / dailyGoalGrams).toFloat().coerceIn(0f, 1f) else 0f
    val quickLogGrams by viewModel.quickLogGrams.collectAsState()
    val maxDosage by viewModel.widgetMaxDosage.collectAsState()

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)) {
        item {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(32.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(text = "Today's Consumption".translate(lang), style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium))
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            val bigGramColor = if (totalTodayGrams > dailyGoalGrams) MaterialTheme.colorScheme.error else Color(0xFF2E7D32)
                            Text(text = totalTodayGrams.format(1), style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Bold, fontSize = 54.sp, color = bigGramColor))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "g", style = MaterialTheme.typography.titleLarge.copy(color = bigGramColor, fontWeight = FontWeight.SemiBold))
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(text = "Sessions".translate(lang), style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium))
                            Text(text = "${sessionsToday.size}", style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface))
                        }
                    }
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)), color = if (totalTodayGrams > dailyGoalGrams) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary, trackColor = MaterialTheme.colorScheme.surfaceVariant)
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Logging".translate(lang), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer))
                            Text(text = "Slide to set log amount".translate(lang), style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)))
                        }
                        Button(onClick = { viewModel.logSession(quickLogGrams, "", "Quick log") }, shape = RoundedCornerShape(16.dp)) {
                            Icon(imageVector = Icons.Default.Add, null, modifier = Modifier.size(20.dp))
                            Text(text = "Log ${quickLogGrams.format(1)}g", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                         Text(text = "Session Dosage:".translate(lang), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer))
                         Text(text = "${quickLogGrams.format(1)} g", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onPrimaryContainer))
                    }
                    Slider(
                        value = quickLogGrams.toFloat(), 
                        onValueChange = { viewModel.setQuickLogGrams(it.toDouble()) }, 
                        valueRange = 0.1f..maxDosage.toFloat(), 
                        steps = if (maxDosage > 0.1) ((maxDosage - 0.1) * 10).toInt() else 0,
                        colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }

        item { Text("Today's Logs Preview".translate(lang), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)) }
        items(sessionsToday.take(5)) { sItem ->
            SessionItemRow(sItem, lang, viewModel, onDelete = { viewModel.deleteSession(sItem.id) }, onEdit = { viewModel.updateSession(it) })
        }
    }
}

@Composable
fun HistoryScreen(allSessions: List<SmokeSession>, lang: String, viewModel: SmokeViewModel) {
    var filter by remember { mutableStateOf(HistoryFilter.ALL) }
    val dayRhythm by viewModel.dayRhythmHours.collectAsState()
    
    val filtered = remember(allSessions, filter) {
        val now = Clock.System.now().toEpochMilliseconds()
        when (filter) {
            HistoryFilter.ALL -> allSessions
            HistoryFilter.WEEK -> allSessions.filter { it.timestamp > now - 7 * 24 * 3600 * 1000L }
            HistoryFilter.MONTH -> allSessions.filter { it.timestamp > now - 30 * 24 * 3600 * 1000L }
            HistoryFilter.YEAR -> allSessions.filter { it.timestamp > now - 365 * 24 * 3600 * 1000L }
        }
    }

    val grouped = remember(filtered, dayRhythm) {
        filtered.groupBy { 
            val instant = Instant.fromEpochMilliseconds(it.timestamp - dayRhythm * 3600 * 1000L)
            val date = instant.toLocalDateTime(TimeZone.currentSystemDefault()).date
            date.toString()
        }.toList().sortedByDescending { it.first }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HistoryFilter.entries.forEach { f ->
                FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.name.translate(lang)) })
            }
        }
        LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            grouped.forEach { (date, sessions) ->
                item { Text(date, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp)) }
                items(sessions) { sItem -> SessionItemRow(sItem, lang, viewModel, onDelete = { viewModel.deleteSession(sItem.id) }, onEdit = { viewModel.updateSession(sItem) }) }
            }
        }
    }
}

@Composable
fun StatsScreen(weeklyStats: List<DayStat>, allSessions: List<SmokeSession>, lang: String, viewModel: SmokeViewModel) {
    val totalGrams = allSessions.sumOf { it.grams }
    val maxGrams = (weeklyStats.maxOfOrNull { it.totalGrams } ?: 1.0).coerceAtLeast(0.1)
    val daysCount = remember(allSessions) {
        if (allSessions.isEmpty()) 1 else allSessions.map { (it.timestamp - 4 * 3600 * 1000L) / (24 * 3600 * 1000L) }.toSet().size
    }
    val avgGrams = totalGrams / daysCount

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)) {
        item { Text("Consumption Analytics".translate(lang), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        item {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(32.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Weekly Trends (Last 7 Days)".translate(lang), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant))
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(modifier = Modifier.fillMaxWidth().height(180.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
                        weeklyStats.forEach { stat ->
                            val heightFrac = (stat.totalGrams / maxGrams).toFloat().coerceIn(0.02f, 1f)
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Bottom) {
                                if (stat.totalGrams > 0.0) Text(stat.totalGrams.format(1), style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold))
                                Box(modifier = Modifier.fillMaxHeight(heightFrac).width(22.dp).clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)).background(if (stat.totalGrams > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant))
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(stat.dayLabel.translate(lang), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = if (stat.totalGrams > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)))
                            }
                        }
                    }
                }
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatInsightCard("Total Sessions".translate(lang), "${allSessions.size}", Icons.Default.Assessment, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                StatInsightCard("Total Grams".translate(lang), "${totalGrams.format(2)}g", Icons.Default.Spa, MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
            }
        }
        item { StatInsightCard("Daily Average".translate(lang), "${(totalGrams / daysCount.coerceAtLeast(1)).format(2)}g", Icons.Default.FilterVintage, MaterialTheme.colorScheme.tertiary, Modifier.fillMaxWidth()) }
    }
}

@Composable
fun StatInsightCard(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(color.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
            }
            Text(title, style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun JournalScreen(strains: List<StrainEntry>, lang: String, viewModel: SmokeViewModel, onEdit: (StrainEntry) -> Unit, onZoom: (String) -> Unit) {
    var search by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var selectedRating by remember { mutableStateOf("All") }

    val filtered = strains.filter { strain ->
        val matchesSearch = strain.strainName.contains(search, ignoreCase = true) || strain.producerCultivar.contains(search, ignoreCase = true) || strain.category.contains(search, ignoreCase = true)
        val matchesCategory = selectedCategory == "All" || strain.category == selectedCategory
        val matchesRating = selectedRating == "All" || strain.rating == selectedRating
        matchesSearch && matchesCategory && matchesRating
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = search, 
            onValueChange = { search = it }, 
            modifier = Modifier.fillMaxWidth(), 
            placeholder = { Text("Search strains or producers...".translate(lang)) }, 
            leadingIcon = { Icon(Icons.Default.Search, null) }, 
            shape = RoundedCornerShape(16.dp)
        )
        
        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("All", "Indica", "Sativa", "Hybrid").forEach { cat ->
                FilterChip(
                    selected = selectedCategory == cat,
                    onClick = { selectedCategory = cat },
                    label = { Text(cat.translate(lang)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = when(cat) {
                            "Indica" -> Color(0xFF6A1B9A); "Sativa" -> Color(0xFFE65100); "Hybrid" -> Color(0xFF1B5E20); else -> MaterialTheme.colorScheme.primary
                        },
                        selectedLabelColor = Color.White
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("All", "THUMBS_UP", "NEUTRAL", "THUMBS_DOWN").forEach { rate ->
                val (label, icon) = when(rate) {
                    "THUMBS_UP" -> "Recommended" to Icons.Default.ThumbUp
                    "NEUTRAL" -> "Neutral" to Icons.Default.SentimentNeutral
                    "THUMBS_DOWN" -> "Avoid" to Icons.Default.ThumbDown
                    else -> "All Ratings" to Icons.Default.Star
                }
                FilterChip(
                    selected = selectedRating == rate,
                    onClick = { selectedRating = rate },
                    label = { 
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(icon, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(label.translate(lang))
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 80.dp)) {
            items(filtered) { strain ->
                var showDeleteConfirm by remember { mutableStateOf(false) }
                if (showDeleteConfirm) {
                    AlertDialog(
                        onDismissRequest = { /* Lock */ },
                        title = { Text("Delete Entry?".translate(lang)) },
                        text = { Text("Move this entry to the trash?".translate(lang)) },
                        confirmButton = { Button(onClick = { viewModel.deleteStrain(strain.id); showDeleteConfirm = false }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Delete".translate(lang)) } },
                        dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel".translate(lang)) } }
                    )
                }

                Card(shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable { if (strain.photoUri.isNotEmpty()) onZoom(strain.photoUri) }, contentAlignment = Alignment.Center) {
                                if (strain.photoUri.isNotEmpty()) {
                                    val imageBitmap = remember(strain.photoUri) {
                                        try {
                                            val base64Data = if (strain.photoUri.contains(",")) strain.photoUri.split(",")[1] else strain.photoUri
                                            val bytes = com.example.util.Base64.decode(base64Data)
                                            org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()
                                        } catch (e: Exception) { null }
                                    }
                                    if (imageBitmap != null) {
                                        Image(
                                            bitmap = imageBitmap,
                                            contentDescription = "Strain Photo",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Icon(Icons.Default.Photo, null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                } else {
                                    Icon(Icons.Default.Spa, null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(strain.strainName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                    val (icon, color, bg) = when(strain.rating) {
                                        "THUMBS_DOWN" -> Triple(Icons.Default.ThumbDown, Color.Red, Color(0xFFFFEBEE))
                                        "NEUTRAL" -> Triple(Icons.Default.SentimentNeutral, Color.Gray, Color(0xFFF5F5F5))
                                        else -> Triple(Icons.Default.ThumbUp, Color(0xFF2E7D32), Color(0xFFE8F5E9))
                                    }
                                    Surface(shape = CircleShape, color = bg, border = BorderStroke(1.dp, color.copy(alpha = 0.5f))) {
                                        Icon(icon, null, modifier = Modifier.padding(6.dp).size(16.dp), tint = color)
                                    }
                                }
                                if (strain.producerCultivar.isNotEmpty()) Text(strain.producerCultivar, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(strain.category, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = when(strain.category){"Indica" -> Color(0xFF6A1B9A); "Sativa" -> Color(0xFFE65100); else -> Color(0xFF1B5E20)})
                                    if (strain.thcPercentage > 0) Text("THC ${strain.thcPercentage}%", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    if (strain.cbdPercentage > 0) Text("CBD ${strain.cbdPercentage}%", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            IconButton(onClick = { onEdit(strain) }) { Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) }
                            IconButton(onClick = { showDeleteConfirm = true }) { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp)) }
                        }
                        if (strain.notes.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(strain.notes, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(viewModel: SmokeViewModel, activeTheme: CannabisTheme, dailyGoalGrams: Double, lang: String, trashedSessions: List<SmokeSession>, trashedStrains: List<StrainEntry>) {
    val dayRhythm by viewModel.dayRhythmHours.collectAsState()
    val reminderInterval by viewModel.reminderInterval.collectAsState()
    var expandedSection by remember { mutableStateOf<String?>(null) }
    var isSessionsTrashExpanded by remember { mutableStateOf(false) }
    var isStrainsTrashExpanded by remember { mutableStateOf(false) }
    var showClearAllConfirm by remember { mutableStateOf(false) }

    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = { /* Lock */ },
            title = { Text("Clear All Data?".translate(lang), color = MaterialTheme.colorScheme.error) },
            text = { Text("This will permanently delete all logs and entries. This action cannot be undone!".translate(lang)) },
            confirmButton = { Button(onClick = { viewModel.clearAll(); showClearAllConfirm = false }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Delete Everything".translate(lang)) } },
            dismissButton = { TextButton(onClick = { showClearAllConfirm = false }) { Text("Cancel".translate(lang)) } }
        )
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp)) {
        item { Text("Application Settings".translate(lang), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        item { CollapsibleSettingsCard("Language Settings".translate(lang), expandedSection == "lang", onToggle = { expandedSection = if (expandedSection == "lang") null else "lang" }) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = lang == "en", onClick = { viewModel.setLanguage("en") }, label = { Text("English") })
                FilterChip(selected = lang == "de", onClick = { viewModel.setLanguage("de") }, label = { Text("Deutsch") })
            }
        } }
        item { CollapsibleSettingsCard("Theme Settings".translate(lang), expandedSection == "theme", onToggle = { expandedSection = if (expandedSection == "theme") null else "theme" }) {
            val themeList = CannabisTheme.entries.toList()
            themeList.forEach { theme ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { viewModel.setTheme(theme) }.padding(vertical = 4.dp)) {
                    RadioButton(selected = activeTheme == theme, onClick = { viewModel.setTheme(theme) })
                    Text(theme.displayName.translate(lang))
                }
            }
        } }
        item { CollapsibleSettingsCard("Day Rhythm".translate(lang), expandedSection == "rhythm", onToggle = { expandedSection = if (expandedSection == "rhythm") null else "rhythm" }) {
            Text("Start of Day".translate(lang) + ": ${dayRhythm.toString().padStart(2, '0')}:00", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Slider(value = dayRhythm.toFloat(), onValueChange = { viewModel.setDayRhythm(it.toInt()) }, valueRange = 0f..23f, steps = 23)
        } }
        item { CollapsibleSettingsCard("Notifications".translate(lang), expandedSection == "notif", onToggle = { expandedSection = if (expandedSection == "notif") null else "notif" }) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Reminders:".translate(lang), fontWeight = FontWeight.Bold)
                listOf(0 to "Off", 1 to "1h", 2 to "2h", 4 to "4h", 8 to "8h").forEach { (h, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { viewModel.setReminderInterval(h) }) {
                        RadioButton(selected = reminderInterval == h, onClick = { viewModel.setReminderInterval(h) })
                        Text(label.translate(lang))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                val quickLogGrams by viewModel.quickLogGrams.collectAsState()
                Text("Quick Log Amount".translate(lang), fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Slider(value = quickLogGrams.toFloat(), onValueChange = { viewModel.setQuickLogGrams(it.toDouble()) }, valueRange = 0.1f..5.0f, steps = 49, modifier = Modifier.weight(1f))
                    Text(quickLogGrams.format(1) + "g", modifier = Modifier.width(48.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                }
                Button(onClick = { com.example.util.NotificationHelper.requestPermission(); com.example.util.NotificationHelper.notify("GreenTracker", "Notifications Active! 🌿") }, modifier = Modifier.fillMaxWidth()) { Text("Test Notification") }
            }
        } }

        item {
            CollapsibleSettingsCard("Daily Dosage Limit".translate(lang), expandedSection == "limit", onToggle = { expandedSection = if (expandedSection == "limit") null else "limit" }) {
                Text(dailyGoalGrams.format(1) + "g", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Slider(value = dailyGoalGrams.toFloat(), onValueChange = { viewModel.setDailyGoal(it.toDouble()) }, valueRange = 0.5f..10.0f, steps = 95)
            }
        }

        item {
            val maxDosage by viewModel.widgetMaxDosage.collectAsState()
            CollapsibleSettingsCard("Slider Max Range".translate(lang), expandedSection == "max_dos", onToggle = { expandedSection = if (expandedSection == "max_dos") null else "max_dos" }) {
                Text(maxDosage.format(1) + "g", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Slider(value = maxDosage.toFloat(), onValueChange = { viewModel.setWidgetMaxDosage(it.toDouble()) }, valueRange = 0.5f..10.0f, steps = 95)
            }
        }

        item { CollapsibleSettingsCard("Backup & Restore".translate(lang), expandedSection == "backup", onToggle = { expandedSection = if (expandedSection == "backup") null else "backup" }) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { triggerDownload("GreenTracker_Backup.json", viewModel.createBackupJson()) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Save, null); Text("Save") }
                Button(onClick = { triggerFilePicker { viewModel.importBackupJson(it) } }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Publish, null); Text("Import") }
            }
        } }
        item { CollapsibleSettingsCard("Trash".translate(lang), expandedSection == "trash", badgeCount = trashedSessions.size + trashedStrains.size, onToggle = { expandedSection = if (expandedSection == "trash") null else "trash" }) {
            Surface(color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.HourglassBottom, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                    Text("Deleted items are automatically removed after 7 days.".translate(lang), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            
            CollapsibleSubSection("Sessions".translate(lang), isSessionsTrashExpanded, onToggle = { isSessionsTrashExpanded = !isSessionsTrashExpanded }) {
                trashedSessions.forEach { s ->
                    var showPermDeleteConfirm by remember { mutableStateOf(false) }
                    if (showPermDeleteConfirm) {
                        AlertDialog(
                            onDismissRequest = { /* Lock */ },
                            title = { Text("Permanently Delete?".translate(lang)) },
                            text = { Text("This will be gone forever!".translate(lang)) },
                            confirmButton = { Button(onClick = { viewModel.permanentlyDeleteSession(s.id); showPermDeleteConfirm = false }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Delete Forever".translate(lang)) } },
                            dismissButton = { TextButton(onClick = { showPermDeleteConfirm = false }) { Text("Cancel".translate(lang)) } }
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${s.grams}g - ${s.strain}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        IconButton(onClick = { viewModel.restoreSession(s.id) }) { Icon(Icons.Default.Restore, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) }
                        IconButton(onClick = { showPermDeleteConfirm = true }) { Icon(Icons.Default.DeleteForever, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)) }
                    }
                }
            }
            CollapsibleSubSection("Journal".translate(lang), isStrainsTrashExpanded, onToggle = { isStrainsTrashExpanded = !isStrainsTrashExpanded }) {
                trashedStrains.forEach { s ->
                    var showPermDeleteConfirm by remember { mutableStateOf(false) }
                    if (showPermDeleteConfirm) {
                        AlertDialog(
                            onDismissRequest = { /* Lock */ },
                            title = { Text("Permanently Delete?".translate(lang)) },
                            text = { Text("This entry will be removed forever.".translate(lang)) },
                            confirmButton = { Button(onClick = { viewModel.permanentlyDeleteStrain(s.id); showPermDeleteConfirm = false }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Delete Forever".translate(lang)) } },
                            dismissButton = { TextButton(onClick = { showPermDeleteConfirm = false }) { Text("Cancel".translate(lang)) } }
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(s.strainName, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        IconButton(onClick = { viewModel.restoreStrain(s.id) }) { Icon(Icons.Default.Restore, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) }
                        IconButton(onClick = { showPermDeleteConfirm = true }) { Icon(Icons.Default.DeleteForever, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)) }
                    }
                }
            }
            Button(onClick = { viewModel.emptyAllTrash() }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Empty Trash") }
        } }

        item { 
            CollapsibleSettingsCard("App Maintenance".translate(lang), expandedSection == "maint", onToggle = { expandedSection = if (expandedSection == "maint") null else "maint" }) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Check for updates or refresh the app cache.".translate(lang), style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { forceAppUpdate() }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text("Check for Updates / Refresh".translate(lang))
                    }
                }
            }
        }

        item { CollapsibleSettingsCard("Danger Zone".translate(lang), expandedSection == "danger", onToggle = { expandedSection = if (expandedSection == "danger") null else "danger" }) {
            Button(onClick = { showClearAllConfirm = true }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), modifier = Modifier.fillMaxWidth()) { Text("Clear All Data") }
        } }

        item {
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("GreenTracker", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                    Text("Version 0.8", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                }
            }
        }
    }
}

@Composable
fun CollapsibleSubSection(title: String, isExpanded: Boolean, onToggle: () -> Unit, content: @Composable () -> Unit) {
    Column {
        Row(modifier = Modifier.fillMaxWidth().clickable { onToggle() }.padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
            Icon(if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null, modifier = Modifier.size(20.dp))
        }
        if (isExpanded) Column(modifier = Modifier.padding(start = 8.dp)) { content() }
    }
}

@Composable
fun CollapsibleSettingsCard(title: String, isExpanded: Boolean, badgeCount: Int = 0, onToggle: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth().clickable { onToggle() }.padding(20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                    if (badgeCount > 0) Badge { Text("$badgeCount") }
                }
                Icon(if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
            }
            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 20.dp)) { content() }
            }
        }
    }
}

@Composable
fun SessionItemRow(session: SmokeSession, lang: String, viewModel: SmokeViewModel, onDelete: (Long) -> Unit, onEdit: ((SmokeSession) -> Unit)? = null) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { /* Lock */ },
            title = { Text("Delete Session?".translate(lang)) },
            text = { Text("Move this log to the trash?".translate(lang)) },
            confirmButton = { Button(onClick = { onDelete(session.id); showDeleteConfirm = false }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Delete".translate(lang)) } },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel".translate(lang)) } }
        )
    }
    if (showEditDialog) {
        val maxDosage by viewModel.widgetMaxDosage.collectAsState()
        EditSessionDialog(session, lang, maxDosage, onDismiss = { showEditDialog = false }, onSave = { onEdit?.invoke(it); showEditDialog = false })
    }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (session.strain.isNotEmpty()) session.strain else "Smoke Session".translate(lang), fontWeight = FontWeight.Bold)
                    val instant = Instant.fromEpochMilliseconds(session.timestamp)
                    val time = instant.toLocalDateTime(TimeZone.currentSystemDefault()).time
                    Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)) {
                         Text("${time.hour.toString().padStart(2,'0')}:${time.minute.toString().padStart(2,'0')}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 4.dp))
                    }
                }
                if (session.notes.isNotEmpty()) Text(session.notes, style = MaterialTheme.typography.bodySmall)
            }
            Text("+${session.grams.format(1)}g", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
            if (onEdit != null) IconButton(onClick = { showEditDialog = true }) { Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) }
            IconButton(onClick = { showDeleteConfirm = true }) { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp)) }
        }
    }
}

@Composable
fun EditSessionDialog(session: SmokeSession, lang: String, maxDosage: Double, onDismiss: () -> Unit, onSave: (SmokeSession) -> Unit) {
    var grams by remember { mutableStateOf(session.grams) }
    var strain by remember { mutableStateOf(session.strain) }
    var notes by remember { mutableStateOf(session.notes) }
    var timestamp by remember { mutableStateOf(session.timestamp) }

    AlertDialog(
        onDismissRequest = { /* Prevent closing on outside click */ },
        title = { Text("Edit Session".translate(lang), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("Amount: ${grams.format(1)}g", fontWeight = FontWeight.Bold)
                Slider(value = grams.toFloat(), onValueChange = { grams = (it * 10).toInt() / 10.0 }, valueRange = 0.1f..maxDosage.toFloat(), steps = if (maxDosage > 0.1) ((maxDosage - 0.1) * 10).toInt() else 0)
                TextField(value = strain, onValueChange = { strain = it }, label = { Text("Strain") }, modifier = Modifier.fillMaxWidth() )
                TextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth() )
                
                Text("Timestamp".translate(lang), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                val instant = Instant.fromEpochMilliseconds(timestamp)
                val dt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
                
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                         Text("Hour".translate(lang), style = MaterialTheme.typography.labelSmall)
                         Slider(value = dt.hour.toFloat(), onValueChange = { 
                             val newDt = LocalDateTime(dt.year, dt.month, dt.dayOfMonth, it.toInt(), dt.minute, 0, 0)
                             timestamp = newDt.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
                         }, valueRange = 0f..23f, steps = 23)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                         Text("Minute".translate(lang), style = MaterialTheme.typography.labelSmall)
                         Slider(value = dt.minute.toFloat(), onValueChange = { 
                             val newDt = LocalDateTime(dt.year, dt.month, dt.dayOfMonth, dt.hour, it.toInt(), 0, 0)
                             timestamp = newDt.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
                         }, valueRange = 0f..59f, steps = 59)
                    }
                }
                Text("${dt.hour.toString().padStart(2,'0')}:${dt.minute.toString().padStart(2,'0')}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            }
        },
        confirmButton = { Button(onClick = { onSave(session.copy(grams = grams, strain = strain, notes = notes, timestamp = timestamp)) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun AddManualSessionDialog(initialGrams: Double, lang: String, maxDosage: Double, theme: CannabisTheme, onDismiss: () -> Unit, onSave: (Double, String, String, Long) -> Unit) {
    var grams by remember { mutableStateOf(initialGrams) }
    var strain by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var timestamp by remember { mutableStateOf(Clock.System.now().toEpochMilliseconds()) }
    AlertDialog(
        onDismissRequest = { /* Prevent closing on outside click */ },
        title = { Text("Log Session", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("Amount: ${grams.format(1)}g", fontWeight = FontWeight.Bold)
                Slider(value = grams.toFloat(), onValueChange = { grams = (it * 10).toInt() / 10.0 }, valueRange = 0.1f..maxDosage.toFloat(), steps = if (maxDosage > 0.1) ((maxDosage - 0.1) * 10).toInt() else 0)
                
                Text("Time:".translate(lang), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                val instant = Instant.fromEpochMilliseconds(timestamp)
                val dt = instant.toLocalDateTime(TimeZone.currentSystemDefault())

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                         Text("Hour".translate(lang), style = MaterialTheme.typography.labelSmall)
                         Slider(value = dt.hour.toFloat(), onValueChange = { 
                             val newDt = LocalDateTime(dt.year, dt.month, dt.dayOfMonth, it.toInt(), dt.minute, 0, 0)
                             timestamp = newDt.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
                         }, valueRange = 0f..23f, steps = 23)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                         Text("Minute".translate(lang), style = MaterialTheme.typography.labelSmall)
                         Slider(value = dt.minute.toFloat(), onValueChange = { 
                             val newDt = LocalDateTime(dt.year, dt.month, dt.dayOfMonth, dt.hour, it.toInt(), 0, 0)
                             timestamp = newDt.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
                         }, valueRange = 0f..59f, steps = 59)
                    }
                }
                Text("${dt.hour.toString().padStart(2,'0')}:${dt.minute.toString().padStart(2,'0')}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)

                TextField(value = strain, onValueChange = { strain = it }, label = { Text("Strain") }, modifier = Modifier.fillMaxWidth() )
                TextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth() )
            }
        },
        confirmButton = { Button(onClick = { onSave(grams, strain, notes, timestamp) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun AddEditStrainDialog(initial: StrainEntry?, lang: String, onDismiss: () -> Unit, onSave: (String, String, String, Double, Double, String, String, String) -> Unit) {
    var name by remember { mutableStateOf(initial?.strainName ?: "") }
    var prod by remember { mutableStateOf(initial?.producerCultivar ?: "") }
    var cat by remember { mutableStateOf(initial?.category ?: "Hybrid") }
    var photo by remember { mutableStateOf(initial?.photoUri ?: "") }
    var rat by remember { mutableStateOf(initial?.rating ?: "THUMBS_UP") }
    var thcText by remember { mutableStateOf(if (initial != null && initial.thcPercentage > 0) initial.thcPercentage.toString() else "") }
    var cbdText by remember { mutableStateOf(if (initial != null && initial.cbdPercentage > 0) initial.cbdPercentage.toString() else "") }
    var nts by remember { mutableStateOf(initial?.notes ?: "") }
    AlertDialog(
        onDismissRequest = { /* Prevent closing on outside click */ },
        title = { Text(if (initial == null) "Add Strain" else "Edit Strain", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (photo.isNotEmpty()) {
                    Box(modifier = Modifier.size(120.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable { photo = "" }, contentAlignment = Alignment.Center) {
                        val imageBitmap = remember(photo) {
                            try {
                                val base64Data = if (photo.contains(",")) photo.split(",")[1] else photo
                                val bytes = com.example.util.Base64.decode(base64Data)
                                val skImg = org.jetbrains.skia.Image.makeFromEncoded(bytes)
                                skImg.toComposeImageBitmap()
                            } catch (e: Exception) { null }
                        }
                        if (imageBitmap != null) {
                            Image(bitmap = imageBitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            Box(modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape)) {
                                Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        } else {
                            Text("Error loading", fontSize = 10.sp)
                        }
                    }
                } else {
                    Button(onClick = { triggerImagePicker { photo = it } }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.AddAPhoto, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Add Photo")
                    }
                }
                
                TextField(value = name, onValueChange = { name = it }, label = { Text("Name *") }, modifier = Modifier.fillMaxWidth())
                TextField(value = prod, onValueChange = { prod = it }, label = { Text("Producer") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextField(value = thcText, onValueChange = { thcText = it }, label = { Text("THC %") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    TextField(value = cbdText, onValueChange = { cbdText = it }, label = { Text("CBD %") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("Indica", "Sativa", "Hybrid").forEach { c -> FilterChip(selected = cat == c, onClick = { cat = c }, label = { Text(c) }, modifier = Modifier.weight(1f)) }
                }
                Text("Rating".translate(lang), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    SelectableRatingItem(selected = rat == "THUMBS_UP", icon = Icons.Default.ThumbUp, label = "Recommended".translate(lang), color = Color(0xFF2E7D32), onClick = { rat = "THUMBS_UP" }, modifier = Modifier.weight(1f))
                    SelectableRatingItem(selected = rat == "NEUTRAL", icon = Icons.Default.SentimentNeutral, label = "Neutral".translate(lang), color = Color.Gray, onClick = { rat = "NEUTRAL" }, modifier = Modifier.weight(1f))
                    SelectableRatingItem(selected = rat == "THUMBS_DOWN", icon = Icons.Default.ThumbDown, label = "Avoid".translate(lang), color = Color.Red, onClick = { rat = "THUMBS_DOWN" }, modifier = Modifier.weight(1f))
                }
                TextField(value = nts, onValueChange = { nts = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            }
        },
        confirmButton = { Button(enabled = name.isNotBlank(), onClick = { onSave(name, prod, cat, thcText.toDoubleOrNull() ?: 0.0, cbdText.toDoubleOrNull() ?: 0.0, rat, nts, photo) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun SelectableRatingItem(selected: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, color: Color, onClick: () -> Unit, modifier: Modifier) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (selected) color.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) color else Color.Transparent),
        modifier = modifier
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 8.dp)) {
            Icon(icon, null, tint = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

fun triggerDownload(fileName: String, content: String) {
    val jsArray = JsArray<JsAny?>()
    jsArray.set(0, content.toJsString())
    val blob = Blob(jsArray, BlobPropertyBag(type = "application/json"))
    val url = URL.createObjectURL(blob)
    val anchor = document.createElement("a") as HTMLAnchorElement
    anchor.href = url
    anchor.download = fileName
    anchor.click()
    URL.revokeObjectURL(url)
}

fun triggerFilePicker(onContent: (String) -> Unit) {
    val input = document.createElement("input") as HTMLInputElement
    input.type = "file"; input.accept = ".json"
    input.onchange = { val file = input.files?.get(0); if (file != null) { val reader = FileReader(); reader.onload = { onContent(reader.result.toString()) }; reader.readAsText(file) } }
    input.click()
}

fun triggerImagePicker(onBase64: (String) -> Unit) {
    val input = document.createElement("input") as HTMLInputElement
    input.type = "file"; input.accept = "image/*"
    input.onchange = { 
        val file = input.files?.get(0)
        if (file != null) { 
            val reader = FileReader()
            reader.onload = { 
                val base64 = reader.result.toString()
                onBase64(base64) 
            }
            reader.readAsDataURL(file) 
        } 
    }
    input.click()
}
