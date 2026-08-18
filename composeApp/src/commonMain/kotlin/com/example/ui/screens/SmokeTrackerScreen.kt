@file:OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
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
import com.example.ui.viewmodel.SmokeViewModel
import com.example.ui.viewmodel.translate
import com.example.ui.viewmodel.format
import com.example.ui.viewmodel.roundToDecimals
import com.example.ui.viewmodel.DayStat
import com.example.util.Base64
import kotlinx.coroutines.launch
import kotlinx.datetime.*
import kotlinx.browser.document
import kotlin.math.roundToInt
import kotlinx.browser.window
import org.w3c.dom.HTMLAnchorElement
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag
import org.w3c.dom.url.URL
import org.w3c.dom.HTMLInputElement
import org.w3c.files.FileReader
import org.w3c.files.get
import kotlin.js.*

import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.painterResource
import greentrackerweb.composeapp.generated.resources.*

enum class AppTab {
    HOME, HISTORY, STATS, JOURNAL, SETTINGS, PRIVACY
}

enum class HistoryFilter {
    ALL, WEEK, MONTH, YEAR, CUSTOM
}

@JsFun("(onDate) => { const input = document.createElement('input'); input.type = 'date'; input.onchange = (e) => { const date = e.target.value; if (date) { onDate(date); } }; input.click(); }")
external fun triggerWebDatePicker(onDate: (String) -> Unit): Unit

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

@JsFun("() => { return window.matchMedia('(display-mode: standalone)').matches || window.navigator.standalone === true; }")
external fun isStandalone(): Boolean

@JsFun("() => { return /iPhone|iPad|iPod/i.test(window.navigator.userAgent); }")
external fun isIos(): Boolean

@JsFun("() => { return /Android/i.test(window.navigator.userAgent); }")
external fun isAndroid(): Boolean

@Composable
fun getAppIconPainter(index: Int): androidx.compose.ui.graphics.painter.Painter {
    return when (index) {
        2 -> painterResource(Res.drawable.ic_app_icon_2)
        3 -> painterResource(Res.drawable.ic_app_icon_3)
        4 -> painterResource(Res.drawable.ic_app_icon_4)
        5 -> painterResource(Res.drawable.ic_app_icon_5)
        6 -> painterResource(Res.drawable.ic_app_icon_6)
        else -> painterResource(Res.drawable.ic_app_icon_1)
    }
}

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
    val activeAppIconIndex by viewModel.activeAppIconIndex.collectAsState()

    var currentTab by remember { mutableStateOf(AppTab.HOME) }
    var showAddManualDialog by remember { mutableStateOf(false) }
    var manualAddInitialGrams by remember { mutableDoubleStateOf(0.0) }
    var showAddStrainDialog by remember { mutableStateOf(false) }
    var editingStrain by remember { mutableStateOf<StrainEntry?>(null) }
    var zoomedPhoto by remember { mutableStateOf<String?>(null) }
    var expandedSection by remember { mutableStateOf<String?>(null) }

    var logoTapCount by remember { mutableStateOf(0) }
    var lastLogoTapTime by remember { mutableLongStateOf(0L) }
    var showRain by remember { mutableStateOf(false) }

    LaunchedEffect(logoTapCount) {
        if (logoTapCount >= 3) {
            showRain = true
        }
    }

    LaunchedEffect(showRain) {
        if (showRain) {
            kotlinx.coroutines.delay(3000L)
            showRain = false
            logoTapCount = 0
        }
    }

    var showLimitConfirmDialog by remember { mutableStateOf(false) }
    var pendingLogGrams by remember { mutableDoubleStateOf(0.0) }
    var pendingLogStrain by remember { mutableStateOf("") }
    var pendingLogNotes by remember { mutableStateOf("") }
    var pendingLogTimestamp by remember { mutableLongStateOf(0L) }

    fun requestLog(g: Double, s: String, n: String, t: Long) {
        val todayStart = viewModel.getStartOfLogicalDay(Clock.System.now().toEpochMilliseconds(), viewModel.dayRhythmHours.value)
        val todayEnd = todayStart + 24 * 60 * 60 * 1000L - 1L
        val totalTodayGrams = allSessions.filter { it.timestamp in todayStart..todayEnd }.sumOf { it.grams }

        if ((totalTodayGrams + g) > dailyGoalGrams) {
            pendingLogGrams = g
            pendingLogStrain = s
            pendingLogNotes = n
            pendingLogTimestamp = t
            showLimitConfirmDialog = true
        } else {
            viewModel.logSession(g, s, n, t)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = if (activeTheme == CannabisTheme.PRIDE) Color(0xFF0F0F1A) else MaterialTheme.colorScheme.background,
        topBar = {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Image(
                            painter = getAppIconPainter(activeAppIconIndex),
                            contentDescription = "App Icon",
                            modifier = Modifier.size(36.dp).clip(CircleShape).clickable {
                                val now = Clock.System.now().toEpochMilliseconds()
                                if (now - lastLogoTapTime < 500) logoTapCount++ else logoTapCount = 1
                                lastLogoTapTime = now
                            }
                        )
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
                    BottomNavItem(Icons.AutoMirrored.Filled.List, "History".translate(activeLanguage), currentTab == AppTab.HISTORY) { currentTab = AppTab.HISTORY }
                    BottomNavItem(Icons.Default.Assessment, "Stats".translate(activeLanguage), currentTab == AppTab.STATS) { currentTab = AppTab.STATS }
                    BottomNavItem(Icons.AutoMirrored.Filled.MenuBook, "Journal".translate(activeLanguage), currentTab == AppTab.JOURNAL) { currentTab = AppTab.JOURNAL }
                    val totalTrashedCount = trashedSessions.size + trashedStrains.size
                    BottomNavItem(Icons.Default.Settings, "Settings".translate(activeLanguage), currentTab == AppTab.SETTINGS, totalTrashedCount) { currentTab = AppTab.SETTINGS }
                }
            }
        },
        floatingActionButton = {
            if (currentTab == AppTab.HISTORY || currentTab == AppTab.JOURNAL) {
                FloatingActionButton(
                    onClick = {
                        if (currentTab == AppTab.HISTORY) { manualAddInitialGrams = 0.0; showAddManualDialog = true }
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
                AppTab.HOME -> {
                    HomeScreen(
                        viewModel, 
                        activeTheme, 
                        sessionsToday, 
                        dailyGoalGrams, 
                        activeLanguage, 
                        activeAppIconIndex, 
                        onLogRequest = { g, s, n, t -> requestLog(g, s, n, t) },
                        onNavigateToSettings = { currentTab = AppTab.SETTINGS; expandedSection = it }
                    )
                }
                AppTab.HISTORY -> HistoryScreen(allSessions, activeLanguage, viewModel, activeAppIconIndex)
                AppTab.STATS -> StatsScreen(weeklyStats, allSessions, activeLanguage, activeAppIconIndex, viewModel)
                AppTab.JOURNAL -> JournalScreen(allStrains, activeLanguage, viewModel, activeAppIconIndex, onEdit = { editingStrain = it; showAddStrainDialog = true }, onZoom = { zoomedPhoto = it })
                AppTab.SETTINGS -> SettingsScreen(viewModel, activeTheme, dailyGoalGrams, activeLanguage, trashedSessions, trashedStrains, activeAppIconIndex, expandedSection, onToggleSection = { expandedSection = it }, onNavigateToPrivacy = { currentTab = AppTab.PRIVACY })
                AppTab.PRIVACY -> PrivacyPolicyScreen(activeLanguage, onBack = { currentTab = AppTab.SETTINGS })
            }
        }
    }

    if (showAddManualDialog) {
        val maxDosage by viewModel.widgetMaxDosage.collectAsState()
        AddManualSessionDialog(
            manualAddInitialGrams, 
            activeLanguage, 
            maxDosage, 
            activeTheme, 
            onDismiss = { showAddManualDialog = false },
            allStrains = allStrains
        ) { g, s, n, t ->
            requestLog(g, s, n, t)
            showAddManualDialog = false
        }
    }

    if (showLimitConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showLimitConfirmDialog = false },
            title = { Text("Daily Limit Reached".translate(activeLanguage), fontWeight = FontWeight.Bold) },
            text = { Text("Daily limit exceeded! Log anyway?".translate(activeLanguage)) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.logSession(pendingLogGrams, pendingLogStrain, pendingLogNotes, pendingLogTimestamp)
                        showLimitConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Log Anyway".translate(activeLanguage), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLimitConfirmDialog = false }) {
                    Text("Cancel".translate(activeLanguage))
                }
            }
        )
    }

    if (showAddStrainDialog) {
        AddEditStrainDialog(editingStrain, activeLanguage, viewModel, onDismiss = { showAddStrainDialog = false }) { n, p, c, thc, cbd, r, nt, photo, nr ->
            if (editingStrain == null) viewModel.addStrain(n, p, c, thc, cbd, r, nt, photo, nr)
            else viewModel.updateStrain(editingStrain!!.copy(strainName = n, producerCultivar = p, category = c, thcPercentage = thc, cbdPercentage = cbd, rating = r, notes = nt, photoUri = photo, needsReview = nr))
            showAddStrainDialog = false
        }
    }

    if (zoomedPhoto != null) {
        ZoomedPhotoDialog(zoomedPhoto!!, onDismiss = { zoomedPhoto = null })
    }

    if (showRain) {
        CannabisRainOverlay(activeAppIconIndex)
    }
}

