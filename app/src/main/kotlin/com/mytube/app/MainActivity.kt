package com.mytube.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.mytube.app.ui.AppRoot
import com.mytube.app.ui.MyTubeTheme

class MainActivity : ComponentActivity() {
    private val vm: MainVM by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyTubeTheme { AppRoot(vm) }
        }
    }
}
