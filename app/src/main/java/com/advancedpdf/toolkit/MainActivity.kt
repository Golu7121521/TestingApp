package com.advancedpdf.toolkit

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {
    var pendingFocusSearch = false
    var pendingFilter: String? = null
    private lateinit var nav: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        nav = findViewById(R.id.nav)
        nav.setOnItemSelectedListener { item ->
            val f: Fragment = when (item.itemId) {
                R.id.nav_home -> HomeFragment()
                R.id.nav_files -> FilesFragment()
                R.id.nav_tools -> ToolsFragment()
                else -> SettingsFragment()
            }
            supportFragmentManager.beginTransaction().replace(R.id.container, f).commit()
            true
        }
        if (savedInstanceState == null) nav.selectedItemId = R.id.nav_home
    }

    fun openFiles(focusSearch: Boolean, filter: String?) {
        pendingFocusSearch = focusSearch
        pendingFilter = filter
        if (nav.selectedItemId == R.id.nav_files) {
            supportFragmentManager.beginTransaction().replace(R.id.container, FilesFragment()).commit()
        } else {
            nav.selectedItemId = R.id.nav_files
        }
    }

    fun openSettings() {
        nav.selectedItemId = R.id.nav_settings
    }
}