@Composable
fun CannabisRainOverlay(activeAppIconIndex: Int) {
    val infiniteTransition = rememberInfiniteTransition(label = "cannabisRain")
    Box(modifier = Modifier.fillMaxSize()) {
        repeat(15) { index ->
            val startX = remember { (0..100).random() / 100f }
            val delay = remember { (0..2000).random() }
            val yPos by infiniteTransition.animateFloat(initialValue = -0.2f, targetValue = 1.2f, animationSpec = infiniteRepeatable(animation = tween(durationMillis = 3000, easing = LinearEasing, delayMillis = delay), repeatMode = RepeatMode.Restart), label = "yPos$index")
            val rotation by infiniteTransition.animateFloat(initialValue = 0f, targetValue = 360f, animationSpec = infiniteRepeatable(animation = tween(durationMillis = 2000, easing = LinearEasing), repeatMode = RepeatMode.Restart), label = "rotation$index")
            Box(modifier = Modifier.fillMaxSize().padding(start = (startX * 400).dp)) {
                Image(painter = getAppIconPainter(activeAppIconIndex), contentDescription = null, modifier = Modifier.size(40.dp).offset(y = (yPos * 800).dp).rotate(rotation))
            }
        }
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
                    bytes.decodeToImageBitmap()
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
fun HomeScreen(
    viewModel: SmokeViewModel, 
    activeTheme: CannabisTheme, 
    sessionsToday: List<SmokeSession>, 
    dailyGoalGrams: Double, 
    lang: String, 
    activeAppIconIndex: Int, 
    onLogRequest: (Double, String, String, Long) -> Unit,
    onNavigateToSettings: (String) -> Unit
) {
    val totalTodayGrams = sessionsToday.sumOf { it.grams }
    val progress = if (dailyGoalGrams > 0.0) (totalTodayGrams / dailyGoalGrams).toFloat().coerceIn(0f, 1f) else 0f
    val quickLogGrams by viewModel.quickLogGrams.collectAsState()
    val maxDosage by viewModel.widgetMaxDosage.collectAsState()

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)) {
        item {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(text = "Today's Consumption".translate(lang), style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold))
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                        Column {
                            Row(verticalAlignment = Alignment.Bottom) {
                                val bigGramColor = if (totalTodayGrams > dailyGoalGrams) MaterialTheme.colorScheme.error else Color(0xFF2E7D32)
                                Text(text = totalTodayGrams.format(1, lang), style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Black, color = bigGramColor))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = "g", style = MaterialTheme.typography.titleMedium.copy(color = bigGramColor, fontWeight = FontWeight.Bold), modifier = Modifier.padding(bottom = 8.dp))
                            }
                            val excess = totalTodayGrams - dailyGoalGrams
                            if (excess > 0.0) {
                                Text(
                                    text = "(${"Excess".translate(lang)}: +${excess.format(1, lang)}g)",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(bottom = 4.dp)) {
                            Text(text = "Sessions".translate(lang), style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold))
                            Text(text = "${sessionsToday.size}", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface))
                        }
                    }
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)), color = if (totalTodayGrams > dailyGoalGrams) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "0.0g", style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), fontWeight = FontWeight.Bold))
                        Text(text = "Max.:".translate(lang) + " ${dailyGoalGrams.format(1, lang)}g", style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold))
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)), border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(text = "Log".translate(lang), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onPrimaryContainer))
                                Image(painter = getAppIconPainter(activeAppIconIndex), contentDescription = null, modifier = Modifier.size(18.dp).clip(CircleShape))
                            }
                            Text(text = "Slide to set log amount".translate(lang), style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)))
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(text = "${quickLogGrams.format(1, lang)}g", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary))
                            Button(
                                onClick = { onLogRequest(quickLogGrams, "", "Quick logged from Dashboard".translate(lang), Clock.System.now().toEpochMilliseconds()) }, 
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = "loggen".translate(lang), fontWeight = FontWeight.Black, fontSize = 14.sp)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                        value = quickLogGrams.toFloat(), 
                        onValueChange = { viewModel.setQuickLogGrams(it.toDouble()) }, 
                        valueRange = 0.1f..maxDosage.toFloat().coerceAtLeast(0.1f), 
                        steps = ((maxDosage - 0.1) / 0.1).toInt().coerceAtLeast(0),
                        colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }

        item { Text("Today's Logs Preview".translate(lang), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)) }
        items(sessionsToday.take(5), key = { it.id }) { sItem ->
            SessionItemRow(sItem, lang, viewModel, onDelete = { viewModel.deleteSession(sItem.id) }, onEdit = { viewModel.updateSession(it) })
        }
    }
}

