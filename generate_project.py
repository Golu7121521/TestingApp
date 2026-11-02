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

    "gradle.properties": """org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
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
        versionCode 1
        versionName "1.0"
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
    implementation 'com.google.code.gson:gson:2.10.1'

    // Best Video Player Library: AndroidX Media3 (ExoPlayer) - Supports all formats
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
        android:label="Network Stream Player"
        android:supportsRtl="true"
        android:usesCleartextTraffic="true"
        android:theme="@style/Theme.BlueStreamPlayer"
        tools:targetApi="31">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:windowSoftInputMode="adjustResize">
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
    <color name="bg_dark">#0F0F12</color>
    <color name="surface_dark">#18181D</color>
    <color name="blue_accent">#2196F3</color>
    <color name="blue_dark">#1976D2</color>
    <color name="text_primary">#EBEBF0</color>
    <color name="text_secondary">#9E9EA8</color>
    <color name="border_unfocused">#3A3A44</color>
    <color name="player_overlay">#80000000</color>
</resources>
""",

    "app/src/main/res/values/themes.xml": """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.BlueStreamPlayer" parent="Theme.MaterialComponents.DayNight.NoActionBar">
        <item name="colorPrimary">@color/blue_accent</item>
        <item name="colorPrimaryVariant">@color/blue_dark</item>
        <item name="colorOnPrimary">@android:color/white</item>
        <item name="colorSecondary">@color/blue_accent</item>
        <item name="android:windowBackground">@color/bg_dark</item>
        <item name="android:statusBarColor">@android:color/transparent</item>
        <item name="android:navigationBarColor">@android:color/transparent</item>
    </style>

    <style name="CustomOutlineBox" parent="Widget.MaterialComponents.TextInputLayout.OutlinedBox">
        <item name="boxStrokeColor">@color/box_stroke_selector</item>
        <item name="boxStrokeWidth">1.5dp</item>
        <item name="hintTextColor">@color/blue_accent</item>
        <item name="android:textColorHint">@color/text_secondary</item>
        <item name="boxBackgroundColor">@color/surface_dark</item>
    </style>

    <style name="CustomDropdownBox" parent="Widget.MaterialComponents.TextInputLayout.OutlinedBox.ExposedDropdownMenu">
        <item name="boxStrokeColor">@color/box_stroke_selector</item>
        <item name="boxStrokeWidth">1.5dp</item>
        <item name="hintTextColor">@color/blue_accent</item>
        <item name="android:textColorHint">@color/text_secondary</item>
        <item name="boxBackgroundColor">@color/surface_dark</item>
        <item name="endIconTint">@color/text_secondary</item>
    </style>
</resources>
""",

    "app/src/main/res/color/box_stroke_selector.xml": """<?xml version="1.0" encoding="utf-8"?>
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:color="@color/blue_accent" android:state_focused="true"/>
    <item android:color="@color/blue_accent" android:state_hovered="true"/>
    <item android:color="@color/border_unfocused"/>
</selector>
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

        <!-- Top Bar -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="64dp"
            android:gravity="center_vertical"
            android:orientation="horizontal">

            <TextView
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:text="Network Stream (Video) Player"
                android:textColor="@color/text_primary"
                android:textSize="20sp"
                android:textStyle="normal" />

            <ImageButton
                android:id="@+id/btnHistory"
                android:layout_width="44dp"
                android:layout_height="44dp"
                android:background="?attr/selectableItemBackgroundBorderless"
                android:contentDescription="History"
                android:src="@android:drawable/ic_menu_recent_history"
                app:tint="@color/blue_accent" />
        </LinearLayout>

        <ScrollView
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:scrollbars="none">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:paddingTop="8dp"
                android:paddingBottom="100dp">

                <com.google.android.material.textfield.TextInputLayout
                    style="@style/CustomOutlineBox"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginBottom="16dp"
                    android:hint="Media Stream URL">
                    <com.google.android.material.textfield.TextInputEditText
                        android:id="@+id/etStreamUrl"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:inputType="textUri"
                        android:textColor="@color/text_primary" />
                </com.google.android.material.textfield.TextInputLayout>

                <com.google.android.material.textfield.TextInputLayout
                    style="@style/CustomOutlineBox"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginBottom="16dp"
                    android:hint="Cookie Value">
                    <com.google.android.material.textfield.TextInputEditText
                        android:id="@+id/etCookie"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:inputType="text"
                        android:textColor="@color/text_primary" />
                </com.google.android.material.textfield.TextInputLayout>

                <com.google.android.material.textfield.TextInputLayout
                    style="@style/CustomOutlineBox"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginBottom="16dp"
                    android:hint="Referer Value">
                    <com.google.android.material.textfield.TextInputEditText
                        android:id="@+id/etReferer"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:inputType="textUri"
                        android:textColor="@color/text_primary" />
                </com.google.android.material.textfield.TextInputLayout>

                <com.google.android.material.textfield.TextInputLayout
                    style="@style/CustomOutlineBox"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginBottom="16dp"
                    android:hint="Origin Value">
                    <com.google.android.material.textfield.TextInputEditText
                        android:id="@+id/etOrigin"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:inputType="textUri"
                        android:textColor="@color/text_primary" />
                </com.google.android.material.textfield.TextInputLayout>

                <com.google.android.material.textfield.TextInputLayout
                    style="@style/CustomOutlineBox"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginBottom="16dp"
                    android:hint="DRM License URL">
                    <com.google.android.material.textfield.TextInputEditText
                        android:id="@+id/etDrmUrl"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:inputType="textUri"
                        android:textColor="@color/text_primary" />
                </com.google.android.material.textfield.TextInputLayout>

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="horizontal"
                    android:baselineAligned="false">

                    <com.google.android.material.textfield.TextInputLayout
                        style="@style/CustomDropdownBox"
                        android:layout_width="0dp"
                        android:layout_height="wrap_content"
                        android:layout_weight="1"
                        android:layout_marginEnd="8dp"
                        android:hint="UserAgent">
                        <AutoCompleteTextView
                            android:id="@+id/actvUserAgent"
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:inputType="none"
                            android:text="Default"
                            android:textColor="@color/text_primary" />
                    </com.google.android.material.textfield.TextInputLayout>

                    <com.google.android.material.textfield.TextInputLayout
                        style="@style/CustomDropdownBox"
                        android:layout_width="0dp"
                        android:layout_height="wrap_content"
                        android:layout_weight="1"
                        android:layout_marginStart="8dp"
                        android:hint="DrmScheme">
                        <AutoCompleteTextView
                            android:id="@+id/actvDrmScheme"
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:inputType="none"
                            android:text="widevine"
                            android:textColor="@color/text_primary" />
                    </com.google.android.material.textfield.TextInputLayout>
                </LinearLayout>

                <com.google.android.material.textfield.TextInputLayout
                    android:id="@+id/tilCustomUa"
                    style="@style/CustomOutlineBox"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="16dp"
                    android:visibility="gone"
                    android:hint="Custom User-Agent String">
                    <com.google.android.material.textfield.TextInputEditText
                        android:id="@+id/etCustomUa"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:inputType="text"
                        android:textColor="@color/text_primary" />
                </com.google.android.material.textfield.TextInputLayout>

            </LinearLayout>
        </ScrollView>
    </LinearLayout>

    <com.google.android.material.floatingactionbutton.FloatingActionButton
        android:id="@+id/fabPlay"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="bottom|end"
        android:layout_margin="24dp"
        android:contentDescription="Play Stream"
        android:src="@android:drawable/ic_media_play"
        app:backgroundTint="@color/blue_accent"
        app:tint="@android:color/white"
        app:shapeAppearanceOverlay="@style/ShapeAppearance.MaterialComponents.MediumComponent" />

</androidx.coordinatorlayout.widget.CoordinatorLayout>
""",

    "app/src/main/res/layout/activity_history.xml": """<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/historyRoot"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/bg_dark"
    android:orientation="vertical">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="64dp"
        android:gravity="center_vertical"
        android:orientation="horizontal"
        android:paddingHorizontal="12dp">

        <ImageButton
            android:id="@+id/btnBack"
            android:layout_width="44dp"
            android:layout_height="44dp"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:src="@android:drawable/ic_menu_revert"
            app:tint="@color/text_primary" />

        <TextView
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_marginStart="12dp"
            android:layout_weight="1"
            android:text="History"
            android:textColor="@color/text_primary"
            android:textSize="20sp" />

        <ImageButton
            android:id="@+id/btnSort"
            android:layout_width="44dp"
            android:layout_height="44dp"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:src="@android:drawable/ic_menu_sort_by_size"
            app:tint="@color/blue_accent" />

        <ImageButton
            android:id="@+id/btnClearAll"
            android:layout_width="44dp"
            android:layout_height="44dp"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:src="@android:drawable/ic_menu_delete"
            app:tint="@color/blue_accent" />
    </LinearLayout>

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/rvHistory"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:padding="12dp"
        android:clipToPadding="false" />
</LinearLayout>
""",

    "app/src/main/res/layout/item_history.xml": """<?xml version="1.0" encoding="utf-8"?>
<com.google.android.material.card.MaterialCardView xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginBottom="12dp"
    app:cardBackgroundColor="@color/surface_dark"
    app:cardCornerRadius="10dp"
    app:strokeColor="@color/blue_accent"
    app:strokeWidth="1dp">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:gravity="center_vertical"
        android:orientation="horizontal"
        android:padding="14dp">

        <TextView
            android:id="@+id/tvHistoryUrl"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:ellipsize="end"
            android:maxLines="2"
            android:textColor="@color/text_primary"
            android:textSize="14sp" />

        <ImageButton
            android:id="@+id/btnDeleteItem"
            android:layout_width="38dp"
            android:layout_height="38dp"
            android:layout_marginStart="8dp"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:src="@android:drawable/ic_menu_delete"
            app:tint="@color/text_secondary" />
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

    <!-- Gesture Overlay Layer -->
    <View
        android:id="@+id/gestureOverlay"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />

    <!-- Center Feedback Indicator (Volume / Brightness / Seek / 2x Speed) -->
    <TextView
        android:id="@+id/tvGestureIndicator"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="center"
        android:background="#B3000000"
        android:paddingHorizontal="20dp"
        android:paddingVertical="10dp"
        android:textColor="@android:color/white"
        android:textSize="18sp"
        android:textStyle="bold"
        android:visibility="gone" />

    <!-- Loading ProgressBar -->
    <ProgressBar
        android:id="@+id/loadingIndicator"
        android:layout_width="52dp"
        android:layout_height="52dp"
        android:layout_gravity="center"
        android:indeterminateTint="@color/blue_accent"
        android:visibility="gone" />

    <!-- Custom Controls Overlay -->
    <FrameLayout
        android:id="@+id/controlsRoot"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:background="@color/player_overlay">

        <!-- Top Controls Bar -->
        <LinearLayout
            android:id="@+id/topControls"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_gravity="top"
            android:gravity="center_vertical"
            android:orientation="horizontal"
            android:paddingHorizontal="16dp"
            android:paddingTop="20dp"
            android:paddingBottom="12dp">

            <ImageButton
                android:id="@+id/btnPlayerBack"
                android:layout_width="40dp"
                android:layout_height="40dp"
                android:background="?attr/selectableItemBackgroundBorderless"
                android:src="@android:drawable/ic_menu_revert"
                app:tint="@android:color/white" />

            <TextView
                android:id="@+id/tvStreamTitle"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_marginHorizontal="12dp"
                android:layout_weight="1"
                android:ellipsize="end"
                android:singleLine="true"
                android:text="Live Stream"
                android:textColor="@android:color/white"
                android:textSize="15sp"
                android:textStyle="bold" />

            <TextView
                android:id="@+id/btnQuality"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:background="?attr/selectableItemBackground"
                android:padding="8dp"
                android:text="HD / Auto"
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

        <!-- Center Play/Pause & Seek Buttons -->
        <LinearLayout
            android:id="@+id/centerControls"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_gravity="center"
            android:gravity="center"
            android:orientation="horizontal">

            <ImageButton
                android:id="@+id/btnRewind"
                android:layout_width="56dp"
                android:layout_height="56dp"
                android:background="?attr/selectableItemBackgroundBorderless"
                android:src="@android:drawable/ic_media_rew"
                app:tint="@android:color/white" />

            <ImageButton
                android:id="@+id/btnPlayPause"
                android:layout_width="72dp"
                android:layout_height="72dp"
                android:layout_marginHorizontal="36dp"
                android:background="@drawable/circle_blue_bg"
                android:src="@android:drawable/ic_media_pause"
                app:tint="@android:color/white" />

            <ImageButton
                android:id="@+id/btnForward"
                android:layout_width="56dp"
                android:layout_height="56dp"
                android:background="?attr/selectableItemBackgroundBorderless"
                android:src="@android:drawable/ic_media_ff"
                app:tint="@android:color/white" />
        </LinearLayout>

        <!-- Bottom Controls Bar -->
        <LinearLayout
            android:id="@+id/bottomControls"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_gravity="bottom"
            android:orientation="vertical"
            android:paddingHorizontal="16dp"
            android:paddingBottom="20dp">

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
                android:gravity="center_vertical"
                android:orientation="horizontal"
                android:layout_marginTop="4dp">

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
                    android:background="@drawable/circle_blue_bg"
                    android:paddingHorizontal="12dp"
                    android:paddingVertical="4dp"
                    android:text="● GO LIVE"
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

    <!-- Unlock Button (Only visible when screen is locked) -->
    <ImageButton
        android:id="@+id/btnUnlockOnly"
        android:layout_width="52dp"
        android:layout_height="52dp"
        android:layout_gravity="center_vertical|start"
        android:layout_marginStart="24dp"
        android:background="@drawable/circle_blue_bg"
        android:src="@android:drawable/ic_lock_lock"
        android:visibility="gone"
        app:tint="@android:color/white" />

</FrameLayout>
""",

    "app/src/main/res/drawable/circle_blue_bg.xml": """<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="@color/blue_accent" />
    <corners android:radius="24dp" />
</shape>
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

    "app/src/main/java/com/stream/blueplayer/PrefsManager.kt": """package com.stream.blueplayer

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class PrefsManager(context: Context) {
    private val prefs = context.getSharedPreferences("blue_player_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun saveStreamToHistory(config: StreamConfig) {
        val list = getHistory().toMutableList()
        list.removeAll { it.streamUrl.equals(config.streamUrl, ignoreCase = true) }
        list.add(0, config)
        prefs.edit().putString("history_list", gson.toJson(list)).apply()
    }

    fun getHistory(): List<StreamConfig> {
        val json = prefs.getString("history_list", null) ?: return emptyList()
        val type = object : TypeToken<List<StreamConfig>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    fun deleteHistoryItem(url: String) {
        val list = getHistory().toMutableList()
        list.removeAll { it.streamUrl == url }
        prefs.edit().putString("history_list", gson.toJson(list)).apply()
    }

    fun clearAllHistory() {
        prefs.edit().remove("history_list").apply()
    }

    // Gesture Settings
    var isSwipeVolumeBrightnessEnabled: Boolean
        get() = prefs.getBoolean("gesture_vol_bright", true)
        set(value) = prefs.edit().putBoolean("gesture_vol_bright", value).apply()

    var isPinchZoomEnabled: Boolean
        get() = prefs.getBoolean("gesture_pinch", true)
        set(value) = prefs.edit().putBoolean("gesture_pinch", value).apply()

    var isDoubleTapSeekEnabled: Boolean
        get() = prefs.getBoolean("gesture_double_tap", true)
        set(value) = prefs.edit().putBoolean("gesture_double_tap", value).apply()

    var isLongPress2xEnabled: Boolean
        get() = prefs.getBoolean("gesture_long_press_2x", true)
        set(value) = prefs.edit().putBoolean("gesture_long_press_2x", value).apply()
}
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
import com.stream.blueplayer.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: PrefsManager

    private val userAgents = mapOf(
        "Default" to "Mozilla/5.0 (Linux; Android 10; Redmi 9 Prime) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
        "Chrome PC" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "ExoPlayer" to "ExoPlayerLib/2.19.1 (Linux; Android 10) ExoPlayerLib/1.3.0",
        "Custom" to ""
    )

    private val drmSchemes = listOf("widevine", "clearkey", "playready", "none")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Full Screen Edge-to-Edge Support
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PrefsManager(this)

        ViewCompat.setOnApplyWindowInsetsListener(binding.mainRoot) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupDropdowns()

        binding.btnHistory.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        binding.fabPlay.setOnClickListener {
            val url = binding.etStreamUrl.text.toString().trim()
            if (url.isEmpty()) {
                Toast.makeText(this, "Please paste a valid Media Stream URL", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val selectedUaKey = binding.actvUserAgent.text.toString()
            val finalUa = if (selectedUaKey == "Custom") {
                binding.etCustomUa.text.toString().trim().ifEmpty { userAgents["Default"]!! }
            } else {
                userAgents[selectedUaKey] ?: userAgents["Default"]!!
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

            prefs.saveStreamToHistory(config)

            val intent = Intent(this, PlayerActivity::class.java).apply {
                putExtra("STREAM_CONFIG", config)
            }
            startActivity(intent)
        }
    }

    private fun setupDropdowns() {
        val uaAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, userAgents.keys.toList())
        binding.actvUserAgent.setAdapter(uaAdapter)
        binding.actvUserAgent.setOnItemClickListener { _, _, position, _ ->
            val selected = userAgents.keys.toList()[position]
            binding.tilCustomUa.visibility = if (selected == "Custom") View.VISIBLE else View.GONE
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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.stream.blueplayer.databinding.ActivityHistoryBinding
import com.stream.blueplayer.databinding.ItemHistoryBinding

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private lateinit var prefs: PrefsManager
    private var historyList = mutableListOf<StreamConfig>()
    private lateinit var adapter: HistoryAdapter
    private var isReversed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PrefsManager(this)

        ViewCompat.setOnApplyWindowInsetsListener(binding.historyRoot) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        historyList = prefs.getHistory().toMutableList()
        adapter = HistoryAdapter()
        binding.rvHistory.layoutManager = LinearLayoutManager(this)
        binding.rvHistory.adapter = adapter

        binding.btnBack.setOnClickListener { finish() }

        binding.btnSort.setOnClickListener {
            isReversed = !isReversed
            historyList.reverse()
            adapter.notifyDataSetChanged()
        }

        binding.btnClearAll.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Clear History")
                .setMessage("Delete all saved stream links?")
                .setPositiveButton("Clear") { _, _ ->
                    prefs.clearAllHistory()
                    historyList.clear()
                    adapter.notifyDataSetChanged()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    inner class HistoryAdapter : RecyclerView.Adapter<HistoryAdapter.VH>() {
        inner class VH(val itemBinding: ItemHistoryBinding) : RecyclerView.ViewHolder(itemBinding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            return VH(ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }

        override fun getItemCount(): Int = historyList.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = historyList[position]
            holder.itemBinding.tvHistoryUrl.text = item.streamUrl
            holder.itemBinding.root.setOnClickListener {
                val intent = Intent(this@HistoryActivity, PlayerActivity::class.java).apply {
                    putExtra("STREAM_CONFIG", item)
                }
                startActivity(intent)
            }
            holder.itemBinding.btnDeleteItem.setOnClickListener {
                prefs.deleteHistoryItem(item.streamUrl)
                historyList.removeAt(position)
                notifyItemRemoved(position)
                notifyItemRangeChanged(position, historyList.size)
            }
        }
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
import kotlin.math.abs

@OptIn(UnstableApi::class)
class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private lateinit var prefs: PrefsManager
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
        // Edge-to-Edge Display Cutout (Notch) Support
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        hideSystemBars()

        prefs = PrefsManager(this)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

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
        // Special Decoding Optimization for Redmi 9 Prime (MediaTek Helio G80 / Android 10)
        val renderersFactory = DefaultRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            .setEnableDecoderFallback(true)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15000, 50000, 1500, 3000)
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

        // Video Quality Selector
        binding.btnQuality.setOnClickListener {
            val exo = player ?: return@setOnClickListener
            TrackSelectionDialogBuilder(this, "Select Video Quality", exo, C.TRACK_TYPE_VIDEO)
                .setAllowAdaptiveSelections(true)
                .build()
                .show()
        }

        // Audio Language Selector
        binding.btnAudio.setOnClickListener {
            val exo = player ?: return@setOnClickListener
            TrackSelectionDialogBuilder(this, "Select Audio Track / Language", exo, C.TRACK_TYPE_AUDIO)
                .build()
                .show()
        }

        // Playback Speed Selector
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

        // PiP Button
        binding.btnPip.setOnClickListener { enterPipMode() }

        // Gesture Settings Dialog Button
        binding.btnGestureSettings.setOnClickListener { showGestureSettingsDialog() }

        // Lock / Unlock Screen
        binding.btnLock.setOnClickListener {
            isScreenLocked = true
            binding.controlsRoot.visibility = View.GONE
            binding.btnUnlockOnly.visibility = View.VISIBLE
            Toast.makeText(this, "Screen Locked", Toast.LENGTH_SHORT).show()
        }

        binding.btnUnlockOnly.setOnClickListener {
            isScreenLocked = false
            binding.btnUnlockOnly.visibility = View.GONE
            binding.controlsRoot.visibility = View.VISIBLE
            Toast.makeText(this, "Screen Unlocked", Toast.LENGTH_SHORT).show()
        }

        // Mute / Unmute Button
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

        // Go Live Button
        binding.btnGoLive.setOnClickListener {
            player?.let {
                if (it.isCurrentMediaItemLive) {
                    it.seekToDefaultPosition()
                } else {
                    it.seekTo(it.duration)
                }
                Toast.makeText(this, "Jumped to Live Edge", Toast.LENGTH_SHORT).show()
            }
        }

        // Scale Mode Button
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

        // Orientation Rotate Button
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
        val edgeGuardPx = (52 * resources.displayMetrics.density).toInt()
        var ignoreCurrentSwipe = false

        val scaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                if (isScreenLocked || !prefs.isPinchZoomEnabled) return false
                if (detector.scaleFactor > 1.08f) {
                    binding.playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    binding.btnScaleMode.text = "ZOOM"
                    showCenterIndicator("Zoom: Crop to Fill")
                } else if (detector.scaleFactor < 0.92f) {
                    binding.playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    binding.btnScaleMode.text = "FIT"
                    showCenterIndicator("Zoom: Fit Screen")
                }
                return true
            }
        })

        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {

            override fun onDown(e: MotionEvent): Boolean {
                // Top and Bottom Status/Nav Bar Exclusion Zone!
                // Upar se swipe karke time dekhne par Volume/Brightness trigger nahi hoga
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
                binding.controlsRoot.visibility = if (isControlsVisible) View.VISIBLE else View.GONE
                if (!isControlsVisible) hideSystemBars()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (isScreenLocked || !prefs.isDoubleTapSeekEnabled) return false
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
                if (isScreenLocked || !prefs.isLongPress2xEnabled) return
                player?.let {
                    savedSpeed = it.playbackParameters.speed
                    it.setPlaybackSpeed(2.0f)
                    isLongPressSpeedActive = true
                    binding.tvGestureIndicator.text = "⏩ 2x Speed Active"
                    binding.tvGestureIndicator.visibility = View.VISIBLE
                }
            }

            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                if (isScreenLocked || ignoreCurrentSwipe || !prefs.isSwipeVolumeBrightnessEnabled || e1 == null) {
                    return false
                }
                if (abs(distanceY) > abs(distanceX)) {
                    val w = binding.gestureOverlay.width
                    val h = binding.gestureOverlay.height.toFloat()
                    val delta = distanceY / h

                    if (e1.x < w / 2) {
                        // Left Side Swipe -> Brightness
                        val lp = window.attributes
                        val currentBright = if (lp.screenBrightness < 0) 0.5f else lp.screenBrightness
                        val newBright = (currentBright + delta).coerceIn(0.02f, 1.0f)
                        lp.screenBrightness = newBright
                        window.attributes = lp
                        showCenterIndicator("☀️ Brightness: ${(newBright * 100).toInt()}%")
                    } else {
                        // Right Side Swipe -> Volume
                        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                        val step = if (delta > 0) 1 else -1
                        if (abs(distanceY) > 12) {
                            val newVol = (curVol + step).coerceIn(0, maxVol)
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                            val pct = (newVol * 100) / maxVol
                            showCenterIndicator("🔊 Volume: $pct%")
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
            "Pinch In/Out to Zoom",
            "Double-Tap 10s Seek",
            "Press & Hold for 2x Speed"
        )
        val checked = booleanArrayOf(
            prefs.isSwipeVolumeBrightnessEnabled,
            prefs.isPinchZoomEnabled,
            prefs.isDoubleTapSeekEnabled,
            prefs.isLongPress2xEnabled
        )

        AlertDialog.Builder(this)
            .setTitle("Gesture Settings")
            .setMultiChoiceItems(items, checked) { _, which, isChecked ->
                when (which) {
                    0 -> prefs.isSwipeVolumeBrightnessEnabled = isChecked
                    1 -> prefs.isPinchZoomEnabled = isChecked
                    2 -> prefs.isDoubleTapSeekEnabled = isChecked
                    3 -> prefs.isLongPress2xEnabled = isChecked
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
        }, "HIDE_IND", android.os.SystemClock.uptimeMillis() + 900)
    }

    private fun seekBy(ms: Long) {
        player?.let {
            val target = (it.currentPosition + ms).coerceAtLeast(0L)
            it.seekTo(target)
        }
    }

    private fun updatePlayPauseIcon() {
        val playing = player?.isPlaying == true
        binding.btnPlayPause.setImageResource(
            if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
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
        if (!isInPictureInPictureMode) {
            player?.pause()
        }
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
    print("Project files created successfully!")

    # Pack everything into BlueStreamPlayer_Source.zip
    with zipfile.ZipFile("BlueStreamPlayer_Source.zip", "w", zipfile.ZIP_DEFLATED) as zf:
        for path in files.keys():
            zf.write(path)
    print("ZIP created: BlueStreamPlayer_Source.zip")

if __name__ == "__main__":
    main()
