import os
import zipfile

files = {
    "settings.gradle": """pluginManagement {
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
rootProject.name = "BlueStreamPlayer"
include ':app'
""",

    "build.gradle": """plugins {
    id 'com.android.application' version '8.2.2' apply false
    id 'org.jetbrains.kotlin.android' version '1.9.22' apply false
}
""",

    "gradle.properties": """org.gradle.jvmargs=-Xmx3072m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
""",

    "app/build.gradle": """plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
}

android {
    namespace 'com.stream.blueplayer'
    compileSdk 34

    defaultConfig {
        applicationId "com.stream.blueplayer"
        minSdk 24
        targetSdk 34
        versionCode 2
        versionName "2.0"

        ndk {
            // Includes lib/arm64-v8a/libdatastore_shared_counter.so and 32-bit fallback
            abiFilters 'arm64-v8a', 'armeabi-v7a'
        }
    }

    packagingOptions {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    buildTypes {
        release {
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = '17'
    }
    buildFeatures {
        viewBinding true
    }
}

dependencies {
    implementation 'androidx.core:core-ktx:1.12.0'
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'com.google.android.material:material:1.11.0'
    implementation 'androidx.constraintlayout:constraintlayout:2.1.4'
    implementation 'androidx.lifecycle:lifecycle-runtime-ktx:2.7.0'
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
    implementation 'com.google.code.gson:gson:2.10.1'

    // Jetpack DataStore (Packs native lib/arm64-v8a/libdatastore_shared_counter.so for ultra-fast I/O)
    implementation "androidx.datastore:datastore-preferences:1.1.1"
    implementation "androidx.datastore:datastore-core:1.1.1"

    // AndroidX Media3 (ExoPlayer) - Full format support
    def media3_version = "1.3.0"
    implementation "androidx.media3:media3-exoplayer:$media3_version"
    implementation "androidx.media3:media3-exoplayer-hls:$media3_version"
    implementation "androidx.media3:media3-exoplayer-dash:$media3_version"
    implementation "androidx.media3:media3-exoplayer-smoothstreaming:$media3_version"
    implementation "androidx.media3:media3-exoplayer-rtsp:$media3_version"
    implementation "androidx.media3:media3-ui:$media3_version"
    implementation "androidx.media3:media3-common:$media3_version"
}
""",

    "app/src/main/AndroidManifest.xml": """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />

    <application
        android:allowBackup="true"
        android:hardwareAccelerated="true"
        android:extractNativeLibs="true"
        android:label="Network Stream (Video) Player"
        android:supportsRtl="true"
        android:usesCleartextTraffic="true"
        android:theme="@style/Theme.BlueStreamPlayer"
        tools:targetApi="31">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:windowSoftInputMode="adjustPan">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <activity
            android:name=".HistoryActivity"
            android:exported="false" />

        <activity
            android:name=".PlayerActivity"
            android:configChanges="keyboard|keyboardHidden|orientation|screenSize|screenLayout|smallestScreenSize|uiMode"
            android:exported="true"
            android:launchMode="singleTask"
            android:supportsPictureInPicture="true" />
    </application>
</manifest>
""",

    "app/src/main/res/values/colors.xml": """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="bg_dark">#141210</color>
    <color name="surface_dark">#1D1A18</color>
    <color name="blue_accent">#2979FF</color>
    <color name="blue_stroke_dim">#4D2979FF</color>
    <color name="border_subtle">#524943</color>
    <color name="text_primary">#E6E1DE</color>
    <color name="text_secondary">#B8AEA8</color>
    <color name="player_overlay">#88000000</color>
</resources>
""",

    "app/src/main/res/values/themes.xml": """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.BlueStreamPlayer" parent="Theme.Material3.Dark.NoActionBar">
        <item name="colorPrimary">@color/blue_accent</item>
        <item name="colorOnPrimary">@android:color/white</item>
        <item name="colorSecondary">@color/blue_accent</item>
        <item name="android:windowBackground">@color/bg_dark</item>
        <item name="android:statusBarColor">@android:color/transparent</item>
        <item name="android:navigationBarColor">@android:color/transparent</item>
    </style>

    <style name="StreamInputBox" parent="Widget.Material3.TextInputLayout.OutlinedBox">
        <item name="boxStrokeColor">@color/input_stroke_selector</item>
        <item name="boxStrokeWidth">1dp</item>
        <item name="boxStrokeWidthFocused">1.8dp</item>
        <item name="boxCornerRadiusTopStart">5dp</item>
        <item name="boxCornerRadiusTopEnd">5dp</item>
        <item name="boxCornerRadiusBottomStart">5dp</item>
        <item name="boxCornerRadiusBottomEnd">5dp</item>
        <item name="hintTextColor">@color/blue_accent</item>
        <item name="android:textColorHint">@color/text_secondary</item>
        <item name="boxBackgroundColor">@android:color/transparent</item>
    </style>

    <style name="StreamDropdownBox" parent="Widget.Material3.TextInputLayout.OutlinedBox.ExposedDropdownMenu">
        <item name="boxStrokeColor">@color/input_stroke_selector</item>
        <item name="boxStrokeWidth">1dp</item>
        <item name="boxStrokeWidthFocused">1.8dp</item>
        <item name="boxCornerRadiusTopStart">5dp</item>
        <item name="boxCornerRadiusTopEnd">5dp</item>
        <item name="boxCornerRadiusBottomStart">5dp</item>
        <item name="boxCornerRadiusBottomEnd">5dp</item>
        <item name="hintTextColor">@color/text_secondary</item>
        <item name="android:textColorHint">@color/text_secondary</item>
        <item name="endIconTint">@color/text_secondary</item>
        <item name="boxBackgroundColor">@android:color/transparent</item>
    </style>

    <style name="RoundedSquareFab" parent="">
        <item name="cornerFamily">rounded</item>
        <item name="cornerSize">16dp</item>
    </style>
</resources>
""",

    "app/src/main/res/color/input_stroke_selector.xml": """<?xml version="1.0" encoding="utf-8"?>
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:color="@color/blue_accent" android:state_focused="true"/>
    <item android:color="@color/blue_accent" android:state_Activated="true"/>
    <item android:color="@color/border_subtle"/>
</selector>
""",

    # Custom Vector Drawables matching the exact reference UI screenshots
    "app/src/main/res/drawable/ic_history_clock.xml": """<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="@color/text_primary"
        android:pathData="M13,3a9,9 0,0 0,-9 9H1l3.89,3.89 0.07,0.14L9,12H6c0,-3.87 3.13,-7 7,-7s7,3.13 7,7 -3.13,7 -7,7c-1.93,0 -3.68,-0.79 -4.94,-2.06l-1.42,1.42A8.954,8.954 0,0 0,13 21a9,9 0,0 0,0 -18zm-1,5v5l4.28,2.54 0.72,-1.21 -3.5,-2.08V8H12z"/>
</vector>
""",

    "app/src/main/res/drawable/ic_arrow_back.xml": """<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="@color/text_primary"
        android:pathData="M20,11H7.83l5.59,-5.59L12,4l-8,8 8,8 1.41,-1.41L7.83,13H20v-2z"/>
</vector>
""",

    "app/src/main/res/drawable/ic_sort_lines.xml": """<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="@color/text_primary"
        android:pathData="M3,18h6v-2H3v2zM3,6v2h18V6H3zm0,7h12v-2H3v2z"/>
</vector>
""",

    "app/src/main/res/drawable/ic_delete_bin.xml": """<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="@color/text_secondary"
        android:pathData="M6,19c0,1.1 0.9,2 2,2h8c1.1,0 2,-0.9 2,-2V7H6v12zM19,4h-3.5l-1,-1h-5l-1,1H5v2h14V4z"/>
</vector>
""",

    "app/src/main/res/drawable/ic_play_arrow.xml": """<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="28dp"
    android:height="28dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M8,5v14l11,-7z"/>
</vector>
""",

    "app/src/main/res/drawable/ic_pause.xml": """<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="28dp"
    android:height="28dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M6,19h4V5H6v14zm8,-14v14h4V5h-4z"/>
</vector>
""",

    "app/src/main/res/drawable/pill_blue_bg.xml": """<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="@color/blue_accent" />
    <corners android:radius="20dp" />
</shape>
""",

    "app/src/main/res/layout/activity_main.xml": """<?xml version="1.0" encoding="utf-8"?>
<androidx.coordinatorlayout.widget.CoordinatorLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/mainRoot"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/bg_dark">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:orientation="vertical"
        android:paddingHorizontal="16dp">

        <!-- Exact Screenshot Toolbar -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="60dp"
            android:gravity="center_vertical"
            android:orientation="horizontal">

            <TextView
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:text="Network Stream (Video) Player"
                android:textColor="@color/text_primary"
                android:textSize="21sp"
                android:fontFamily="sans-serif" />

            <ImageButton
                android:id="@+id/btnHistory"
                android:layout_width="42dp"
                android:layout_height="42dp"
                android:background="?attr/selectableItemBackgroundBorderless"
                android:contentDescription="History"
                android:src="@drawable/ic_history_clock" />
        </LinearLayout>

        <androidx.core.widget.NestedScrollView
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:overScrollMode="never"
            android:scrollbars="none">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:paddingTop="10dp"
                android:paddingBottom="100dp">

                <com.google.android.material.textfield.TextInputLayout
                    style="@style/StreamInputBox"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginBottom="18dp"
                    android:hint="Media Stream URL">
                    <com.google.android.material.textfield.TextInputEditText
                        android:id="@+id/etStreamUrl"
                        android:layout_width="match_parent"
                        android:layout_height="58dp"
                        android:inputType="textUri"
                        android:paddingHorizontal="16dp"
                        android:textColor="@color/text_primary"
                        android:textSize="16sp" />
                </com.google.android.material.textfield.TextInputLayout>

                <com.google.android.material.textfield.TextInputLayout
                    style="@style/StreamInputBox"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginBottom="18dp"
                    android:hint="Cookie Value">
                    <com.google.android.material.textfield.TextInputEditText
                        android:id="@+id/etCookie"
                        android:layout_width="match_parent"
                        android:layout_height="58dp"
                        android:inputType="text"
                        android:paddingHorizontal="16dp"
                        android:textColor="@color/text_primary"
                        android:textSize="16sp" />
                </com.google.android.material.textfield.TextInputLayout>

                <com.google.android.material.textfield.TextInputLayout
                    style="@style/StreamInputBox"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginBottom="18dp"
                    android:hint="Referer Value">
                    <com.google.android.material.textfield.TextInputEditText
                        android:id="@+id/etReferer"
                        android:layout_width="match_parent"
                        android:layout_height="58dp"
                        android:inputType="textUri"
                        android:paddingHorizontal="16dp"
                        android:textColor="@color/text_primary"
                        android:textSize="16sp" />
                </com.google.android.material.textfield.TextInputLayout>

                <com.google.android.material.textfield.TextInputLayout
                    style="@style/StreamInputBox"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginBottom="18dp"
                    android:hint="Origin Value">
                    <com.google.android.material.textfield.TextInputEditText
                        android:id="@+id/etOrigin"
                        android:layout_width="match_parent"
                        android:layout_height="58dp"
                        android:inputType="textUri"
                        android:paddingHorizontal="16dp"
                        android:textColor="@color/text_primary"
                        android:textSize="16sp" />
                </com.google.android.material.textfield.TextInputLayout>

                <com.google.android.material.textfield.TextInputLayout
                    style="@style/StreamInputBox"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginBottom="18dp"
                    android:hint="DRM License URL">
                    <com.google.android.material.textfield.TextInputEditText
                        android:id="@+id/etDrmUrl"
                        android:layout_width="match_parent"
                        android:layout_height="58dp"
                        android:inputType="textUri"
                        android:paddingHorizontal="16dp"
                        android:textColor="@color/text_primary"
                        android:textSize="16sp" />
                </com.google.android.material.textfield.TextInputLayout>

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="horizontal"
                    android:baselineAligned="false">

                    <com.google.android.material.textfield.TextInputLayout
                        style="@style/StreamDropdownBox"
                        android:layout_width="0dp"
                        android:layout_height="wrap_content"
                        android:layout_weight="1"
                        android:layout_marginEnd="6dp"
                        android:hint="UserAgent">
                        <AutoCompleteTextView
                            android:id="@+id/actvUserAgent"
                            android:layout_width="match_parent"
                            android:layout_height="56dp"
                            android:inputType="none"
                            android:paddingHorizontal="14dp"
                            android:text="Custom"
                            android:textColor="@color/text_primary"
                            android:textSize="16sp" />
                    </com.google.android.material.textfield.TextInputLayout>

                    <com.google.android.material.textfield.TextInputLayout
                        style="@style/StreamDropdownBox"
                        android:layout_width="0dp"
                        android:layout_height="wrap_content"
                        android:layout_weight="1"
                        android:layout_marginStart="6dp"
                        android:hint="DrmScheme"
                        app:boxStrokeColor="@color/blue_accent">
                        <AutoCompleteTextView
                            android:id="@+id/actvDrmScheme"
                            android:layout_width="match_parent"
                            android:layout_height="56dp"
                            android:inputType="none"
                            android:paddingHorizontal="14dp"
                            android:text="widevine"
                            android:textColor="@color/text_primary"
                            android:textSize="16sp" />
                    </com.google.android.material.textfield.TextInputLayout>
                </LinearLayout>

                <com.google.android.material.textfield.TextInputLayout
                    android:id="@+id/tilCustomUa"
                    style="@style/StreamInputBox"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="18dp"
                    android:visibility="visible"
                    android:hint="Custom UserAgent Value">
                    <com.google.android.material.textfield.TextInputEditText
                        android:id="@+id/etCustomUa"
                        android:layout_width="match_parent"
                        android:layout_height="58dp"
                        android:inputType="text"
                        android:paddingHorizontal="16dp"
                        android:textColor="@color/text_primary"
                        android:textSize="16sp" />
                </com.google.android.material.textfield.TextInputLayout>

            </LinearLayout>
        </androidx.core.widget.NestedScrollView>
    </LinearLayout>

    <!-- Exact Rounded Square Floating Action Button in Blue -->
    <com.google.android.material.floatingactionbutton.FloatingActionButton
        android:id="@+id/fabPlay"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="bottom|end"
        android:layout_marginEnd="22dp"
        android:layout_marginBottom="28dp"
        android:contentDescription="Play"
        android:src="@drawable/ic_play_arrow"
        app:backgroundTint="@color/blue_accent"
        app:tint="@android:color/white"
        app:fabCustomSize="58dp"
        app:maxImageSize="28dp"
        app:shapeAppearanceOverlay="@style/RoundedSquareFab" />

</androidx.coordinatorlayout.widget.CoordinatorLayout>
""",

    "app/src/main/res/layout/activity_history.xml": """<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/historyRoot"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/bg_dark"
    android:orientation="vertical">

    <!-- Exact History Header matching Screenshot -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="60dp"
        android:gravity="center_vertical"
        android:orientation="horizontal"
        android:paddingHorizontal="12dp">

        <ImageButton
            android:id="@+id/btnBack"
            android:layout_width="42dp"
            android:layout_height="42dp"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:src="@drawable/ic_arrow_back" />

        <TextView
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_marginStart="12dp"
            android:layout_weight="1"
            android:text="History"
            android:textColor="@color/text_primary"
            android:textSize="22sp" />

        <ImageButton
            android:id="@+id/btnSort"
            android:layout_width="44dp"
            android:layout_height="44dp"
            android:layout_marginEnd="4dp"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:src="@drawable/ic_sort_lines" />

        <ImageButton
            android:id="@+id/btnClearAll"
            android:layout_width="44dp"
            android:layout_height="44dp"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:src="@drawable/ic_delete_bin" />
    </LinearLayout>

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/rvHistory"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:clipToPadding="false"
        android:overScrollMode="never"
        android:paddingHorizontal="14dp"
        android:paddingTop="8dp"
        android:paddingBottom="24dp" />
</LinearLayout>
""",

    "app/src/main/res/layout/item_history.xml": """<?xml version="1.0" encoding="utf-8"?>
<com.google.android.material.card.MaterialCardView xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginBottom="12dp"
    app:cardBackgroundColor="@color/surface_dark"
    app:cardCornerRadius="12dp"
    app:cardElevation="0dp"
    app:strokeColor="@color/border_subtle"
    app:strokeWidth="1dp">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:gravity="center_vertical"
        android:orientation="horizontal"
        android:paddingVertical="14dp"
        android:paddingStart="16dp"
        android:paddingEnd="10dp">

        <TextView
            android:id="@+id/tvHistoryUrl"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:ellipsize="end"
            android:lineSpacingExtra="2dp"
            android:maxLines="2"
            android:textColor="@color/text_secondary"
            android:textSize="15sp" />

        <ImageButton
            android:id="@+id/btnDeleteItem"
            android:layout_width="40dp"
            android:layout_height="40dp"
            android:layout_marginStart="10dp"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:src="@drawable/ic_delete_bin" />
    </LinearLayout>
</com.google.android.material.card.MaterialCardView>
""",

    "app/src/main/res/layout/activity_player.xml": """<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/playerContainer"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@android:color/black"
    android:keepScreenOn="true">

    <androidx.media3.ui.PlayerView
        android:id="@+id/playerView"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        app:use_controller="false"
        app:surface_type="surface_view" />

    <View
        android:id="@+id/gestureOverlay"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />

    <TextView
        android:id="@+id/tvGestureIndicator"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="center"
        android:background="@drawable/pill_blue_bg"
        android:paddingHorizontal="20dp"
        android:paddingVertical="10dp"
        android:textColor="@android:color/white"
        android:textSize="16sp"
        android:textStyle="bold"
        android:visibility="gone" />

    <ProgressBar
        android:id="@+id/loadingIndicator"
        android:layout_width="50dp"
        android:layout_height="50dp"
        android:layout_gravity="center"
        android:indeterminateTint="@color/blue_accent"
        android:visibility="gone" />

    <!-- Smooth Custom Controls Overlay -->
    <FrameLayout
        android:id="@+id/controlsRoot"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:background="@color/player_overlay">

        <!-- Top Bar -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_gravity="top"
            android:gravity="center_vertical"
            android:orientation="horizontal"
            android:paddingHorizontal="16dp"
            android:paddingTop="18dp"
            android:paddingBottom="10dp">

            <ImageButton
                android:id="@+id/btnPlayerBack"
                android:layout_width="40dp"
                android:layout_height="40dp"
                android:background="?attr/selectableItemBackgroundBorderless"
                android:src="@drawable/ic_arrow_back" />

            <TextView
                android:id="@+id/tvStreamTitle"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_marginHorizontal="12dp"
                android:layout_weight="1"
                android:ellipsize="end"
                android:singleLine="true"
                android:text="Stream"
                android:textColor="@android:color/white"
                android:textSize="15sp"
                android:textStyle="bold" />

            <TextView
                android:id="@+id/btnQuality"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:background="?attr/selectableItemBackground"
                android:padding="8dp"
                android:text="Quality"
                android:textColor="@color/blue_accent"
                android:textStyle="bold" />

            <TextView
                android:id="@+id/btnAudio"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:background="?attr/selectableItemBackground"
                android:padding="8dp"
                android:text="Audio"
                android:textColor="@android:color/white" />

            <TextView
                android:id="@+id/btnSpeed"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:background="?attr/selectableItemBackground"
                android:padding="8dp"
                android:text="1.0x"
                android:textColor="@android:color/white" />

            <ImageButton
                android:id="@+id/btnPip"
                android:layout_width="40dp"
                android:layout_height="40dp"
                android:background="?attr/selectableItemBackgroundBorderless"
                android:src="@android:drawable/ic_menu_crop"
                app:tint="@android:color/white" />

            <ImageButton
                android:id="@+id/btnGestureSettings"
                android:layout_width="40dp"
                android:layout_height="40dp"
                android:background="?attr/selectableItemBackgroundBorderless"
                android:src="@android:drawable/ic_menu_preferences"
                app:tint="@color/blue_accent" />
        </LinearLayout>

        <!-- Center Play/Pause & 10s Seek -->
        <LinearLayout
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_gravity="center"
            android:gravity="center"
            android:orientation="horizontal">

            <ImageButton
                android:id="@+id/btnRewind"
                android:layout_width="52dp"
                android:layout_height="52dp"
                android:background="?attr/selectableItemBackgroundBorderless"
                android:src="@android:drawable/ic_media_rew"
                app:tint="@android:color/white" />

            <ImageButton
                android:id="@+id/btnPlayPause"
                android:layout_width="68dp"
                android:layout_height="68dp"
                android:layout_marginHorizontal="36dp"
                android:background="@drawable/pill_blue_bg"
                android:src="@drawable/ic_pause" />

            <ImageButton
                android:id="@+id/btnForward"
                android:layout_width="52dp"
                android:layout_height="52dp"
                android:background="?attr/selectableItemBackgroundBorderless"
                android:src="@android:drawable/ic_media_ff"
                app:tint="@android:color/white" />
        </LinearLayout>

        <!-- Bottom Bar -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_gravity="bottom"
            android:orientation="vertical"
            android:paddingHorizontal="16dp"
            android:paddingBottom="18dp">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:gravity="center_vertical"
                android:orientation="horizontal">

                <TextView
                    android:id="@+id/tvPosition"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="00:00"
                    android:textColor="@android:color/white"
                    android:textSize="13sp" />

                <SeekBar
                    android:id="@+id/seekBar"
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:progressTint="@color/blue_accent"
                    android:thumbTint="@color/blue_accent" />

                <TextView
                    android:id="@+id/tvDuration"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="00:00"
                    android:textColor="@android:color/white"
                    android:textSize="13sp" />
            </LinearLayout>

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="4dp"
                android:gravity="center_vertical"
                android:orientation="horizontal">

                <ImageButton
                    android:id="@+id/btnLock"
                    android:layout_width="40dp"
                    android:layout_height="40dp"
                    android:background="?attr/selectableItemBackgroundBorderless"
                    android:src="@android:drawable/ic_lock_lock"
                    app:tint="@android:color/white" />

                <ImageButton
                    android:id="@+id/btnMute"
                    android:layout_width="40dp"
                    android:layout_height="40dp"
                    android:layout_marginStart="12dp"
                    android:background="?attr/selectableItemBackgroundBorderless"
                    android:src="@android:drawable/ic_lock_silent_mode_off"
                    app:tint="@android:color/white" />

                <TextView
                    android:id="@+id/btnGoLive"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginStart="16dp"
                    android:background="@drawable/pill_blue_bg"
                    android:paddingHorizontal="12dp"
                    android:paddingVertical="4dp"
                    android:text="● LIVE"
                    android:textColor="@android:color/white"
                    android:textSize="12sp"
                    android:textStyle="bold" />

                <View
                    android:layout_width="0dp"
                    android:layout_height="1dp"
                    android:layout_weight="1" />

                <TextView
                    android:id="@+id/btnScaleMode"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:background="?attr/selectableItemBackground"
                    android:padding="8dp"
                    android:text="FIT"
                    android:textColor="@color/blue_accent"
                    android:textStyle="bold" />

                <ImageButton
                    android:id="@+id/btnRotate"
                    android:layout_width="40dp"
                    android:layout_height="40dp"
                    android:layout_marginStart="8dp"
                    android:background="?attr/selectableItemBackgroundBorderless"
                    android:src="@android:drawable/ic_menu_always_landscape_portrait"
                    app:tint="@android:color/white" />
            </LinearLayout>
        </LinearLayout>
    </FrameLayout>

    <ImageButton
        android:id="@+id/btnUnlockOnly"
        android:layout_width="50dp"
        android:layout_height="50dp"
        android:layout_gravity="center_vertical|start"
        android:layout_marginStart="24dp"
        android:background="@drawable/pill_blue_bg"
        android:src="@android:drawable/ic_lock_lock"
        android:visibility="gone"
        app:tint="@android:color/white" />

</FrameLayout>
""",

    "app/src/main/java/com/stream/blueplayer/StreamConfig.kt": """package com.stream.blueplayer

import java.io.Serializable

data class StreamConfig(
    val streamUrl: String,
    val cookie: String = "",
    val referer: String = "",
    val origin: String = "",
    val drmLicenseUrl: String = "",
    val userAgent: String = "",
    val drmScheme: String = "widevine",
    val timestamp: Long = System.currentTimeMillis()
) : Serializable
""",

    "app/src/main/java/com/stream/blueplayer/DataStoreManager.kt": """package com.stream.blueplayer

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Uses Jetpack DataStore backed by native lib/arm64-v8a/libdatastore_shared_counter.so
val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "blue_stream_datastore")

class DataStoreManager(private val context: Context) {
    private val gson = Gson()

    companion object {
        val KEY_HISTORY = stringPreferencesKey("history_json")
        val KEY_VOL_BRIGHT = booleanPreferencesKey("gesture_vol_bright")
        val KEY_PINCH = booleanPreferencesKey("gesture_pinch")
        val KEY_DOUBLE_TAP = booleanPreferencesKey("gesture_double_tap")
        val KEY_LONG_PRESS = booleanPreferencesKey("gesture_long_press")
    }

    val historyFlow: Flow<List<StreamConfig>> = context.appDataStore.data.map { prefs ->
        val json = prefs[KEY_HISTORY] ?: return@map emptyList()
        val type = object : TypeToken<List<StreamConfig>>() {}.type
        gson.fromJson<List<StreamConfig>>(json, type) ?: emptyList()
    }

    suspend fun saveStream(config: StreamConfig) {
        context.appDataStore.edit { prefs ->
            val currentJson = prefs[KEY_HISTORY]
            val type = object : TypeToken<MutableList<StreamConfig>>() {}.type
            val list: MutableList<StreamConfig> = if (currentJson != null) {
                gson.fromJson(currentJson, type) ?: mutableListOf()
            } else {
                mutableListOf()
            }
            list.removeAll { it.streamUrl.equals(config.streamUrl, ignoreCase = true) }
            list.add(0, config)
            prefs[KEY_HISTORY] = gson.toJson(list)
        }
    }

    suspend fun deleteStream(url: String) {
        context.appDataStore.edit { prefs ->
            val currentJson = prefs[KEY_HISTORY] ?: return@edit
            val type = object : TypeToken<MutableList<StreamConfig>>() {}.type
            val list: MutableList<StreamConfig> = gson.fromJson(currentJson, type) ?: return@edit
            list.removeAll { it.streamUrl == url }
            prefs[KEY_HISTORY] = gson.toJson(list)
        }
    }

    suspend fun clearHistory() {
        context.appDataStore.edit { prefs ->
            prefs.remove(KEY_HISTORY)
        }
    }

    val gestureSettingsFlow: Flow<GestureState> = context.appDataStore.data.map { prefs ->
        GestureState(
            volBright = prefs[KEY_VOL_BRIGHT] ?: true,
            pinchZoom = prefs[KEY_PINCH] ?: true,
            doubleTap = prefs[KEY_DOUBLE_TAP] ?: true,
            longPress2x = prefs[KEY_LONG_PRESS] ?: true
        )
    }

    suspend fun updateGesture(key: Preferences.Key<Boolean>, enabled: Boolean) {
        context.appDataStore.edit { prefs ->
            prefs[key] = enabled
        }
    }
}

data class GestureState(
    val volBright: Boolean = true,
    val pinchZoom: Boolean = true,
    val doubleTap: Boolean = true,
    val longPress2x: Boolean = true
)
""",

    "app/src/main/java/com/stream/blueplayer/MainActivity.kt": """package com.stream.blueplayer

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.stream.blueplayer.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var dataStore: DataStoreManager

    private val userAgents = mapOf(
        "Custom" to "",
        "Default Android" to "Mozilla/5.0 (Linux; Android 10; Redmi 9 Prime) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
        "Chrome Windows" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "ExoPlayer" to "ExoPlayerLib/2.19.1 (Linux; Android 10) ExoPlayerLib/1.3.0"
    )

    private val drmSchemes = listOf("widevine", "clearkey", "playready", "none")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dataStore = DataStoreManager(this)

        ViewCompat.setOnApplyWindowInsetsListener(binding.mainRoot) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        setupDropdowns()

        binding.btnHistory.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        binding.fabPlay.setOnClickListener {
            val url = binding.etStreamUrl.text.toString().trim()
            if (url.isEmpty()) {
                Toast.makeText(this, "Paste a Media Stream URL first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val selectedUaKey = binding.actvUserAgent.text.toString()
            val finalUa = if (selectedUaKey == "Custom") {
                binding.etCustomUa.text.toString().trim().ifEmpty { userAgents["Default Android"]!! }
            } else {
                userAgents[selectedUaKey] ?: userAgents["Default Android"]!!
            }

            val config = StreamConfig(
                streamUrl = url,
                cookie = binding.etCookie.text.toString().trim(),
                referer = binding.etReferer.text.toString().trim(),
                origin = binding.etOrigin.text.toString().trim(),
                drmLicenseUrl = binding.etDrmUrl.text.toString().trim(),
                userAgent = finalUa,
                drmScheme = binding.actvDrmScheme.text.toString().lowercase()
            )

            lifecycleScope.launch {
                dataStore.saveStream(config)
            }

            startActivity(Intent(this, PlayerActivity::class.java).apply {
                putExtra("STREAM_CONFIG", config)
            })
        }
    }

    private fun setupDropdowns() {
        val uaKeys = userAgents.keys.toList()
        val uaAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, uaKeys)
        binding.actvUserAgent.setAdapter(uaAdapter)
        binding.actvUserAgent.setOnItemClickListener { _, _, pos, _ ->
            binding.tilCustomUa.visibility = if (uaKeys[pos] == "Custom") View.VISIBLE else View.GONE
        }

        val drmAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, drmSchemes)
        binding.actvDrmScheme.setAdapter(drmAdapter)
    }
}
""",

    "app/src/main/java/com/stream/blueplayer/HistoryActivity.kt": """package com.stream.blueplayer

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.stream.blueplayer.databinding.ActivityHistoryBinding
import com.stream.blueplayer.databinding.ItemHistoryBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private lateinit var dataStore: DataStoreManager
    private val adapter = HistoryDiffAdapter()
    private var isAscending = false
    private var cachedList = listOf<StreamConfig>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dataStore = DataStoreManager(this)

        ViewCompat.setOnApplyWindowInsetsListener(binding.historyRoot) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        binding.rvHistory.layoutManager = LinearLayoutManager(this)
        binding.rvHistory.setHasFixedSize(true)
        binding.rvHistory.adapter = adapter

        lifecycleScope.launch {
            dataStore.historyFlow.collectLatest { list ->
                cachedList = list
                adapter.submitList(if (isAscending) list.reversed() else list)
            }
        }

        binding.btnBack.setOnClickListener { finish() }

        binding.btnSort.setOnClickListener {
            isAscending = !isAscending
            adapter.submitList(if (isAscending) cachedList.reversed() else cachedList)
        }

        binding.btnClearAll.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Delete All History")
                .setMessage("Clear all saved links?")
                .setPositiveButton("Clear") { _, _ ->
                    lifecycleScope.launch { dataStore.clearHistory() }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    inner class HistoryDiffAdapter : ListAdapter<StreamConfig, HistoryDiffAdapter.VH>(DiffCallback) {
        inner class VH(val itemBinding: ItemHistoryBinding) : RecyclerView.ViewHolder(itemBinding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            return VH(ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = getItem(position)
            holder.itemBinding.tvHistoryUrl.text = item.streamUrl
            holder.itemBinding.root.setOnClickListener {
                startActivity(Intent(this@HistoryActivity, PlayerActivity::class.java).apply {
                    putExtra("STREAM_CONFIG", item)
                })
            }
            holder.itemBinding.btnDeleteItem.setOnClickListener {
                lifecycleScope.launch { dataStore.deleteStream(item.streamUrl) }
            }
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<StreamConfig>() {
        override fun areItemsTheSame(oldItem: StreamConfig, newItem: StreamConfig) =
            oldItem.streamUrl == newItem.streamUrl

        override fun areContentsTheSame(oldItem: StreamConfig, newItem: StreamConfig) =
            oldItem == newItem
    }
}
""",

    "app/src/main/java/com/stream/blueplayer/PlayerActivity.kt": """package com.stream.blueplayer

import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Rational
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.WindowManager
import android.widget.SeekBar
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.TrackSelectionDialogBuilder
import com.stream.blueplayer.databinding.ActivityPlayerBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(UnstableApi::class)
class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private lateinit var dataStore: DataStoreManager
    private var gestureState = GestureState()
    private var player: ExoPlayer? = null
    private lateinit var audioManager: AudioManager

    private var isScreenLocked = false
    private var isControlsVisible = true
    private var isLongPressSpeedActive = false
    private var savedSpeed = 1.0f
    private var currentResizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT

    private val handler = Handler(Looper.getMainLooper())
    private val updateProgressAction = object : Runnable {
        override fun run() {
            updateSeekBar()
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        hideSystemBars()

        dataStore = DataStoreManager(this)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        lifecycleScope.launch {
            dataStore.gestureSettingsFlow.collectLatest { state ->
                gestureState = state
            }
        }

        val config = intent.getSerializableExtra("STREAM_CONFIG") as? StreamConfig
        if (config == null) {
            finish()
            return
        }

        binding.tvStreamTitle.text = Uri.parse(config.streamUrl).lastPathSegment ?: "Network Stream"

        initializePlayer(config)
        setupControls()
        setupTouchGestures()
    }

    private fun hideSystemBars() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun initializePlayer(config: StreamConfig) {
        // Redmi 9 Prime (Android 10 / Helio G80) Hardware + Software Fallback Decoding
        val renderersFactory = DefaultRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            .setEnableDecoderFallback(true)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15000, 50000, 1200, 2500)
            .build()

        val headers = mutableMapOf<String, String>()
        if (config.cookie.isNotEmpty()) headers["Cookie"] = config.cookie
        if (config.referer.isNotEmpty()) headers["Referer"] = config.referer
        if (config.origin.isNotEmpty()) headers["Origin"] = config.origin

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(config.userAgent)
            .setDefaultRequestProperties(headers)
            .setAllowCrossProtocolRedirects(true)

        player = ExoPlayer.Builder(this, renderersFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpDataSourceFactory))
            .setLoadControl(loadControl)
            .build()

        binding.playerView.player = player

        val mediaItemBuilder = MediaItem.Builder().setUri(config.streamUrl)

        if (config.drmLicenseUrl.isNotEmpty() && config.drmScheme != "none") {
            val uuid = when (config.drmScheme) {
                "clearkey" -> C.CLEARKEY_UUID
                "playready" -> C.PLAYREADY_UUID
                else -> C.WIDEVINE_UUID
            }
            mediaItemBuilder.setDrmConfiguration(
                MediaItem.DrmConfiguration.Builder(uuid)
                    .setLicenseUri(config.drmLicenseUrl)
                    .setMultiSession(true)
                    .build()
            )
        }

        player?.apply {
            setMediaItem(mediaItemBuilder.build())
            prepare()
            playWhenReady = true
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    binding.loadingIndicator.visibility =
                        if (state == Player.STATE_BUFFERING) View.VISIBLE else View.GONE
                    updatePlayPauseIcon()
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    updatePlayPauseIcon()
                }

                override fun onPlayerError(error: PlaybackException) {
                    Toast.makeText(
                        this@PlayerActivity,
                        "Playback Error: ${error.errorCodeName}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            })
        }

        handler.post(updateProgressAction)
    }

    private fun setupControls() {
        binding.btnPlayerBack.setOnClickListener { finish() }

        binding.btnPlayPause.setOnClickListener {
            player?.let {
                if (it.isPlaying) it.pause() else it.play()
                updatePlayPauseIcon()
            }
        }

        binding.btnRewind.setOnClickListener { seekBy(-10000) }
        binding.btnForward.setOnClickListener { seekBy(10000) }

        binding.btnQuality.setOnClickListener {
            val exo = player ?: return@setOnClickListener
            TrackSelectionDialogBuilder(this, "Video Quality", exo, C.TRACK_TYPE_VIDEO)
                .setAllowAdaptiveSelections(true)
                .build()
                .show()
        }

        binding.btnAudio.setOnClickListener {
            val exo = player ?: return@setOnClickListener
            TrackSelectionDialogBuilder(this, "Audio Language", exo, C.TRACK_TYPE_AUDIO)
                .build()
                .show()
        }

        binding.btnSpeed.setOnClickListener {
            val speeds = arrayOf("0.25x", "0.5x", "0.75x", "1.0x (Normal)", "1.25x", "1.5x", "2.0x", "3.0x")
            val values = floatArrayOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f)
            AlertDialog.Builder(this)
                .setTitle("Playback Speed")
                .setItems(speeds) { _, which ->
                    savedSpeed = values[which]
                    player?.setPlaybackSpeed(savedSpeed)
                    binding.btnSpeed.text = "${savedSpeed}x"
                }
                .show()
        }

        binding.btnPip.setOnClickListener { enterPipMode() }
        binding.btnGestureSettings.setOnClickListener { showGestureSettingsDialog() }

        binding.btnLock.setOnClickListener {
            isScreenLocked = true
            binding.controlsRoot.visibility = View.GONE
            binding.btnUnlockOnly.visibility = View.VISIBLE
        }

        binding.btnUnlockOnly.setOnClickListener {
            isScreenLocked = false
            binding.btnUnlockOnly.visibility = View.GONE
            binding.controlsRoot.visibility = View.VISIBLE
        }

        binding.btnMute.setOnClickListener {
            player?.let {
                if (it.volume > 0f) {
                    it.volume = 0f
                    binding.btnMute.setImageResource(android.R.drawable.ic_lock_silent_mode)
                } else {
                    it.volume = 1f
                    binding.btnMute.setImageResource(android.R.drawable.ic_lock_silent_mode_off)
                }
            }
        }

        binding.btnGoLive.setOnClickListener {
            player?.let {
                if (it.isCurrentMediaItemLive) it.seekToDefaultPosition() else it.seekTo(it.duration)
            }
        }

        binding.btnScaleMode.setOnClickListener {
            currentResizeMode = when (currentResizeMode) {
                AspectRatioFrameLayout.RESIZE_MODE_FIT -> {
                    binding.btnScaleMode.text = "FILL"
                    AspectRatioFrameLayout.RESIZE_MODE_FILL
                }
                AspectRatioFrameLayout.RESIZE_MODE_FILL -> {
                    binding.btnScaleMode.text = "ZOOM"
                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                }
                else -> {
                    binding.btnScaleMode.text = "FIT"
                    AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            }
            binding.playerView.resizeMode = currentResizeMode
        }

        binding.btnRotate.setOnClickListener {
            requestedOrientation = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
        }

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) player?.seekTo(progress.toLong())
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setupTouchGestures() {
        // 54dp Top & Bottom Exclusion Zone so status bar swipe never triggers Volume/Brightness
        val edgeGuardPx = (54 * resources.displayMetrics.density).toInt()
        var ignoreCurrentSwipe = false

        val scaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                if (isScreenLocked || !gestureState.pinchZoom) return false
                if (detector.scaleFactor > 1.07f) {
                    binding.playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    binding.btnScaleMode.text = "ZOOM"
                    showCenterIndicator("Zoom: Crop")
                } else if (detector.scaleFactor < 0.93f) {
                    binding.playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    binding.btnScaleMode.text = "FIT"
                    showCenterIndicator("Zoom: Fit")
                }
                return true
            }
        })

        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {

            override fun onDown(e: MotionEvent): Boolean {
                val screenH = binding.gestureOverlay.height
                ignoreCurrentSwipe = (e.y < edgeGuardPx) || (e.y > screenH - edgeGuardPx)
                return true
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (isScreenLocked) {
                    binding.btnUnlockOnly.visibility =
                        if (binding.btnUnlockOnly.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                    return true
                }
                isControlsVisible = !isControlsVisible
                if (isControlsVisible) {
                    binding.controlsRoot.alpha = 0f
                    binding.controlsRoot.visibility = View.VISIBLE
                    binding.controlsRoot.animate().alpha(1f).setDuration(180).start()
                } else {
                    binding.controlsRoot.animate().alpha(0f).setDuration(180).withEndAction {
                        binding.controlsRoot.visibility = View.GONE
                    }.start()
                    hideSystemBars()
                }
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (isScreenLocked || !gestureState.doubleTap) return false
                val w = binding.gestureOverlay.width
                if (e.x < w / 2) {
                    seekBy(-10000)
                    showCenterIndicator("⏪ -10s")
                } else {
                    seekBy(10000)
                    showCenterIndicator("⏩ +10s")
                }
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                if (isScreenLocked || !gestureState.longPress2x) return
                player?.let {
                    savedSpeed = it.playbackParameters.speed
                    it.setPlaybackSpeed(2.0f)
                    isLongPressSpeedActive = true
                    binding.tvGestureIndicator.text = "⏩ 2x Speed"
                    binding.tvGestureIndicator.visibility = View.VISIBLE
                }
            }

            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                if (isScreenLocked || ignoreCurrentSwipe || !gestureState.volBright || e1 == null) {
                    return false
                }
                if (abs(distanceY) > abs(distanceX)) {
                    val w = binding.gestureOverlay.width
                    val h = binding.gestureOverlay.height.toFloat()
                    val delta = distanceY / h

                    if (e1.x < w / 2) {
                        val lp = window.attributes
                        val cur = if (lp.screenBrightness < 0) 0.5f else lp.screenBrightness
                        val next = (cur + delta).coerceIn(0.02f, 1.0f)
                        lp.screenBrightness = next
                        window.attributes = lp
                        showCenterIndicator("☀️ ${(next * 100).toInt()}%")
                    } else {
                        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                        val step = if (delta > 0) 1 else -1
                        if (abs(distanceY) > 12) {
                            val nextVol = (curVol + step).coerceIn(0, maxVol)
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, nextVol, 0)
                            showCenterIndicator("🔊 ${(nextVol * 100) / maxVol}%")
                        }
                    }
                    return true
                }
                return false
            }
        })

        binding.gestureOverlay.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            gestureDetector.onTouchEvent(event)

            if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                if (isLongPressSpeedActive) {
                    player?.setPlaybackSpeed(savedSpeed)
                    isLongPressSpeedActive = false
                    binding.tvGestureIndicator.visibility = View.GONE
                }
            }
            true
        }
    }

    private fun showGestureSettingsDialog() {
        val items = arrayOf(
            "Swipe Volume & Brightness",
            "Pinch In/Out Zoom",
            "Double-Tap 10s Seek",
            "Hold for 2x Speed"
        )
        val checked = booleanArrayOf(
            gestureState.volBright,
            gestureState.pinchZoom,
            gestureState.doubleTap,
            gestureState.longPress2x
        )

        AlertDialog.Builder(this)
            .setTitle("Gesture Controls")
            .setMultiChoiceItems(items, checked) { _, which, isChecked ->
                lifecycleScope.launch {
                    when (which) {
                        0 -> dataStore.updateGesture(DataStoreManager.KEY_VOL_BRIGHT, isChecked)
                        1 -> dataStore.updateGesture(DataStoreManager.KEY_PINCH, isChecked)
                        2 -> dataStore.updateGesture(DataStoreManager.KEY_DOUBLE_TAP, isChecked)
                        3 -> dataStore.updateGesture(DataStoreManager.KEY_LONG_PRESS, isChecked)
                    }
                }
            }
            .setPositiveButton("Done", null)
            .show()
    }

    private fun showCenterIndicator(text: String) {
        binding.tvGestureIndicator.text = text
        binding.tvGestureIndicator.visibility = View.VISIBLE
        handler.removeCallbacksAndMessages("HIDE_IND")
        handler.postAtTime({
            if (!isLongPressSpeedActive) {
                binding.tvGestureIndicator.visibility = View.GONE
            }
        }, "HIDE_IND", android.os.SystemClock.uptimeMillis() + 800)
    }

    private fun seekBy(ms: Long) {
        player?.let {
            it.seekTo((it.currentPosition + ms).coerceAtLeast(0L))
        }
    }

    private fun updatePlayPauseIcon() {
        val playing = player?.isPlaying == true
        binding.btnPlayPause.setImageResource(
            if (playing) R.drawable.ic_pause else R.drawable.ic_play_arrow
        )
    }

    private fun updateSeekBar() {
        val exo = player ?: return
        val pos = exo.currentPosition.coerceAtLeast(0L)
        val dur = exo.duration.coerceAtLeast(0L)
        if (dur > 0) {
            binding.seekBar.max = dur.toInt()
            binding.seekBar.progress = pos.toInt()
            binding.tvDuration.text = formatTime(dur)
        } else {
            binding.tvDuration.text = "LIVE"
        }
        binding.tvPosition.text = formatTime(pos)
    }

    private fun formatTime(ms: Long): String {
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) String.format("%02d:%02d:%02d", h, m, s)
        else String.format("%02d:%02d", m, s)
    }

    private fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        binding.controlsRoot.visibility = if (isInPictureInPictureMode) View.GONE else View.VISIBLE
    }

    override fun onStop() {
        super.onStop()
        if (!isInPictureInPictureMode) player?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateProgressAction)
        player?.release()
        player = null
    }
}
"""
}

def main():
    for path, content in files.items():
        os.makedirs(os.path.dirname(path) if os.path.dirname(path) else ".", exist_ok=True)
        with open(path, "w", encoding="utf-8") as f:
            f.write(content)
    print("Updated project files generated!")

    with zipfile.ZipFile("BlueStreamPlayer_Source.zip", "w", zipfile.ZIP_DEFLATED) as zf:
        for path in files.keys():
            zf.write(path)
    print("Updated ZIP created: BlueStreamPlayer_Source.zip")

if __name__ == "__main__":
    main()