@Composable
fun HistoryScreen(allSessions: List<SmokeSession>, lang: String, viewModel: SmokeViewModel, activeAppIconIndex: Int) {
    var filter by remember { mutableStateOf(HistoryFilter.ALL) }
    val dayRhythm by viewModel.dayRhythmHours.collectAsState()
    var customStartDate by remember { mutableStateOf<Long?>(null) }
    var customEndDate by remember { mutableStateOf<Long?>(null) }

    val filtered = remember(allSessions, filter, customStartDate, customEndDate) {
        val now = Clock.System.now().toEpochMilliseconds()
        when (filter) {
            HistoryFilter.ALL -> allSessions
            HistoryFilter.WEEK -> allSessions.filter { it.timestamp > now - 7 * 24 * 3600 * 1000L }
            HistoryFilter.MONTH -> allSessions.filter { it.timestamp > now - 30 * 24 * 3600 * 1000L }
            HistoryFilter.YEAR -> allSessions.filter { it.timestamp > now - 365 * 24 * 3600 * 1000L }
            HistoryFilter.CUSTOM -> allSessions.filter { s ->
                val after = customStartDate?.let { s.timestamp >= it } ?: true
                val before = customEndDate?.let { s.timestamp <= it + 24 * 3600 * 1000L - 1L } ?: true
                after && before
            }
        }
    }

    val grouped = remember(filtered, dayRhythm) {
        filtered.sortedByDescending { it.timestamp }.groupBy { 
            val inst = Instant.fromEpochMilliseconds(it.timestamp - dayRhythm * 3600 * 1000L)
            inst.toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
        }.toList()
    }

    val todayDate = remember(dayRhythm) {
        val inst = Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds() - dayRhythm * 3600 * 1000L)
        inst.toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
    }
    val yesterdayDate = remember(dayRhythm) {
        val inst = Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds() - (24 + dayRhythm) * 3600 * 1000L)
        inst.toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
    }

    var expandedDates by rememberSaveable { mutableStateOf(setOf(todayDate)) }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HistoryFilter.entries.forEach { f ->
                val label = when (f) {
                    HistoryFilter.ALL -> "All"; HistoryFilter.WEEK -> "WEEK"; HistoryFilter.MONTH -> "MONTH"; HistoryFilter.YEAR -> "YEAR"; HistoryFilter.CUSTOM -> "Custom Range"
                }.translate(lang)
                FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(label) })
            }
        }

        if (filter == HistoryFilter.CUSTOM) {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { triggerWebDatePicker { d -> try { customStartDate = LocalDate.parse(d).atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds() } catch(e:Exception){} } }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                    Text(customStartDate?.let { "From: ".translate(lang) + Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.currentSystemDefault()).date.toString() } ?: "Select Start Date".translate(lang), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
                Button(onClick = { triggerWebDatePicker { d -> try { customEndDate = LocalDate.parse(d).atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds() } catch(e:Exception){} } }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                    Text(customEndDate?.let { "To: ".translate(lang) + Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.currentSystemDefault()).date.toString() } ?: "Select End Date".translate(lang), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            grouped.forEach { (date, sessions) ->
                val isExpanded = expandedDates.contains(date)
                val dayTitle = when (date) { todayDate -> "Today".translate(lang); yesterdayDate -> "Yesterday".translate(lang); else -> date }
                item {
                    Row(modifier = Modifier.fillMaxWidth().clickable { expandedDates = if (isExpanded) expandedDates - date else expandedDates + date }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(dayTitle, fontWeight = FontWeight.Bold, color = if (date == todayDate) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        Icon(if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
                if (isExpanded) {
                    items(sessions) { sItem -> 
                        SessionItemRow(sItem, lang, viewModel, onDelete = { viewModel.deleteSession(sItem.id) }, onEdit = { viewModel.updateSession(it) })
                    }
                }
            }
        }
    }
}

@Composable
fun StatsScreen(
    weeklyStats: List<DayStat>,
    allSessions: List<SmokeSession>,
    lang: String,
    activeAppIconIndex: Int,
    viewModel: SmokeViewModel
) {
    var selectedFilter by remember { mutableStateOf(HistoryFilter.WEEK) }
    var customStartDate by remember { mutableStateOf<Long?>(null) }
    var customEndDate by remember { mutableStateOf<Long?>(null) }
    val scope = rememberCoroutineScope()
    val dayRhythm by viewModel.dayRhythmHours.collectAsState()
    val dailyGoalGrams by viewModel.dailyGoalGrams.collectAsState()

    val filteredSessions = remember(allSessions, selectedFilter, customStartDate, customEndDate) {
        when (selectedFilter) {
            HistoryFilter.ALL -> allSessions
            HistoryFilter.WEEK -> {
                val now = Clock.System.now().toEpochMilliseconds()
                val startOfWeek = viewModel.getStartOfLogicalDay(now, dayRhythm) - 6 * 24 * 3600 * 1000L
                allSessions.filter { it.timestamp >= startOfWeek }
            }
            HistoryFilter.MONTH -> {
                val now = Clock.System.now().toEpochMilliseconds()
                val startOfMonth = viewModel.getStartOfLogicalDay(now, dayRhythm) - 30 * 24 * 3600 * 1000L
                allSessions.filter { it.timestamp >= startOfMonth }
            }
            HistoryFilter.YEAR -> {
                val now = Clock.System.now().toEpochMilliseconds()
                val startOfYear = viewModel.getStartOfLogicalDay(now, dayRhythm) - 365 * 24 * 3600 * 1000L
                allSessions.filter { it.timestamp >= startOfYear }
            }
            HistoryFilter.CUSTOM -> {
                allSessions.filter { session ->
                    val afterStart = customStartDate?.let { session.timestamp >= it } ?: true
                    val beforeEnd = customEndDate?.let { session.timestamp <= it + 24 * 3600 * 1000L } ?: true
                    afterStart && beforeEnd
                }
            }
        }
    }

    val totalGrams = filteredSessions.sumOf { it.grams }.roundToDecimals(1)
    val sessionsCount = filteredSessions.size
    val daysCount = if (filteredSessions.isEmpty()) 1 else filteredSessions.map { viewModel.getStartOfLogicalDay(it.timestamp, dayRhythm) }.toSet().size
    val dailyAverage = (totalGrams / daysCount.coerceAtLeast(1)).roundToDecimals(1)

    val summaryTitle = when (selectedFilter) {
        HistoryFilter.ALL -> "SINCE FIRST LOG"
        HistoryFilter.WEEK -> "WEEK SUMMARY"
        HistoryFilter.MONTH -> "MONTH SUMMARY"
        HistoryFilter.YEAR -> "YEAR SUMMARY"
        else -> "CUSTOM SUMMARY"
    }.translate(lang)

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 40.dp)
    ) {
        item {
            Text(
                text = "Usage Analytics".translate(lang),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        item {
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HistoryFilter.entries.forEach { filter ->
                    val label = when (filter) {
                        HistoryFilter.ALL -> "Since First Log"
                        HistoryFilter.WEEK -> "This Week"
                        HistoryFilter.MONTH -> "This Month"
                        HistoryFilter.YEAR -> "This Year"
                        HistoryFilter.CUSTOM -> "Custom Range"
                    }.translate(lang)
                    
                    FilterTabChip(label, selectedFilter == filter) { 
                        if (filter == HistoryFilter.ALL) {
                            scope.launch {
                                val first = viewModel.getFirstSessionTimestamp()
                                if (first != null) {
                                    customStartDate = first
                                    customEndDate = Clock.System.now().toEpochMilliseconds()
                                    selectedFilter = HistoryFilter.CUSTOM
                                } else { selectedFilter = HistoryFilter.ALL }
                            }
                        } else { selectedFilter = filter }
                    }
                }
            }
        }

        if (selectedFilter == HistoryFilter.CUSTOM) {
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { triggerWebDatePicker { d -> try { customStartDate = LocalDate.parse(d).atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds() } catch(e:Exception){} } }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)) {
                        Text(customStartDate?.let { Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.currentSystemDefault()).let { d -> "${d.dayOfMonth}.${d.monthNumber}.${d.year}" } } ?: "Select Start Date".translate(lang), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(onClick = { triggerWebDatePicker { d -> try { customEndDate = LocalDate.parse(d).atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds() } catch(e:Exception){} } }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)) {
                        Text(customEndDate?.let { Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.currentSystemDefault()).let { d -> "${d.dayOfMonth}.${d.monthNumber}.${d.year}" } } ?: "Select End Date".translate(lang), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Weekly Trends (Last 7 Days)".translate(lang), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                        Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), shape = RoundedCornerShape(12.dp)) {
                            Text(text = "Total: ".translate(lang) + "${totalGrams.format(1, lang)}g", modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32)))
                        }
                    }
                    Spacer(modifier = Modifier.height(28.dp))
                    
                    if (selectedFilter == HistoryFilter.WEEK) {
                        BarChart(weeklyStats, dailyGoalGrams, lang, modifier = Modifier.fillMaxWidth().height(200.dp))
                    } else {
                        val filterStats = remember(filteredSessions, dayRhythm) {
                            val map = mutableMapOf<String, DayStat>()
                            filteredSessions.forEach { session ->
                                val logicalTime = viewModel.getStartOfLogicalDay(session.timestamp, dayRhythm)
                                val dt = Instant.fromEpochMilliseconds(logicalTime).toLocalDateTime(TimeZone.currentSystemDefault())
                                val key = "${dt.dayOfMonth}.${dt.monthNumber}"
                                val existing = map[key]
                                if (existing == null) {
                                    map[key] = DayStat(key, 1, session.grams, logicalTime)
                                } else {
                                    map[key] = existing.copy(sessionsCount = existing.sessionsCount + 1, totalGrams = (existing.totalGrams + session.grams).roundToDecimals(1))
                                }
                            }
                            map.values.sortedBy { it.dateTimestamp }
                        }
                        LineChart(filterStats, dailyGoalGrams, lang, modifier = Modifier.fillMaxWidth().height(200.dp))
                    }
                }
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatInsightCard("Daily Average".translate(lang), dailyAverage.format(1, lang), Icons.Default.FilterVintage, Color(0xFF2E7D32), activeAppIconIndex, modifier = Modifier.weight(1f), subCaption = "Logged".translate(lang) + ": $daysCount " + "Days".translate(lang))
                StatInsightCard("Total Sessions".translate(lang), "$sessionsCount", Icons.Default.Assessment, MaterialTheme.colorScheme.primary, activeAppIconIndex, modifier = Modifier.weight(1f))
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(summaryTitle, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Sessions".translate(lang), style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                            Text("$sessionsCount", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black))
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Consumption".translate(lang), style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                            Text("${totalGrams.format(1, lang)}g", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, color = Color(0xFF2E7D32)))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FilterTabChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(12.dp),
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
    ) {
        Text(text = label, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
    }
}

@Composable
fun BarChart(stats: List<DayStat>, dailyGoalGrams: Double, lang: String, modifier: Modifier = Modifier) {
    var pressedStat by remember { mutableStateOf<DayStat?>(null) }
    Box(modifier = modifier) {
        Row(modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceEvenly) {
            stats.forEach { stat ->
                val maxVal = (stats.maxOfOrNull { it.totalGrams } ?: 1.0).coerceAtLeast(0.1)
                val heightFrac = (stat.totalGrams / maxVal).toFloat().coerceIn(0f, 1f)
                val isExceeded = stat.totalGrams > dailyGoalGrams
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom, modifier = Modifier.weight(1f).fillMaxHeight().pointerInput(stat) {
                    detectTapGestures(onPress = { pressedStat = stat; try { awaitRelease() } finally { pressedStat = null } })
                }) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                        Box(modifier = Modifier.fillMaxHeight(heightFrac.coerceAtLeast(0.05f)).width(20.dp).clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)).background(if (stat.totalGrams > 0) { if (isExceeded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary } else { MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f) }))
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = stat.dayLabel.translate(lang), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
                }
            }
        }
        if (pressedStat != null && pressedStat!!.totalGrams > 0) {
            Surface(modifier = Modifier.align(Alignment.TopCenter).offset(y = (-45).dp), color = if (pressedStat!!.totalGrams > dailyGoalGrams) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(8.dp), tonalElevation = 8.dp) {
                Text(text = "${pressedStat!!.totalGrams.format(1, lang)}g", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = Color.White, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            }
        }
    }
}

