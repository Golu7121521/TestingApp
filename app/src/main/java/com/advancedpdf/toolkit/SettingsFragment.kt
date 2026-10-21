package com.advancedpdf.toolkit

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private lateinit var box: LinearLayout

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        box = view.findViewById(R.id.settings_container)
        render()
    }

    private fun row(title: String, summary: String, onClick: () -> Unit) {
        val v = LayoutInflater.from(requireContext()).inflate(R.layout.item_setting, box, false)
        v.findViewById<TextView>(R.id.title).text = title
        v.findViewById<TextView>(R.id.summary).text = summary
        v.setOnClickListener { onClick() }
        box.addView(v)
    }

    private fun render() {
        val ctx = requireContext()
        box.removeAllViews()

        val themeNames = arrayOf("System default", "Light", "Dark")
        val themeKeys = arrayOf("system", "light", "dark")
        row("Theme", themeNames[themeKeys.indexOf(Store.themeKey(ctx)).coerceAtLeast(0)]) {
            choose("Theme", themeNames, themeKeys.indexOf(Store.themeKey(ctx)).coerceAtLeast(0)) { i ->
                Store.setTheme(ctx, themeKeys[i])
            }
        }

        val sortNames = arrayOf("Name", "Date modified", "Size", "Last opened")
        val sortKeys = arrayOf("name", "date", "size", "opened")
        row("Default sorting", sortNames[sortKeys.indexOf(Store.sort(ctx)).coerceAtLeast(0)]) {
            choose("Default sorting", sortNames, sortKeys.indexOf(Store.sort(ctx)).coerceAtLeast(0)) { i ->
                Store.setSort(ctx, sortKeys[i]); render()
            }
        }

        val compNames = arrayOf("Low (best quality)", "Medium", "High (smallest size)")
        row("Default compression level", compNames[Store.compression(ctx)]) {
            choose("Default compression level", compNames, Store.compression(ctx)) { i ->
                Store.setCompression(ctx, i); render()
            }
        }

        row("Export location", "Files you create are saved in this app's folder. Use “Save a copy” on a result to put one anywhere else.") {}

        row("Clear recent files history", "${Store.recents(ctx).size} files in history") {
            confirm("Clear recent files?", "The list will be cleared. Your files are not deleted.") {
                Store.clearRecents(ctx); render()
            }
        }

        row("Clear temporary files", "Frees space used by processing caches") {
            val freed = FileUtil.cleanTemp(ctx)
            ctx.toast("Freed " + FileUtil.formatSize(freed))
        }

        row("Clear starred files", "${Store.starred(ctx).size} starred files") {
            confirm("Clear starred files?", "All files will be removed from Starred. Your files are not deleted.") {
                Store.clearStarred(ctx); render()
            }
        }

        row("Permissions", "This app does not request all-files access. Tap to open Android app settings.") {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.fromParts("package", ctx.packageName, null))
            )
        }

        row("Privacy", "Everything stays on your device") {
            MaterialAlertDialogBuilder(requireActivity()).setTitle("Privacy")
                .setMessage(
                    "• All PDF processing happens locally on your device.\n" +
                            "• No documents are uploaded and no account is needed.\n" +
                            "• No analytics or tracking are included.\n" +
                            "• The app does not use the internet for any feature.\n" +
                            "• Passwords and document contents are never written to logs."
                )
                .setPositiveButton("OK", null).show()
        }

        val version = try {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
        } catch (_: Exception) {
            "unknown"
        }
        row("About", "PDF Toolkit $version") {
            MaterialAlertDialogBuilder(requireActivity()).setTitle("PDF Toolkit")
                .setMessage(
                    "Version $version\n\nA lightweight, offline PDF utility.\n" +
                            "PDF merging and splitting use Apache PDFBox (Android port). " +
                            "Page rendering uses the Android system PDF renderer."
                )
                .setPositiveButton("OK", null).show()
        }

        row("Reset preferences", "Restore all settings, history and favorites to defaults") {
            confirm("Reset preferences?", "Settings, recent files, starred files and reading positions will be reset. Your files are not deleted.") {
                Store.resetAll(ctx); render()
            }
        }
    }

    private fun choose(title: String, names: Array<String>, current: Int, onPick: (Int) -> Unit) {
        MaterialAlertDialogBuilder(requireActivity()).setTitle(title)
            .setSingleChoiceItems(names, current) { d, which ->
                onPick(which)
                d.dismiss()
                if (isAdded) render()
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun confirm(title: String, msg: String, action: () -> Unit) {
        MaterialAlertDialogBuilder(requireActivity()).setTitle(title).setMessage(msg)
            .setPositiveButton("Confirm") { _, _ -> action() }
            .setNegativeButton("Cancel", null).show()
    }
}
