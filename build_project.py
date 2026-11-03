import os

files = {
    "settings.gradle.kts": """pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "StreamPlay"
include(":app")
""",

    "build.gradle.kts": """plugins {
    id("com.android.application") version "8.3.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    id("com.google.devtools.ksp") version "1.9.22-1.0.17" apply false
}
""",

    "gradle.properties": """org.gradle.jvmargs=-Xmx3072m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
""",

    "app/build.gradle.kts": """plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.streamplay.player"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.streamplay.player"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        ndk {
            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64"))
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.10"
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.02.02")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Native graphics path library (bundles libandroidx.graphics.path.so)
    implementation("androidx.graphics:graphics-path:1.0.0-rc01")
    implementation("androidx.graphics:graphics-shapes:1.0.0-alpha05")

    // Media3 ExoPlayer for universal format support
    val media3Version = "1.3.0"
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-exoplayer-dash:$media3Version")
    implementation("androidx.media3:media3-exoplayer-hls:$media3Version")
    implementation("androidx.media3:media3-exoplayer-smoothstreaming:$media3Version")
    implementation("androidx.media3:media3-exoplayer-rtsp:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")
    implementation("androidx.media3:media3-datasource-okhttp:$media3Version")
    implementation("androidx.media3:media3-common:$media3Version")

    // Network & Custom Headers
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Room Database for History
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // DataStore for Settings
    implementation("androidx.datastore:datastore-preferences:1.0.0")
}
""",

    "app/proguard-rules.pro": """-keep class androidx.graphics.path.** { *; }
-keep class androidx.media3.** { *; }
""",

    "app/src/main/AndroidManifest.xml": """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />

    <application
        android:allowBackup="true"
        android:label="StreamPlay"
        android:supportsRtl="true"
        android:usesCleartextTraffic="true"
        android:theme="@style/Theme.StreamPlay"
        tools:targetApi="34">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:windowSoftInputMode="adjustResize"
            android:configChanges="orientation|screenSize|screenLayout|keyboardHidden">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <activity
            android:name=".PlayerActivity"
            android:exported="true"
            android:configChanges="screenSize|smallestScreenSize|screenLayout|orientation|keyboardHidden|uiMode"
            android:launchMode="singleTask"
            android:supportsPictureInPicture="true"
            android:theme="@style/Theme.StreamPlay" />
    </application>
</manifest>
""",

    "app/src/main/res/values/themes.xml": """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.StreamPlay" parent="android:Theme.Material.NoActionBar">
        <item name="android:statusBarColor">@android:color/transparent</item>
        <item name="android:navigationBarColor">@android:color/transparent</item>
        <item name="android:windowLayoutInDisplayCutoutMode">shortEdges</item>
    </style>
</resources>
""",

    "app/src/main/java/com/streamplay/player/ui/theme/Theme.kt": """package com.streamplay.player.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BlueAccent = Color(0xFF0066FF)
val BlueSecondary = Color(0xFF3385FF)
val BlueContainerDark = Color(0xFF002B73)
val BlueContainerLight = Color(0xFFD6E4FF)

private val DarkColorScheme = darkColorScheme(
    primary = BlueAccent,
    onPrimary = Color.White,
    primaryContainer = BlueContainerDark,
    onPrimaryContainer = Color.White,
    secondary = BlueSecondary,
    background = Color(0xFF0E1117),
    surface = Color(0xFF161B22),
    surfaceVariant = Color(0xFF21262D)
)

private val LightColorScheme = lightColorScheme(
    primary = BlueAccent,
    onPrimary = Color.White,
    primaryContainer = BlueContainerLight,
    onPrimaryContainer = Color(0xFF001B4D),
    secondary = BlueSecondary,
    background = Color(0xFFF7F9FC),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFEEF2F8)
)

@Composable
fun StreamPlayTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
""",

    "app/src/main/java/com/streamplay/player/data/AppDatabase.kt": """package com.streamplay.player.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "stream_history")
data class StreamHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val userAgent: String,
    val referer: String,
    val rawHeaders: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface HistoryDao {
    @Query("SELECT * FROM stream_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<StreamHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: StreamHistoryEntity)

    @Delete
    suspend fun delete(item: StreamHistoryEntity)

    @Query("DELETE FROM stream_history")
    suspend fun clearAll()
}

@Database(entities = [StreamHistoryEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "streamplay_history.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
""",

    "app/src/main/java/com/streamplay/player/data/SettingsManager.kt": """package com.streamplay.player.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "player_settings")

data class GestureSettings(
    val swipeVolumeBrightness: Boolean = true,
    val pinchToZoom: Boolean = true,
    val doubleTapSeek: Boolean = true,
    val holdFor2xSpeed: Boolean = true,
    val autoPip: Boolean = true
)

class SettingsManager(private val context: Context) {
    companion object {
        val KEY_SWIPE = booleanPreferencesKey("swipe_vol_bright")
        val KEY_PINCH = booleanPreferencesKey("pinch_zoom")
        val KEY_DOUBLE_TAP = booleanPreferencesKey("double_tap_seek")
        val KEY_HOLD_2X = booleanPreferencesKey("hold_2x_speed")
        val KEY_AUTO_PIP = booleanPreferencesKey("auto_pip")
    }

    val settingsFlow: Flow<GestureSettings> = context.dataStore.data.map { prefs ->
        GestureSettings(
            swipeVolumeBrightness = prefs[KEY_SWIPE] ?: true,
            pinchToZoom = prefs[KEY_PINCH] ?: true,
            doubleTapSeek = prefs[KEY_DOUBLE_TAP] ?: true,
            holdFor2xSpeed = prefs[KEY_HOLD_2X] ?: true,
            autoPip = prefs[KEY_AUTO_PIP] ?: true
        )
    }

    suspend fun updateSetting(key: androidx.datastore.preferences.core.Preferences.Key<Boolean>, value: Boolean) {
        context.dataStore.edit { it[key] = value }
    }
}
""",

    "app/src/main/java/com/streamplay/player/MainActivity.kt": """package com.streamplay.player

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.graphics.path.PathIterator
import com.streamplay.player.data.AppDatabase
import com.streamplay.player.data.GestureSettings
import com.streamplay.player.data.SettingsManager
import com.streamplay.player.data.StreamHistoryEntity
import com.streamplay.player.ui.theme.StreamPlayTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)

        // Initialize androidx.graphics.path native bridge reference
        val dummyPath = android.graphics.Path().apply { moveTo(0f, 0f); lineTo(1f, 1f) }
        val iterator = PathIterator(dummyPath)
        iterator.hasNext()

        val database = AppDatabase.getInstance(this)
        val settingsManager = SettingsManager(this)

        setContent {
            StreamPlayTheme {
                HomeScreen(
                    database = database,
                    settingsManager = settingsManager,
                    onPlayStream = { url, userAgent, referer, rawHeaders ->
                        val intent = Intent(this, PlayerActivity::class.java).apply {
                            putExtra("STREAM_URL", url.trim())
                            putExtra("USER_AGENT", userAgent.trim())
                            putExtra("REFERER", referer.trim())
                            putExtra("RAW_HEADERS", rawHeaders.trim())
                        }
                        startActivity(intent)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    database: AppDatabase,
    settingsManager: SettingsManager,
    onPlayStream: (String, String, String, String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val historyList by database.historyDao().getAllHistory().collectAsState(initial = emptyList())
    val gestureSettings by settingsManager.settingsFlow.collectAsState(initial = GestureSettings())

    var streamUrl by remember { mutableStateOf("") }
    var streamTitle by remember { mutableStateOf("") }
    var userAgent by remember { mutableStateOf("") }
    var referer by remember { mutableStateOf("") }
    var customHeaders by remember { mutableStateOf("") }

    var showSettingsDialog by remember { mutableStateOf(false) }
    var showHistorySheet by remember { mutableStateOf(false) }
    var urlError by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "StreamPlay",
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = { showHistorySheet = true }) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "History"
                        )
                    }
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Stream Configuration",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )

                    OutlinedTextField(
                        value = streamUrl,
                        onValueChange = {
                            streamUrl = it
                            urlError = false
                        },
                        label = { Text("Video / Stream URL (MP4, M3U8, MPD, MKV, RTSP)") },
                        placeholder = { Text("https://example.com/live/index.m3u8") },
                        isError = urlError,
                        supportingText = if (urlError) {
                            { Text("Please enter a valid video or stream URL") }
                        } else null,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        maxLines = 3,
                        trailingIcon = {
                            Row {
                                if (streamUrl.isNotEmpty()) {
                                    IconButton(onClick = { streamUrl = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                                    }
                                }
                                IconButton(onClick = {
                                    clipboard.getText()?.text?.let { pasted ->
                                        streamUrl = pasted
                                        urlError = false
                                    }
                                }) {
                                    Icon(Icons.Default.ContentPaste, contentDescription = "Paste")
                                }
                            }
                        }
                    )

                    OutlinedTextField(
                        value = streamTitle,
                        onValueChange = { streamTitle = it },
                        label = { Text("Stream Name (Optional for History)") },
                        placeholder = { Text("Live Match / Movie Stream") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "HTTP Request Headers",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )

                    OutlinedTextField(
                        value = userAgent,
                        onValueChange = { userAgent = it },
                        label = { Text("User-Agent (Optional)") },
                        placeholder = { Text("Mozilla/5.0 (Windows NT 10.0; Win64; x64)...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = referer,
                        onValueChange = { referer = it },
                        label = { Text("Referer / Origin (Optional)") },
                        placeholder = { Text("https://referer-domain.com/") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = customHeaders,
                        onValueChange = { customHeaders = it },
                        label = { Text("Additional Headers (Key: Value per line)") },
                        placeholder = {
                            Text("Cookie: session=abcd\nAuthorization: Bearer token\nX-Forwarded-For: 1.1.1.1")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 110.dp),
                        maxLines = 6
                    )
                }
            }

            Button(
                onClick = {
                    if (streamUrl.isBlank()) {
                        urlError = true
                    } else {
                        scope.launch {
                            database.historyDao().insert(
                                StreamHistoryEntity(
                                    title = streamTitle.ifBlank { streamUrl.substringAfterLast("/").ifBlank { "Network Stream" } },
                                    url = streamUrl.trim(),
                                    userAgent = userAgent.trim(),
                                    referer = referer.trim(),
                                    rawHeaders = customHeaders.trim()
                                )
                            )
                        }
                        onPlayStream(streamUrl, userAgent, referer, customHeaders)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "PLAY STREAM",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showSettingsDialog) {
        GestureSettingsDialog(
            settings = gestureSettings,
            onDismiss = { showSettingsDialog = false },
            onToggleSwipe = { scope.launch { settingsManager.updateSetting(SettingsManager.KEY_SWIPE, it) } },
            onTogglePinch = { scope.launch { settingsManager.updateSetting(SettingsManager.KEY_PINCH, it) } },
            onToggleDoubleTap = { scope.launch { settingsManager.updateSetting(SettingsManager.KEY_DOUBLE_TAP, it) } },
            onToggleHold2x = { scope.launch { settingsManager.updateSetting(SettingsManager.KEY_HOLD_2X, it) } },
            onToggleAutoPip = { scope.launch { settingsManager.updateSetting(SettingsManager.KEY_AUTO_PIP, it) } }
        )
    }

    if (showHistorySheet) {
        ModalBottomSheet(
            onDismissRequest = { showHistorySheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Playback History",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (historyList.isNotEmpty()) {
                        TextButton(
                            onClick = { scope.launch { database.historyDao().clearAll() } }
                        ) {
                            Text("Clear All")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (historyList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No playback history yet",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 32.dp)
                    ) {
                        items(historyList, key = { it.id }) { item ->
                            ElevatedCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        streamTitle = item.title
                                        streamUrl = item.url
                                        userAgent = item.userAgent
                                        referer = item.referer
                                        customHeaders = item.rawHeaders
                                        showHistorySheet = false
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = item.url,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val dateStr = remember(item.timestamp) {
                                            SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                                                .format(Date(item.timestamp))
                                        }
                                        Text(
                                            text = dateStr,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            showHistorySheet = false
                                            onPlayStream(item.url, item.userAgent, item.referer, item.rawHeaders)
                                        }
                                    ) {
                                        Icon(
                                            Icons.Default.PlayCircleFilled,
                                            contentDescription = "Play Now",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    IconButton(
                                        onClick = { scope.launch { database.historyDao().delete(item) } }
                                    ) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GestureSettingsDialog(
    settings: GestureSettings,
    onDismiss: () -> Unit,
    onToggleSwipe: (Boolean) -> Unit,
    onTogglePinch: (Boolean) -> Unit,
    onToggleDoubleTap: (Boolean) -> Unit,
    onToggleHold2x: (Boolean) -> Unit,
    onToggleAutoPip: (Boolean) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Player & Gesture Settings",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingToggleRow(
                    title = "Volume & Brightness Swipe",
                    subtitle = "Left swipe for Brightness, Right for Volume (Status bar top zone protected)",
                    checked = settings.swipeVolumeBrightness,
                    onCheckedChange = onToggleSwipe
                )
                SettingToggleRow(
                    title = "Pinch In / Out Zoom",
                    subtitle = "Two-finger pinch gesture to scale video",
                    checked = settings.pinchToZoom,
                    onCheckedChange = onTogglePinch
                )
                SettingToggleRow(
                    title = "Double-Tap 10s Seek",
                    subtitle = "Double tap left or right side to seek 10 seconds",
                    checked = settings.doubleTapSeek,
                    onCheckedChange = onToggleDoubleTap
                )
                SettingToggleRow(
                    title = "Hold for 2x Speed",
                    subtitle = "Long press anywhere on video for temporary 2x playback",
                    checked = settings.holdFor2xSpeed,
                    onCheckedChange = onToggleHold2x
                )
                SettingToggleRow(
                    title = "Auto Picture-in-Picture",
                    subtitle = "Enter PiP mode when pressing Home while playing",
                    checked = settings.autoPip,
                    onCheckedChange = onToggleAutoPip
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

@Composable
fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
""",

    "app/src/main/java/com/streamplay/player/PlayerActivity.kt": """package com.streamplay.player

import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.TextureView
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import com.streamplay.player.data.GestureSettings
import com.streamplay.player.data.SettingsManager
import com.streamplay.player.ui.theme.StreamPlayTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import kotlin.math.abs

@OptIn(UnstableApi::class)
class PlayerActivity : ComponentActivity() {
    private var exoPlayer: ExoPlayer? = null
    private var isInPipModeState by mutableStateOf(false)
    private var autoPipEnabled = true

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()

        val streamUrl = intent.getStringExtra("STREAM_URL") ?: ""
        val userAgent = intent.getStringExtra("USER_AGENT") ?: ""
        val referer = intent.getStringExtra("REFERER") ?: ""
        val rawHeaders = intent.getStringExtra("RAW_HEADERS") ?: ""

        val headersMap = parseHeaders(userAgent, referer, rawHeaders)
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        val okHttpFactory = OkHttpDataSource.Factory(okHttpClient).apply {
            if (userAgent.isNotBlank()) {
                setUserAgent(userAgent)
            } else {
                setUserAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36")
            }
            if (headersMap.isNotEmpty()) {
                setDefaultRequestProperties(headersMap)
            }
        }

        val dataSourceFactory = DefaultDataSource.Factory(this, okHttpFactory)
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build().apply {
                setMediaItem(MediaItem.fromUri(Uri.parse(streamUrl)))
                prepare()
                playWhenReady = true
            }

        exoPlayer = player
        val settingsManager = SettingsManager(this)

        setContent {
            val gestureSettings by settingsManager.settingsFlow.collectAsState(initial = GestureSettings())
            LaunchedEffect(gestureSettings.autoPip) {
                autoPipEnabled = gestureSettings.autoPip
            }

            StreamPlayTheme(darkTheme = true) {
                VideoPlayerScreen(
                    player = player,
                    settingsManager = settingsManager,
                    gestureSettings = gestureSettings,
                    isInPipMode = isInPipModeState,
                    onEnterPip = { enterPip() },
                    onToggleRotation = { toggleRotation() },
                    onBack = { finish() },
                    onAdjustBrightness = { delta -> adjustWindowBrightness(delta) }
                )
            }
        }
    }

    private fun parseHeaders(userAgent: String, referer: String, raw: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        if (referer.isNotBlank()) {
            map["Referer"] = referer
        }
        raw.lines().forEach { line ->
            val index = line.indexOf(":")
            if (index > 0) {
                val key = line.substring(0, index).trim()
                val value = line.substring(index + 1).trim()
                if (key.isNotEmpty() && value.isNotEmpty()) {
                    map[key] = value
                }
            }
        }
        return map
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun adjustWindowBrightness(delta: Float): Float {
        val lp = window.attributes
        val current = if (lp.screenBrightness < 0f) 0.5f else lp.screenBrightness
        val updated = (current + delta).coerceIn(0.02f, 1.0f)
        lp.screenBrightness = updated
        window.attributes = lp
        return updated
    }

    private fun toggleRotation() {
        requestedOrientation = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    private fun enterPip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (autoPipEnabled && exoPlayer?.isPlaying == true) {
            enterPip()
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipModeState = isInPictureInPictureMode
        if (!isInPictureInPictureMode) {
            hideSystemBars()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer?.release()
        exoPlayer = null
    }
}

data class VideoTrackOption(
    val name: String,
    val group: Tracks.Group?,
    val trackIndex: Int
)

data class AudioTrackOption(
    val name: String,
    val group: Tracks.Group,
    val trackIndex: Int
)

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    player: ExoPlayer,
    settingsManager: SettingsManager,
    gestureSettings: GestureSettings,
    isInPipMode: Boolean,
    onEnterPip: () -> Unit,
    onToggleRotation: () -> Unit,
    onBack: () -> Unit,
    onAdjustBrightness: (Float) -> Float
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }

    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var playbackState by remember { mutableIntStateOf(player.playbackState) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isLive by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }

    var controlsVisible by remember { mutableStateOf(true) }
    var isLocked by remember { mutableStateOf(false) }
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffsetX by remember { mutableFloatStateOf(0f) }
    var panOffsetY by remember { mutableFloatStateOf(0f) }

    var statusOverlayText by remember { mutableStateOf<String?>(null) }
    var isHolding2x by remember { mutableStateOf(false) }

    var showQualityDialog by remember { mutableStateOf(false) }
    var showAudioDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    var videoTracks by remember { mutableStateOf<List<VideoTrackOption>>(emptyList()) }
    var audioTracks by remember { mutableStateOf<List<AudioTrackOption>>(emptyList()) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                playbackState = state
                duration = player.duration.coerceAtLeast(0L)
                isLive = player.isCurrentMediaItemLive
            }

            override fun onTracksChanged(tracks: Tracks) {
                val vList = mutableListOf(VideoTrackOption("Auto Quality", null, -1))
                val aList = mutableListOf<AudioTrackOption>()

                for (group in tracks.groups) {
                    if (group.type == C.TRACK_TYPE_VIDEO) {
                        for (i in 0 until group.length) {
                            if (group.isTrackSupported(i)) {
                                val format = group.getTrackFormat(i)
                                val height = format.height
                                val bitrateKbps = if (format.bitrate > 0) "${format.bitrate / 1000} kbps" else ""
                                val label = if (height > 0) "${height}p $bitrateKbps".trim() else "Video Track ${i + 1}"
                                vList.add(VideoTrackOption(label, group, i))
                            }
                        }
                    } else if (group.type == C.TRACK_TYPE_AUDIO) {
                        for (i in 0 until group.length) {
                            if (group.isTrackSupported(i)) {
                                val format = group.getTrackFormat(i)
                                val lang = format.language?.let { Locale(it).displayLanguage } ?: "Track ${i + 1}"
                                val label = listOfNotNull(lang, format.label).joinToString(" - ").ifBlank { "Audio ${i + 1}" }
                                aList.add(AudioTrackOption(label, group, i))
                            }
                        }
                    }
                }
                videoTracks = vList
                audioTracks = aList
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(player) {
        while (true) {
            currentPosition = player.currentPosition.coerceAtLeast(0L)
            duration = player.duration.coerceAtLeast(0L)
            isLive = player.isCurrentMediaItemLive
            delay(500)
        }
    }

    LaunchedEffect(controlsVisible, isPlaying, isLocked) {
        if (controlsVisible && isPlaying) {
            delay(4000)
            controlsVisible = false
        }
    }

    LaunchedEffect(statusOverlayText) {
        if (statusOverlayText != null && !isHolding2x) {
            delay(900)
            statusOverlayText = null
        }
    }

    var volumeAccumulator by remember { mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(gestureSettings, isLocked, isInPipMode) {
                if (isInPipMode) return@pointerInput
                detectTapGestures(
                    onTap = {
                        controlsVisible = !controlsVisible
                    },
                    onDoubleTap = { offset ->
                        if (!isLocked && gestureSettings.doubleTapSeek) {
                            val width = size.width
                            if (offset.x < width * 0.45f) {
                                val target = (player.currentPosition - 10_000L).coerceAtLeast(0L)
                                player.seekTo(target)
                                statusOverlayText = "Rewind 10s"
                            } else if (offset.x > width * 0.55f) {
                                val target = (player.currentPosition + 10_000L).coerceAtMost(player.duration.coerceAtLeast(player.currentPosition + 10_000L))
                                player.seekTo(target)
                                statusOverlayText = "Forward 10s"
                            }
                        }
                    },
                    onLongPress = {
                        if (!isLocked && gestureSettings.holdFor2xSpeed) {
                            isHolding2x = true
                            player.setPlaybackSpeed(2.0f)
                            statusOverlayText = "2x Speed Active"
                        }
                    }
                )
            }
            .pointerInput(gestureSettings, isLocked, isInPipMode) {
                if (isInPipMode) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val screenHeight = size.height.toFloat().coerceAtLeast(1f)
                    val screenWidth = size.width.toFloat().coerceAtLeast(1f)

                    // TOP 12% DEAD ZONE: Ignore vertical swipe if user pulls down from status bar area
                    val startedInStatusBarZone = down.position.y < (screenHeight * 0.12f)
                    val isLeftSide = down.position.x < (screenWidth * 0.5f)
                    var verticalDragMode = false
                    var totalVerticalDelta = 0f

                    volumeAccumulator = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()

                    do {
                        val event = awaitPointerEvent()
                        val pointerCount = event.changes.count { it.pressed }

                        if (!isLocked && pointerCount >= 2 && gestureSettings.pinchToZoom) {
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            if (zoomChange != 1f) {
                                zoomScale = (zoomScale * zoomChange).coerceIn(0.5f, 4.0f)
                                if (zoomScale <= 1.02f) {
                                    panOffsetX = 0f
                                    panOffsetY = 0f
                                } else {
                                    panOffsetX += panChange.x
                                    panOffsetY += panChange.y
                                }
                                statusOverlayText = "Zoom ${(zoomScale * 100).toInt()}%"
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        } else if (!isLocked && pointerCount == 1 && !startedInStatusBarZone && gestureSettings.swipeVolumeBrightness && !isHolding2x) {
                            val change = event.changes.firstOrNull()
                            if (change != null && change.positionChanged()) {
                                val dy = change.position.y - change.previousPosition.y
                                val dx = change.position.x - change.previousPosition.x

                                if (!verticalDragMode && abs(dy) > 12f && abs(dy) > abs(dx) * 1.5f) {
                                    verticalDragMode = true
                                }

                                if (verticalDragMode) {
                                    totalVerticalDelta += dy
                                    val sensitivity = screenHeight * 0.65f
                                    val deltaRatio = -dy / sensitivity

                                    if (isLeftSide) {
                                        val newBright = onAdjustBrightness(deltaRatio)
                                        statusOverlayText = "Brightness ${(newBright * 100).toInt()}%"
                                    } else {
                                        volumeAccumulator = (volumeAccumulator + deltaRatio * maxVolume).coerceIn(0f, maxVolume.toFloat())
                                        val targetVol = volumeAccumulator.toInt()
                                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                                        val pct = ((volumeAccumulator / maxVolume) * 100).toInt()
                                        statusOverlayText = "Volume $pct%"
                                    }
                                    change.consume()
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    if (isHolding2x) {
                        isHolding2x = false
                        player.setPlaybackSpeed(playbackSpeed)
                        statusOverlayText = null
                    }
                }
            }
    ) {
        // Video Surface using TextureView for smooth pinch-to-zoom scaling
        AndroidView(
            factory = { ctx ->
                AspectRatioFrameLayout(ctx).apply {
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    val textureView = TextureView(ctx)
                    addView(textureView)
                    player.setVideoTextureView(textureView)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = zoomScale,
                    scaleY = zoomScale,
                    translationX = panOffsetX,
                    translationY = panOffsetY
                )
        )

        // Buffering indicator
        if (playbackState == Player.STATE_BUFFERING) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Gesture feedback badge (Volume / Brightness / 2x / Zoom / Seek)
        statusOverlayText?.let { text ->
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 48.dp),
                shape = RoundedCornerShape(24.dp),
                color = Color.Black.copy(alpha = 0.75f)
            ) {
                Text(
                    text = text,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                )
            }
        }

        // Full Custom Controls Overlay
        AnimatedVisibility(
            visible = controlsVisible && !isInPipMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
            ) {
                if (isLocked) {
                    // Lock Mode UI
                    IconButton(
                        onClick = { isLocked = false },
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 24.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Unlock Controls",
                            tint = Color.White
                        )
                    }
                } else {
                    // Top Controls Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Go Live Button
                            TextButton(
                                onClick = {
                                    if (player.isCurrentMediaItemLive) {
                                        player.seekToDefaultPosition()
                                    } else if (duration > 0) {
                                        player.seekTo(duration)
                                    }
                                },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = if (isLive) MaterialTheme.colorScheme.primary else Color.White
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Sensors,
                                    contentDescription = "Go Live",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("GO LIVE", fontWeight = FontWeight.Bold)
                            }

                            // Video Quality Selection
                            IconButton(onClick = { showQualityDialog = true }) {
                                Icon(Icons.Default.HighQuality, contentDescription = "Video Quality", tint = Color.White)
                            }

                            // Audio Language Selection
                            IconButton(onClick = { showAudioDialog = true }) {
                                Icon(Icons.Default.Audiotrack, contentDescription = "Audio Track", tint = Color.White)
                            }

                            // Speed Control Button
                            TextButton(onClick = { showSpeedDialog = true }) {
                                Text(
                                    text = "${playbackSpeed}x",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Gesture Settings Button inside Player
                            IconButton(onClick = { showSettingsDialog = true }) {
                                Icon(Icons.Default.Tune, contentDescription = "Gesture Settings", tint = Color.White)
                            }
                        }
                    }

                    // Center Play/Pause & 10s Skip Controls
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(36.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L))
                            },
                            modifier = Modifier
                                .size(52.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(
                                Icons.Default.Replay10,
                                contentDescription = "Rewind 10s",
                                tint = Color.White,
                                modifier = Modifier.size(30.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                if (player.playbackState == Player.STATE_ENDED) {
                                    player.seekTo(0)
                                    player.play()
                                } else if (player.isPlaying) {
                                    player.pause()
                                } else {
                                    player.play()
                                }
                            },
                            modifier = Modifier
                                .size(68.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play or Pause",
                                tint = Color.White,
                                modifier = Modifier.size(40.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                player.seekTo(player.currentPosition + 10_000L)
                            },
                            modifier = Modifier
                                .size(52.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(
                                Icons.Default.Forward10,
                                contentDescription = "Forward 10s",
                                tint = Color.White,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }

                    // Bottom Bar: Seekbar + Lock, Mute, Rotation, PiP, Reset Zoom
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatDuration(currentPosition),
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Slider(
                                value = if (duration > 0) currentPosition.toFloat().coerceIn(0f, duration.toFloat()) else 0f,
                                onValueChange = { newPos ->
                                    currentPosition = newPos.toLong()
                                    player.seekTo(currentPosition)
                                },
                                valueRange = 0f..(duration.toFloat().coerceAtLeast(1f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 12.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary
                                )
                            )
                            Text(
                                text = if (isLive && duration <= 0) "LIVE" else formatDuration(duration),
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                // Lock Button
                                IconButton(onClick = { isLocked = true }) {
                                    Icon(Icons.Default.LockOpen, contentDescription = "Lock Screen", tint = Color.White)
                                }

                                // Speaker Mute / Unmute Button
                                IconButton(
                                    onClick = {
                                        isMuted = !isMuted
                                        player.volume = if (isMuted) 0f else 1f
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                        contentDescription = "Mute or Unmute",
                                        tint = Color.White
                                    )
                                }

                                if (zoomScale != 1f) {
                                    TextButton(
                                        onClick = {
                                            zoomScale = 1f
                                            panOffsetX = 0f
                                            panOffsetY = 0f
                                        }
                                    ) {
                                        Text("Reset Zoom", color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                // Picture in Picture Button
                                IconButton(onClick = onEnterPip) {
                                    Icon(
                                        Icons.Default.PictureInPictureAlt,
                                        contentDescription = "Picture in Picture",
                                        tint = Color.White
                                    )
                                }

                                // Screen Rotation Button
                                IconButton(onClick = onToggleRotation) {
                                    Icon(
                                        Icons.Default.ScreenRotation,
                                        contentDescription = "Rotate Screen",
                                        tint = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Quality Selection Dialog
    if (showQualityDialog) {
        AlertDialog(
            onDismissRequest = { showQualityDialog = false },
            title = { Text("Select Video Quality", fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn {
                    items(videoTracks) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val builder = player.trackSelectionParameters.buildUpon()
                                    if (item.group == null) {
                                        builder.clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                                    } else {
                                        builder.clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                                        builder.addOverride(
                                            TrackSelectionOverride(
                                                item.group.mediaTrackGroup,
                                                listOf(item.trackIndex)
                                            )
                                        )
                                    }
                                    player.trackSelectionParameters = builder.build()
                                    showQualityDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.HighQuality, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = item.name, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showQualityDialog = false }) { Text("Close") }
            }
        )
    }

    // Audio Language Selection Dialog
    if (showAudioDialog) {
        AlertDialog(
            onDismissRequest = { showAudioDialog = false },
            title = { Text("Select Audio Language", fontWeight = FontWeight.Bold) },
            text = {
                if (audioTracks.isEmpty()) {
                    Text("Default Audio Track (No multi-audio tracks detected)")
                } else {
                    LazyColumn {
                        items(audioTracks) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val builder = player.trackSelectionParameters.buildUpon()
                                        builder.clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                                        builder.addOverride(
                                            TrackSelectionOverride(
                                                item.group.mediaTrackGroup,
                                                listOf(item.trackIndex)
                                            )
                                        )
                                        player.trackSelectionParameters = builder.build()
                                        showAudioDialog = false
                                    }
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Audiotrack, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(text = item.name, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAudioDialog = false }) { Text("Close") }
            }
        )
    }

    // Playback Speed Dialog
    if (showSpeedDialog) {
        val speeds = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f)
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            title = { Text("Playback Speed", fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn {
                    items(speeds) { spd ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    playbackSpeed = spd
                                    player.setPlaybackSpeed(spd)
                                    showSpeedDialog = false
                                }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "${spd}x", style = MaterialTheme.typography.bodyLarge)
                            if (playbackSpeed == spd) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSpeedDialog = false }) { Text("Close") }
            }
        )
    }

    // In-Player Gesture Settings Dialog
    if (showSettingsDialog) {
        GestureSettingsDialog(
            settings = gestureSettings,
            onDismiss = { showSettingsDialog = false },
            onToggleSwipe = { scope.launch { settingsManager.updateSetting(SettingsManager.KEY_SWIPE, it) } },
            onTogglePinch = { scope.launch { settingsManager.updateSetting(SettingsManager.KEY_PINCH, it) } },
            onToggleDoubleTap = { scope.launch { settingsManager.updateSetting(SettingsManager.KEY_DOUBLE_TAP, it) } },
            onToggleHold2x = { scope.launch { settingsManager.updateSetting(SettingsManager.KEY_HOLD_2X, it) } },
            onToggleAutoPip = { scope.launch { settingsManager.updateSetting(SettingsManager.KEY_AUTO_PIP, it) } }
        )
    }
}

private fun formatDuration(millis: Long): String {
    if (millis <= 0L) return "00:00"
    val totalSeconds = millis / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}
"""
}

for path, content in files.items():
    dir_name = os.path.dirname(path)
    if dir_name:
        os.makedirs(dir_name, exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)

print("StreamPlay Android Project generated successfully.")