@Composable
fun LineChart(stats: List<DayStat>, dailyGoalGrams: Double, lang: String, modifier: Modifier = Modifier) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val errorColor = MaterialTheme.colorScheme.error
    val outlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    var pressedStat by remember { mutableStateOf<DayStat?>(null) }
    var touchPoint by remember { mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) }

    Column(modifier = modifier) {
        Box(modifier = Modifier.weight(1f)) {
            Canvas(modifier = Modifier.fillMaxSize().pointerInput(stats) {
                detectTapGestures(onPress = { offset ->
                    if (stats.isEmpty()) return@detectTapGestures
                    val spacing = size.width.toFloat() / (stats.size - 1).coerceAtLeast(1)
                    val index = (offset.x / spacing).roundToInt().coerceIn(0, stats.size - 1)
                    pressedStat = stats[index]; touchPoint = offset
                    try { awaitRelease() } finally { pressedStat = null; touchPoint = null }
                })
            }) {
                if (stats.isEmpty()) return@Canvas
                val maxVal = (stats.maxOfOrNull { it.totalGrams } ?: 1.0).coerceAtLeast(1.0).toFloat()
                val spacing = size.width / (stats.size - 1).coerceAtLeast(1)
                val points = stats.mapIndexed { index, stat ->
                    val x = index * spacing
                    val y = size.height - (stat.totalGrams.toFloat() / maxVal * size.height)
                    androidx.compose.ui.geometry.Offset(x, y)
                }
                for (i in 0..4) {
                    val y = size.height - (i / 4f * size.height)
                    drawLine(outlineColor, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), strokeWidth = 1.dp.toPx())
                }
                val path = androidx.compose.ui.graphics.Path().apply {
                    if (points.isNotEmpty()) {
                        moveTo(points[0].x, points[0].y)
                        for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
                    }
                }
                drawPath(path, primaryColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                points.forEachIndexed { index, point ->
                    val isExceeded = stats[index].totalGrams > dailyGoalGrams
                    drawCircle(if (isExceeded) errorColor else primaryColor, radius = 4.dp.toPx(), center = point)
                    drawCircle(Color.White, radius = 1.5.dp.toPx(), center = point)
                }
            }
            if (pressedStat != null && touchPoint != null) {
                Surface(modifier = Modifier.align(Alignment.TopCenter).offset(y = (-40).dp), color = if (pressedStat!!.totalGrams > dailyGoalGrams) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(8.dp), tonalElevation = 6.dp) {
                    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = pressedStat!!.dayLabel, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
                        Text(text = "${pressedStat!!.totalGrams.format(1, lang)}g", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
                    }
                }
            }
        }
        if (stats.size >= 2) {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stats.first().dayLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stats.last().dayLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun StatInsightCard(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, appIconIndex: Int? = null, modifier: Modifier = Modifier, subCaption: String? = null) {
    Card(modifier = modifier, shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(color.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                if (appIconIndex != null) {
                    Image(painter = getAppIconPainter(appIconIndex), contentDescription = null, modifier = Modifier.size(20.dp).clip(CircleShape))
                } else {
                    Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
                }
            }
            Text(title, style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (subCaption != null) Text(subCaption, style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)))
        }
    }
}

@Composable
fun JournalScreen(strains: List<StrainEntry>, lang: String, viewModel: SmokeViewModel, activeAppIconIndex: Int, onEdit: (StrainEntry) -> Unit, onZoom: (String) -> Unit) {
    val listState = rememberLazyListState()
    var search by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var selectedRating by remember { mutableStateOf("All") }
    LaunchedEffect(strains.size) { if (strains.isNotEmpty()) listState.animateScrollToItem(0) }
    val filtered = strains.filter { s ->
        (s.strainName.contains(search, ignoreCase = true) || s.producerCultivar.contains(search, ignoreCase = true) || s.category.contains(search, ignoreCase = true)) &&
        (selectedCategory == "All" || s.category == selectedCategory) && 
        (when(selectedRating) {
            "THUMBS_UP" -> s.rating == "THUMBS_UP" && !s.needsReview
            "NEUTRAL" -> s.rating == "NEUTRAL" && !s.needsReview
            "THUMBS_DOWN" -> s.rating == "THUMBS_DOWN" && !s.needsReview
            "NEEDS_REVIEW" -> s.needsReview
            else -> true
        })
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Search strains or producers...".translate(lang)) }, leadingIcon = { Icon(Icons.Default.Search, null) }, shape = RoundedCornerShape(16.dp))
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("All", "Indica", "Sativa", "Hybrid").forEach { cat ->
                FilterChip(selected = selectedCategory == cat, onClick = { selectedCategory = cat }, label = { Text(cat.translate(lang)) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = when(cat) { "Indica" -> Color(0xFF6A1B9A); "Sativa" -> Color(0xFFE65100); "Hybrid" -> Color(0xFF1B5E20); else -> MaterialTheme.colorScheme.primary }, selectedLabelColor = Color.White))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("All", "NEEDS_REVIEW", "THUMBS_UP", "NEUTRAL", "THUMBS_DOWN").forEach { rate ->
                val (label, icon) = when(rate) { 
                    "THUMBS_UP" -> "Recommended" to Icons.Default.ThumbUp 
                    "NEUTRAL" -> "Neutral" to Icons.Default.SentimentNeutral 
                    "THUMBS_DOWN" -> "Avoid" to Icons.Default.ThumbDown 
                    "NEEDS_REVIEW" -> "Needs Review" to Icons.Default.Timer
                    else -> "All Ratings" to Icons.Default.Star 
                }
                FilterChip(selected = selectedRating == rate, onClick = { selectedRating = rate }, label = { Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(label.translate(lang)) } })
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 80.dp)) {
            items(filtered) { strain ->
                var showDeleteConfirm by remember { mutableStateOf(false) }
                if (showDeleteConfirm) {
                    AlertDialog(onDismissRequest = {}, title = { Text("Delete Entry?".translate(lang)) }, text = { Text("Move this entry to the trash?".translate(lang)) }, confirmButton = { Button(onClick = { viewModel.deleteStrain(strain.id); showDeleteConfirm = false }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Delete".translate(lang)) } }, dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel".translate(lang)) } })
                }
                Card(shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        // Left: Photo + Rating Badge
                        Box(modifier = Modifier.size(72.dp)) {
                            Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable { if (strain.photoUri.isNotEmpty()) onZoom(strain.photoUri) }, contentAlignment = Alignment.Center) {
                                if (strain.photoUri.isNotEmpty()) {
                                    val bitmap = remember(strain.photoUri) { try { val base64Data = if (strain.photoUri.contains(",")) strain.photoUri.split(",")[1] else strain.photoUri; com.example.util.Base64.decode(base64Data).decodeToImageBitmap() } catch (e: Exception) { null } }
                                    if (bitmap != null) Image(bitmap = bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                    else Image(painter = getAppIconPainter(activeAppIconIndex), contentDescription = null, modifier = Modifier.size(36.dp).clip(CircleShape))
                                } else Image(painter = getAppIconPainter(activeAppIconIndex), contentDescription = null, modifier = Modifier.size(36.dp).clip(CircleShape))
                            }
                            // Rating Badge overlay
                            val (rIcon, rColor) = when {
                                strain.needsReview -> Icons.Default.Timer to MaterialTheme.colorScheme.tertiary
                                strain.rating == "THUMBS_DOWN" -> Icons.Default.ThumbDown to Color.Red
                                strain.rating == "NEUTRAL" -> Icons.Default.SentimentNeutral to Color.Gray
                                else -> Icons.Default.ThumbUp to Color(0xFF2E7D32)
                            }
                            Surface(shape = CircleShape, color = rColor, modifier = Modifier.size(24.dp).align(Alignment.BottomEnd).offset(x = 4.dp, y = 4.dp), border = BorderStroke(2.dp, MaterialTheme.colorScheme.surface)) {
                                Icon(rIcon, null, modifier = Modifier.padding(4.dp), tint = Color.White)
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        // Center: Content
                        Column(modifier = Modifier.weight(1f)) {
                            Text(strain.strainName, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                            if (strain.producerCultivar.isNotEmpty()) Text(strain.producerCultivar, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                // Category Badge
                                val catColor = when(strain.category) { "Indica" -> Color(0xFF9C27B0); "Sativa" -> Color(0xFFFF9800); else -> Color(0xFF4CAF50) }
                                JournalBadge(strain.category, catColor)
                                
                                if (strain.thcPercentage > 0) JournalBadge("${strain.thcPercentage.toInt()}% THC", MaterialTheme.colorScheme.secondary)
                                if (strain.cbdPercentage > 0) JournalBadge("${strain.cbdPercentage.toInt()}% CBD", MaterialTheme.colorScheme.tertiary)
                            }

                            if (strain.notes.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(strain.notes, style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }

                        // Right: Actions
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(onClick = { onEdit(strain) }, modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape)) {
                                Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            }
                            IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f), CircleShape)) {
                                Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun JournalBadge(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = color.copy(alpha = 0.18f),
        border = BorderStroke(1.5.dp, color.copy(alpha = 0.5f))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black, color = color, fontSize = 12.sp)
        )
    }
}

@Composable
fun SettingsScreen(viewModel: SmokeViewModel, activeTheme: CannabisTheme, dailyGoalGrams: Double, lang: String, trashedSessions: List<SmokeSession>, trashedStrains: List<StrainEntry>, activeAppIconIndex: Int, expandedSection: String?, onToggleSection: (String?) -> Unit, onNavigateToPrivacy: () -> Unit) {
    val dayRhythm by viewModel.dayRhythmHours.collectAsState()
    var isSessionsTrashExpanded by remember { mutableStateOf(false) }
    var isStrainsTrashExpanded by remember { mutableStateOf(false) }
    var showClearAllConfirm by remember { mutableStateOf(false) }

    if (showClearAllConfirm) {
        AlertDialog(onDismissRequest = {}, title = { Text("Clear All Data?".translate(lang), color = MaterialTheme.colorScheme.error) }, text = { Text("This will permanently delete all logs and entries. This action cannot be undone!".translate(lang)) }, confirmButton = { Button(onClick = { viewModel.clearAll(); showClearAllConfirm = false }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Delete Everything".translate(lang)) } }, dismissButton = { TextButton(onClick = { showClearAllConfirm = false }) { Text("Cancel".translate(lang)) } })
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp)) {
        if (!isStandalone()) {
            val isUserOnIos = isIos()
            val isUserOnAndroid = isAndroid()

            if (isUserOnIos || isUserOnAndroid) {
                item {
                    CollapsibleSettingsCard(
                        title = "Install App".translate(lang),
                        isExpanded = expandedSection == "install",
                        onToggle = { onToggleSection(if (expandedSection == "install") null else "install") }
                    ) {
                        if (isUserOnIos) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("How to install GreenTracker on your device:".translate(lang), fontWeight = FontWeight.Bold)
                                Text("1. Tap the Share button (square with arrow up)")
                                Text("2. Scroll down and select 'Add to Home Screen'")
                                Text("This enables features like Push Notifications on iOS.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        } else if (isUserOnAndroid) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                Text(
                    text = "Download GreenTracker".translate(lang),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                                
                                val isDe = lang.lowercase().startsWith("de")
                                val badgeRes = if (isDe) Res.drawable.google_play_badge_de else Res.drawable.google_play_badge_en
                                
                                Box(
                                    modifier = Modifier
                                        .height(56.dp)
                                        .clickable { 
                                            window.open("https://play.google.com/store/apps/details?id=com.greentracker.app", "_blank") 
                                        }
                                ) {
                                    Image(
                                        painter = painterResource(badgeRes),
                                        contentDescription = "Get it on Google Play",
                                        modifier = Modifier.fillMaxHeight(),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        item { Text("Application Settings".translate(lang), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        item { CollapsibleSettingsCard("Language Settings".translate(lang), expandedSection == "lang", onToggle = { onToggleSection(if (expandedSection == "lang") null else "lang") }) {
            Column { Text(text = "Set your preferred application language: English or German.".translate(lang), style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)); Spacer(modifier = Modifier.height(12.dp)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(selected = lang == "en", onClick = { viewModel.setLanguage("en") }, label = { Text("English") }); FilterChip(selected = lang == "de", onClick = { viewModel.setLanguage("de") }, label = { Text("Deutsch") }) } }
        } }
        item { CollapsibleSettingsCard("Theme Settings".translate(lang), expandedSection == "theme", onToggle = { onToggleSection(if (expandedSection == "theme") null else "theme") }) {
            CannabisTheme.entries.forEach { theme -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { viewModel.setTheme(theme) }.padding(vertical = 4.dp)) { RadioButton(selected = activeTheme == theme, onClick = { viewModel.setTheme(theme) }); Text(theme.displayName.translate(lang)) } }
        } }

        item {
            CollapsibleSettingsCard("App Icon".translate(lang), expandedSection == "icon", onToggle = { onToggleSection(if (expandedSection == "icon") null else "icon") }) {
                Column {
                    Text(text = "Choose your preferred App Icon. Changes apply automatically across the web dashboard.".translate(lang), style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                    Spacer(modifier = Modifier.height(12.dp))
                    val icons = listOf(1, 2, 3, 4, 5, 6)
                    icons.chunked(3).forEach { row ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { index ->
                                val isSelected = activeAppIconIndex == index
                                Card(
                                    modifier = Modifier.weight(1f).clickable { viewModel.setAppIconIndex(index) },
                                    border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
                                    colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(modifier = Modifier.padding(8.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Image(painter = getAppIconPainter(index), contentDescription = null, modifier = Modifier.size(40.dp).clip(CircleShape))
                                        RadioButton(selected = isSelected, onClick = { viewModel.setAppIconIndex(index) })
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
        
        item { CollapsibleSettingsCard("Day Rhythm".translate(lang), expandedSection == "rhythm", onToggle = { onToggleSection(if (expandedSection == "rhythm") null else "rhythm") }) {
            Text("Start of Day".translate(lang) + ": ${dayRhythm.toString().padStart(2, '0')}:00", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary); Slider(value = dayRhythm.toFloat(), onValueChange = { viewModel.setDayRhythm(it.toInt()) }, valueRange = 0f..23f, steps = 23)
        } }
        item { 
            CollapsibleSettingsCard(
                title = "Permissions".translate(lang), 
                expandedSection == "notif", 
                onToggle = { onToggleSection(if (expandedSection == "notif") null else "notif") }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { 
                    Text(
                        text = "Enable browser notifications to receive status updates. Note: Notifications are only supported when GreenTracker is used as a WebApp (added to the Home Screen).".translate(lang),
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )

                    Button(
                        onClick = { 
                            com.example.util.NotificationHelper.requestPermission()
                            com.example.util.NotificationHelper.notify("GreenTracker", "Notifications Active!")
                        }, 
                        modifier = Modifier.fillMaxWidth()
                    ) { 
                        Text("Test Notification".translate(lang)) 
                    } 
                }
            }
        }
        item { CollapsibleSettingsCard("Daily Dosage Limit".translate(lang), expandedSection == "limit", onToggle = { onToggleSection(if (expandedSection == "limit") null else "limit") }) {
            Text(dailyGoalGrams.format(1) + "g", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary); Slider(value = dailyGoalGrams.toFloat(), onValueChange = { viewModel.setDailyGoal(it.toDouble()) }, valueRange = 0.0f..10.0f)
        } }
        item { val maxD by viewModel.widgetMaxDosage.collectAsState(); CollapsibleSettingsCard("Slider Max Range".translate(lang), expandedSection == "max_dos", onToggle = { onToggleSection(if (expandedSection == "max_dos") null else "max_dos") }) {
            Text(maxD.format(1) + "g", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary); Slider(value = maxD.toFloat(), onValueChange = { viewModel.setWidgetMaxDosage(it.toDouble()) }, valueRange = 1.0f..10.0f)
        } }
        item { CollapsibleSettingsCard("Backup & Restore".translate(lang), expandedSection == "backup", onToggle = { onToggleSection(if (expandedSection == "backup") null else "backup") }) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = { triggerDownload("GreenTracker_Backup.json", viewModel.createBackupJson()) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Save, null); Text("Save Backup".translate(lang)) }; Button(onClick = { triggerFilePicker { viewModel.importBackupJson(it) } }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Publish, null); Text("Import Backup".translate(lang)) } }
        } }
        item { CollapsibleSettingsCard("Trash".translate(lang), expandedSection == "trash", badgeCount = trashedSessions.size + trashedStrains.size, onToggle = { onToggleSection(if (expandedSection == "trash") null else "trash") }) {
            Surface(color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) { Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Icon(Icons.Default.HourglassBottom, null, tint = Color.Red, modifier = Modifier.size(20.dp)); Text("Deleted items are automatically removed after 7 days.".translate(lang), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)) } }; Spacer(modifier = Modifier.height(16.dp))
            CollapsibleSubSection("Sessions".translate(lang), isSessionsTrashExpanded, badgeCount = trashedSessions.size, onToggle = { isSessionsTrashExpanded = !isSessionsTrashExpanded }) { trashedSessions.forEach { TrashedSessionRow(it, viewModel, lang) } }
            CollapsibleSubSection("Journal".translate(lang), isStrainsTrashExpanded, badgeCount = trashedStrains.size, onToggle = { isStrainsTrashExpanded = !isStrainsTrashExpanded }) { trashedStrains.forEach { TrashedStrainRow(it, viewModel, lang) } }
            var empC by remember { mutableStateOf(false) }; Button(onClick = { if (empC) { viewModel.emptyAllTrash(); empC = false } else { empC = true } }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp), colors = ButtonDefaults.buttonColors(containerColor = if (empC) Color.Red else Color.Red.copy(alpha = 0.8f))) { Text(if (empC) "Are you sure?".translate(lang) else "Empty Trash".translate(lang)) }; if (empC) { TextButton(onClick = { empC = false }, modifier = Modifier.fillMaxWidth()) { Text("Cancel".translate(lang)) } }
        } }
        item { CollapsibleSettingsCard("App Maintenance".translate(lang), expandedSection == "maint", onToggle = { onToggleSection(if (expandedSection == "maint") null else "maint") }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Check for updates or refresh the app cache.".translate(lang), style = MaterialTheme.typography.bodySmall); Button(onClick = { forceAppUpdate() }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text("Check for Updates / Refresh".translate(lang)) } }
        } }
        item { var v135 by remember { mutableStateOf(false) }; var v132 by remember { mutableStateOf(false) }; var v122 by remember { mutableStateOf(false) }; var v120 by remember { mutableStateOf(false) }; var v118 by remember { mutableStateOf(false) }; var v100 by remember { mutableStateOf(false) }
            CollapsibleSettingsCard(title = "Changelog".translate(lang), isExpanded = expandedSection == "changelog", onToggle = { onToggleSection(if (expandedSection == "changelog") null else "changelog") }) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CollapsibleSubSection(title = "Version 1.3.5", isExpanded = v135, onToggle = { v135 = !v135 }) {
                        ChangelogDetailText("• " + "Limit Confirmation: Mandatory dialog when reaching daily limits".translate(lang))
                        ChangelogDetailText("• " + "Log Debouncing: 2-second cooldown to prevent double-logs".translate(lang))
                        ChangelogDetailText("• " + "Enhanced Statistics: Line Charts for Month/Year views".translate(lang))
                        ChangelogDetailText("• " + "Interactive Charts: Press and hold to see exact values".translate(lang))
                        ChangelogDetailText("• " + "Journal Integration: Select strains directly in the log dialog".translate(lang))
                        ChangelogDetailText("• " + "Future Logging: Blocked logs with future timestamps".translate(lang))
                        ChangelogDetailText("• " + "Compact UI: Reduced vertical size for better overview".translate(lang))
                    }
                    CollapsibleSubSection(title = "Version 1.3.2", isExpanded = v132, onToggle = { v132 = !v132 }) {
                        ChangelogDetailText("• " + "Full localization: 100% German UI coverage and bug fixes".translate(lang))
                        ChangelogDetailText("• " + "iPhone Notification Fix: Improved reliability for iOS".translate(lang))
                        ChangelogDetailText("• " + "Journal UI: New 'Review Later' button logic and compact layout".translate(lang))
                        ChangelogDetailText("• " + "Improved Backup: Trash items and review states are now included".translate(lang))
                    }
                    CollapsibleSubSection(title = "Version 1.2.2", isExpanded = v122, onToggle = { v122 = !v122 }) { 
                        ChangelogDetailText("• " + "Review Later: Mark strains for subsequent rating".translate(lang))
                        ChangelogDetailText("• " + "Rating Reminders: Configurable alerts (1-14 days)".translate(lang))
                        ChangelogDetailText("• " + "New Filter: Quickly find entries needing review".translate(lang)) 
                        ChangelogDetailText("• " + "Compact Settings: Streamlined notification selection".translate(lang))
                        ChangelogDetailText("• " + "Platform Modernization: Android 16 (API 36)".translate(lang))
                        ChangelogDetailText("• " + "Java 17 Update for performance & security".translate(lang))
                        ChangelogDetailText("• " + "Restored 0.1g logging precision".translate(lang))
                        ChangelogDetailText("• " + "Fixed German 'Heute/Gestern' headers".translate(lang))
                        ChangelogDetailText("• " + "Privacy Transparency: Refined Privacy Policy with clear sections and direct contact details".translate(lang))
                    }
                    CollapsibleSubSection(title = "Version 1.2.0", isExpanded = v120, onToggle = { v120 = !v120 }) { 
                        ChangelogDetailText("• " + "Smart History: Auto-collapse past days".translate(lang))
                        ChangelogDetailText("• " + "Excess Counter on Home screen".translate(lang))
                        ChangelogDetailText("• " + "Natural German localization update".translate(lang)) 
                    }
                    CollapsibleSubSection(title = "Version 1.1.8", isExpanded = v118, onToggle = { v118 = !v118 }) { 
                        ChangelogDetailText("• " + "Full JSON Backup & Restore".translate(lang))
                        ChangelogDetailText("• " + "Improved data integrity".translate(lang)) 
                    }
                    CollapsibleSubSection(title = "Version 1.0.0", isExpanded = v100, onToggle = { v100 = !v100 }) { 
                        ChangelogDetailText("• " + "Initial Release: Core tracking".translate(lang)) 
                    }
                }
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onNavigateToPrivacy() },
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Privacy Policy / Datenschutzerklärung".translate(lang), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
                }
            }
        }
        item { CollapsibleSettingsCard("Danger Zone".translate(lang), expandedSection == "danger", onToggle = { onToggleSection(if (expandedSection == "danger") null else "danger") }) {
            Button(onClick = { showClearAllConfirm = true }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red), modifier = Modifier.fillMaxWidth()) { Text("Clear All Data".translate(lang)) }
        } }
        item { Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("GreenTracker", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)); Text("Version 1.3.5", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)); Spacer(Modifier.height(8.dp)); Text("Human Vision & AI Power", style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f), fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) } } }
    }
}

@Composable
fun ChangelogDetailText(text: String) { Text(text = text, style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp), modifier = Modifier.padding(start = 28.dp, bottom = 4.dp)) }

@Composable
fun CollapsibleSubSection(title: String, isExpanded: Boolean, badgeCount: Int = 0, onToggle: () -> Unit, content: @Composable () -> Unit) {
    Column {
        Row(modifier = Modifier.fillMaxWidth().clickable { onToggle() }.padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                if (badgeCount > 0) { Box(modifier = Modifier.size(18.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) { Text("$badgeCount", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold) } }
            }
            Icon(if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, modifier = Modifier.size(20.dp))
        }
        if (isExpanded) Column(modifier = Modifier.padding(start = 8.dp)) { content() }
    }
}

@Composable
fun CollapsibleSettingsCard(title: String, isExpanded: Boolean, badgeCount: Int = 0, onToggle: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth().clickable { onToggle() }.padding(20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) { Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)); if (badgeCount > 0) Badge { Text("$badgeCount") } }
                Icon(if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
            }
            AnimatedVisibility(visible = isExpanded) { Column(modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 20.dp)) { content() } }
        }
    }
}

@Composable
fun SessionItemRow(session: SmokeSession, lang: String, viewModel: SmokeViewModel, activeAppIconIndex: Int = 1, onDelete: (Long) -> Unit, onEdit: ((SmokeSession) -> Unit)? = null) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    if (showDeleteConfirm) { AlertDialog(onDismissRequest = {}, title = { Text("Delete Session?".translate(lang)) }, text = { Text("Move this log to the trash?".translate(lang)) }, confirmButton = { Button(onClick = { onDelete(session.id); showDeleteConfirm = false }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("Delete".translate(lang)) } }, dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel".translate(lang)) } }) }
    if (showEditDialog) { val maxD by viewModel.widgetMaxDosage.collectAsState(); EditSessionDialog(session, lang, maxD, onDismiss = { showEditDialog = false }, onSave = { onEdit?.invoke(it); showEditDialog = false }) }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painter = getAppIconPainter(activeAppIconIndex), contentDescription = null, modifier = Modifier.size(38.dp).clip(CircleShape))
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = if (session.strain.isNotEmpty()) session.strain else "Dose logged".translate(lang),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    
                    val sourceTag = if (session.notes.contains("Widget")) "Widget" else if (session.notes.contains("Dashboard")) "Web" else "App"
                    Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)) {
                        Text(sourceTag, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                    }

                    val dt = Instant.fromEpochMilliseconds(session.timestamp).toLocalDateTime(TimeZone.currentSystemDefault()).time
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    ) {
                        Text(
                            text = "${dt.hour.toString().padStart(2, '0')}:${dt.minute.toString().padStart(2, '0')}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
                if (session.notes.isNotEmpty()) {
                    Text(
                        text = session.notes,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "+${session.grams.format(1, lang)}g",
                fontWeight = FontWeight.Black,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.secondary
            )
            if (onEdit != null) {
                IconButton(onClick = { showEditDialog = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                }
            }
            IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, null, tint = Color.Red.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun EditSessionDialog(session: SmokeSession, lang: String, maxDosage: Double, onDismiss: () -> Unit, onSave: (SmokeSession) -> Unit) {
    var grams by remember { mutableStateOf(session.grams) }; var strain by remember { mutableStateOf(session.strain) }; var notes by remember { mutableStateOf(session.notes) }; var timestamp by remember { mutableStateOf(session.timestamp) }
    AlertDialog(onDismissRequest = {}, title = { Text("Edit Session".translate(lang), fontWeight = FontWeight.Bold) }, text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.verticalScroll(rememberScrollState())) { Text("Amount:".translate(lang) + " ${grams.format(1, lang)}g", fontWeight = FontWeight.Bold); Slider(value = grams.toFloat(), onValueChange = { grams = it.toDouble() }, valueRange = 0.0f..maxDosage.toFloat()); TextField(value = strain, onValueChange = { strain = it }, label = { Text("Strain / Variety".translate(lang)) }, modifier = Modifier.fillMaxWidth() ); TextField(value = notes, onValueChange = { notes = it }, label = { Text("Session Notes".translate(lang)) }, modifier = Modifier.fillMaxWidth() ); Text("Timestamp".translate(lang), fontWeight = FontWeight.Bold, fontSize = 12.sp); val dt = Instant.fromEpochMilliseconds(timestamp).toLocalDateTime(TimeZone.currentSystemDefault()); Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Column(modifier = Modifier.weight(1f)) { Text("Hour".translate(lang), style = MaterialTheme.typography.labelSmall); Slider(value = dt.hour.toFloat(), onValueChange = { val newDt = LocalDateTime(dt.year, dt.month, dt.dayOfMonth, it.toInt(), dt.minute, 0, 0); timestamp = newDt.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds() }, valueRange = 0f..23f, steps = 23) }; Column(modifier = Modifier.weight(1f)) { Text("Minute".translate(lang), style = MaterialTheme.typography.labelSmall); Slider(value = dt.minute.toFloat(), onValueChange = { val newDt = LocalDateTime(dt.year, dt.month, dt.dayOfMonth, dt.hour, it.toInt(), 0, 0); timestamp = newDt.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds() }, valueRange = 0f..59f, steps = 59) } }; Text("${dt.hour.toString().padStart(2,'0')}:${dt.minute.toString().padStart(2,'0')}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) } }, confirmButton = { Button(onClick = { onSave(session.copy(grams = grams, strain = strain, notes = notes, timestamp = timestamp)) }) { Text("Save".translate(lang)) } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel".translate(lang)) } })
}

@Composable
fun AddManualSessionDialog(
    initialGrams: Double, 
    lang: String, 
    maxDosage: Double, 
    theme: CannabisTheme, 
    onDismiss: () -> Unit, 
    allStrains: List<StrainEntry> = emptyList(),
    onSave: (Double, String, String, Long) -> Unit
) {
    var grams by remember { mutableStateOf(if (initialGrams > 0.0) initialGrams else 0.2) }
    var strain by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var timestamp by remember { mutableStateOf(Clock.System.now().toEpochMilliseconds()) }
    var timeOption by remember { mutableStateOf("now") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showStrainPicker by remember { mutableStateOf(false) }

    LaunchedEffect(timeOption) {
        when (timeOption) {
            "now" -> { timestamp = Clock.System.now().toEpochMilliseconds(); errorMessage = null }
            "15m" -> { timestamp = Clock.System.now().toEpochMilliseconds() - 15 * 60 * 1000L; errorMessage = null }
            "1h" -> { timestamp = Clock.System.now().toEpochMilliseconds() - 60 * 60 * 1000L; errorMessage = null }
            "3h" -> { timestamp = Clock.System.now().toEpochMilliseconds() - 3 * 60 * 60 * 1000L; errorMessage = null }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss, 
        title = { Text("Log Dose".translate(lang), fontWeight = FontWeight.Black) }, 
        text = { 
            Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.verticalScroll(rememberScrollState())) { 
                // Grams Control
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Amount (Grams):".translate(lang), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        Text("${grams.format(1, lang)} g", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { if (grams > 0.1) grams = (grams - 0.1).roundToDecimals(1) }) { Icon(Icons.Default.Remove, null) }
                        Slider(value = grams.toFloat(), onValueChange = { grams = it.toDouble().roundToDecimals(1) }, valueRange = 0.1f..maxDosage.toFloat().coerceAtLeast(0.1f), modifier = Modifier.weight(1f))
                        IconButton(onClick = { grams = (grams + 0.1).roundToDecimals(1) }) { Icon(Icons.Default.Add, null) }
                    }
                }

                // Time Selection
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Time of Session:".translate(lang), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("now" to "Now", "15m" to "-15m", "1h" to "-1h", "3h" to "-3h", "custom" to "Custom...").forEach { (opt, label) ->
                            val isSel = timeOption == opt
                            Button(
                                onClick = { 
                                    timeOption = opt
                                    if (opt == "custom") { triggerWebDatePicker { timestamp = it.toLongOrNull() ?: timestamp; timeOption = "custom" } }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, contentColor = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant),
                                contentPadding = PaddingValues(horizontal = 4.dp),
                                modifier = Modifier.weight(1f).height(32.dp)
                            ) { Text(label.translate(lang), fontSize = 10.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) }
                        }
                    }
                    val dt = Instant.fromEpochMilliseconds(timestamp).toLocalDateTime(TimeZone.currentSystemDefault())
                    Text("${dt.dayOfMonth}.${dt.monthNumber}.${dt.year}  ${dt.hour.toString().padStart(2,'0')}:${dt.minute.toString().padStart(2,'0')}", 
                        style = MaterialTheme.typography.labelMedium, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.primary)
                }

                // Strain Picker
                Box {
                    OutlinedTextField(
                        value = strain, 
                        onValueChange = { strain = it }, 
                        label = { Text("Strain / Variety".translate(lang)) }, 
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = { if (allStrains.isNotEmpty()) IconButton(onClick = { showStrainPicker = true }) { Icon(Icons.AutoMirrored.Filled.MenuBook, null) } }
                    )
                    DropdownMenu(expanded = showStrainPicker, onDismissRequest = { showStrainPicker = false }, modifier = Modifier.fillMaxWidth(0.8f)) {
                        allStrains.forEach { entry ->
                            DropdownMenuItem(text = { Text(entry.strainName) }, onClick = { strain = entry.strainName; showStrainPicker = false })
                        }
                    }
                }

                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Session Notes".translate(lang)) }, modifier = Modifier.fillMaxWidth()) 
                
                if (errorMessage != null) {
                    Text(errorMessage!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
            } 
        }, 
        confirmButton = { 
            Button(onClick = { 
                if (timestamp > Clock.System.now().toEpochMilliseconds()) {
                    errorMessage = "Cannot log in the future".translate(lang)
                } else {
                    onSave(grams, strain, notes, timestamp) 
                }
            }) { Text("Save".translate(lang), fontWeight = FontWeight.Bold) } 
        }, 
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel".translate(lang)) } }
    )
}

@Composable
fun AddEditStrainDialog(initial: StrainEntry?, lang: String, viewModel: SmokeViewModel, onDismiss: () -> Unit, onSave: (String, String, String, Double, Double, String, String, String, Boolean) -> Unit) {
    var name by remember { mutableStateOf(initial?.strainName ?: "") }; var prod by remember { mutableStateOf(initial?.producerCultivar ?: "") }; var cat by remember { mutableStateOf(initial?.category ?: "Hybrid") }; var photo by remember { mutableStateOf(initial?.photoUri ?: "") }; var rat by remember { mutableStateOf(initial?.rating ?: "THUMBS_UP") }; var thcT by remember { mutableStateOf(if (initial != null && initial.thcPercentage > 0) initial.thcPercentage.toInt().toString() else "") }; var cbdT by remember { mutableStateOf(if (initial != null && initial.cbdPercentage > 0) initial.cbdPercentage.toInt().toString() else "") }; var nts by remember { mutableStateOf(initial?.notes ?: "") }
    var needsReview by remember { mutableStateOf(initial?.needsReview ?: false) }
    AlertDialog(onDismissRequest = {}, title = { Text(if (initial == null) "Add Strain".translate(lang) else "Edit Strain".translate(lang), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) { 
        if (photo.isNotEmpty()) { Box(modifier = Modifier.size(100.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable { photo = "" }, contentAlignment = Alignment.Center) { val bitmap = remember(photo) { try { val base64Data = if (photo.contains(",")) photo.split(",")[1] else photo; com.example.util.Base64.decode(base64Data).decodeToImageBitmap() } catch (e: Exception) { null } }; if (bitmap != null) { Image(bitmap = bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()); Box(modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape)) { Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(16.dp)) } } else { Text("Error loading", fontSize = 10.sp) } } } else { Button(onClick = { triggerImagePicker { photo = it } }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.AddAPhoto, null); Spacer(Modifier.width(8.dp)); Text("Add Photo".translate(lang)) } }; 
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name *") }, modifier = Modifier.fillMaxWidth(), singleLine = true); 
        OutlinedTextField(value = prod, onValueChange = { prod = it }, label = { Text("Producer".translate(lang)) }, modifier = Modifier.fillMaxWidth(), singleLine = true); 
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { 
            OutlinedTextField(value = thcT, onValueChange = { if (it.all { c -> c.isDigit() }) { if ((it.toDoubleOrNull() ?: 0.0) <= 100.0) thcT = it } }, label = { Text("THC %") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true); 
            OutlinedTextField(value = cbdT, onValueChange = { if (it.all { c -> c.isDigit() }) { if ((it.toDoubleOrNull() ?: 0.0) <= 100.0) cbdT = it } }, label = { Text("CBD %") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true) 
        }; 
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { 
            listOf("Indica", "Sativa", "Hybrid").forEach { c -> FilterChip(selected = cat == c, onClick = { cat = c }, label = { Text(c) }, modifier = Modifier.weight(1f)) } 
        }; 
        Text("Rating".translate(lang), fontWeight = FontWeight.Bold, fontSize = 12.sp); 
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) { 
            SelectableRatingItem(selected = !needsReview && rat == "THUMBS_UP", icon = Icons.Default.ThumbUp, label = "Recommended".translate(lang), color = Color(0xFF2E7D32), onClick = { rat = "THUMBS_UP"; needsReview = false }, modifier = Modifier.weight(1f)); 
            SelectableRatingItem(selected = !needsReview && rat == "NEUTRAL", icon = Icons.Default.SentimentNeutral, label = "Neutral".translate(lang), color = Color.Gray, onClick = { rat = "NEUTRAL"; needsReview = false }, modifier = Modifier.weight(1f)); 
        }; 
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) { 
            SelectableRatingItem(selected = !needsReview && rat == "THUMBS_DOWN", icon = Icons.Default.ThumbDown, label = "Avoid".translate(lang), color = Color.Red, onClick = { rat = "THUMBS_DOWN"; needsReview = false }, modifier = Modifier.weight(1f)); 
            SelectableRatingItem(selected = needsReview, icon = Icons.Default.Timer, label = "Review Later".translate(lang), color = MaterialTheme.colorScheme.tertiary, onClick = { needsReview = true }, modifier = Modifier.weight(1f)) 
        }; 
        OutlinedTextField(value = nts, onValueChange = { nts = it }, label = { Text("Notes (Optional)...".translate(lang)) }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4)
    } }, confirmButton = { Button(enabled = name.isNotBlank(), onClick = { onSave(name, prod, cat, (thcT.toDoubleOrNull() ?: 0.0), (cbdT.toDoubleOrNull() ?: 0.0), rat, nts, photo, needsReview) }) { Text("Save".translate(lang)) } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel".translate(lang)) } })
}

@Composable
fun SelectableRatingItem(selected: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, color: Color, onClick: () -> Unit, modifier: Modifier) {
    Surface(onClick = onClick, shape = RoundedCornerShape(8.dp), color = if (selected) color.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant, border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) color else Color.Transparent), modifier = modifier) { Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 8.dp)) { Icon(icon, null, tint = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp)); Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant) } }
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

@Composable
fun TrashedSessionRow(session: SmokeSession, viewModel: SmokeViewModel, activeLanguage: String) {
    var isConfirming by remember(session.id) { mutableStateOf(false) }
    LaunchedEffect(isConfirming) { if (isConfirming) { kotlinx.coroutines.delay(3000L); isConfirming = false } }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "${session.grams.format(1)}g - ${session.strain.ifEmpty { "Default Strain".translate(activeLanguage) }}", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
            val dt = Instant.fromEpochMilliseconds(session.timestamp).toLocalDateTime(TimeZone.currentSystemDefault())
            Text(text = "${dt.dayOfMonth.toString().padStart(2, '0')}.${dt.monthNumber.toString().padStart(2, '0')}.${dt.year} ${dt.hour.toString().padStart(2, '0')}:${dt.minute.toString().padStart(2, '0')}", style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)))
        }
        IconButton(onClick = { viewModel.restoreSession(session.id) }) { Icon(Icons.Default.Restore, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) }
        if (isConfirming) { TextButton(onClick = { viewModel.permanentlyDeleteSession(session.id) }, colors = ButtonDefaults.textButtonColors(contentColor = Color.Red), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp), modifier = Modifier.height(32.dp)) { Text(text = "Rly?".translate(activeLanguage), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp)) } }
        else { IconButton(onClick = { isConfirming = true }) { Icon(Icons.Default.DeleteForever, null, tint = Color.Red, modifier = Modifier.size(18.dp)) } }
    }
}

@Composable
fun TrashedStrainRow(strain: StrainEntry, viewModel: SmokeViewModel, activeLanguage: String) {
    var isConfirming by remember(strain.id) { mutableStateOf(false) }
    LaunchedEffect(isConfirming) { if (isConfirming) { kotlinx.coroutines.delay(3000L); isConfirming = false } }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(strain.strainName, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
            Text(text = "${strain.producerCultivar} (${strain.category})", style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)))
        }
        IconButton(onClick = { viewModel.restoreStrain(strain.id) }) { Icon(Icons.Default.Restore, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) }
        if (isConfirming) { TextButton(onClick = { viewModel.permanentlyDeleteStrain(strain.id) }, colors = ButtonDefaults.textButtonColors(contentColor = Color.Red), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp), modifier = Modifier.height(32.dp)) { Text(text = "Rly?".translate(activeLanguage), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp)) } }
        else { IconButton(onClick = { isConfirming = true }) { Icon(Icons.Default.DeleteForever, null, tint = Color.Red, modifier = Modifier.size(18.dp)) } }
    }
}

@Composable
fun PrivacyPolicyScreen(lang: String, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
            Text("Privacy Policy / Datenschutz", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }

        if (lang == "de") {
            PrivacyTextGerman()
        } else {
            PrivacyTextEnglish()
        }
        
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
fun PrivacyTextEnglish() {
    Text("Privacy Policy", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Text("\nLast updated: August 1, 2026\n", style = MaterialTheme.typography.bodySmall)
    Text("GreenTrackerDev is committed to protecting your privacy. This Privacy Policy explains how we handle your information when you use our mobile and web applications (\"GreenTracker\").\n", style = MaterialTheme.typography.bodyMedium)
    
    PrivacyHeader("1. Data Controller & Contact")
    Text("The operator and data controller of this application is:\nDeveloper: GreenTrackerDev\nEmail: GreenTracker420app@gmail.com\n\nIf you have any questions about this Privacy Policy, please contact us directly via email.\n", style = MaterialTheme.typography.bodyMedium)

    PrivacyHeader("2. Data Collection & Storage")
    Text("GreenTracker is designed to be a privacy-first application. All data you log (smoke sessions, strain entries, notes, and photos) is stored strictly locally on your device's internal storage using a local database. We do not transmit your usage logs, files, or personal information to any external servers.\n", style = MaterialTheme.typography.bodyMedium)
    
    PrivacyHeader("3. Device Permissions")
    Text("To provide its features, the app requires specific device permissions. All data accessed via these permissions stays entirely on your device (phone or browser):\n\n• Camera / Photos: Used only if you choose to take or upload photos of strains for your journal. These photos are processed and stored locally on your device. We have no access to your camera feed or your photo library.\n• Notifications: Used to send optional logging reminders as configured in your settings. No user data is tracked or transmitted for this purpose.\n", style = MaterialTheme.typography.bodyMedium)
    
    PrivacyHeader("4. Data Backup")
    Text("If you use the Backup feature, a backup file is created locally on your device. You can choose to share or store this file yourself. This data remains under your absolute control at all times.\n", style = MaterialTheme.typography.bodyMedium)
    
    PrivacyHeader("5. Third-Party Services")
    Text("The web version of GreenTracker is hosted on GitHub Pages. GitHub may collect basic server logs (such as IP addresses) for security and maintenance purposes, governed by GitHub's privacy policy. The app itself does not use any third-party tracking, analytics, or advertising frameworks.\n", style = MaterialTheme.typography.bodyMedium)
}

@Composable
fun PrivacyTextGerman() {
    Text("Datenschutzerklärung", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Text("\nZuletzt aktualisiert: 1. August 2026\n", style = MaterialTheme.typography.bodySmall)
    Text("GreenTrackerDev setzt sich für den Schutz Ihrer Privatsphäre ein. Diese Datenschutzerklärung erläutert, wie wir mit Ihren Informationen umgehen, wenn Sie unsere mobilen und Web-Anwendungen (\"GreenTracker\") nutzen.\n", style = MaterialTheme.typography.bodyMedium)
    
    PrivacyHeader("1. Verantwortlicher & Kontakt")
    Text("Der Betreiber und Verantwortliche für diese Anwendung ist:\nEntwickler: GreenTrackerDev\nE-Mail: GreenTracker420app@gmail.com\n\nBei Fragen zu dieser Datenschutzerklärung wenden Sie sich bitte direkt per E-Mail an uns.\n", style = MaterialTheme.typography.bodyMedium)

    PrivacyHeader("2. Datenerhebung & Speicherung")
    Text("GreenTracker ist als Privacy-First-Anwendung konzipiert. Alle von Ihnen protokollierten Daten (Sitzungen, Sorteneinträge, Notizen und Fotos) werden ausschließlich lokal auf dem internen Speicher Ihres Geräts gespeichert. Wir übertragen Ihre Verbrauchsprotokolle, Dateien oder persönlichen Informationen nicht an externe Server.\n", style = MaterialTheme.typography.bodyMedium)
    
    PrivacyHeader("3. Geräteberechtigungen")
    Text("Um die Funktionen bereitzustellen, benötigt die App bestimmte Berechtigungen. Alle Daten, auf die über diese Berechtigungen zugegriffen wird, verbleiben vollständig auf Ihrem Gerät (Handy oder Browser):\n\n• Kamera / Fotos: Wird nur verwendet, wenn Sie Fotos von Sorten für Ihr Tagebuch aufnehmen oder hochladen möchten. Diese Fotos werden lokal auf Ihrem Gerät verarbeitet und gespeichert. Wir haben keinen Zugriff auf Ihren Kamera-Feed oder Ihre Foto-Bibliothek.\n• Benachrichtigungen: Wird verwendet, um optionale Erinnerungen zu senden, wie in Ihren Einstellungen konfiguriert. Zu diesem Zweck werden keine Benutzerdaten verfolgt oder übertragen.\n", style = MaterialTheme.typography.bodyMedium)
    
    PrivacyHeader("4. Datensicherung")
    Text("Wenn Sie die Backup-Funktion nutzen, wird lokal auf Ihrem Gerät eine Backup-Datei erstellt. Sie können wählen, ob Sie diese Datei selbst teilen oder speichern möchten. Diese Daten bleiben jederzeit unter Ihrer absoluten Kontrolle.\n", style = MaterialTheme.typography.bodyMedium)
    
    PrivacyHeader("5. Drittanbieter")
    Text("Die Webversion von GreenTracker wird auf GitHub Pages gehostet. GitHub kann grundlegende Serverprotokolle (wie IP-Adressen) für Sicherheits- und Wartungszwecke erfassen, gemäß der Datenschutzerklärung von GitHub. Die App selbst verwendet keine Drittanbieter-Tracking-, Analyse- oder Werbe-Frameworks.\n", style = MaterialTheme.typography.bodyMedium)
}

@Composable
fun PrivacyHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp), color = MaterialTheme.colorScheme.primary)
}
