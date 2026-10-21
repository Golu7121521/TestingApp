package com.advancedpdf.toolkit

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton

object UiUtil {
    /** Adds a square-ish tool tile to a 3-column GridLayout. */
    fun addTile(grid: GridLayout, label: String, icon: Int, onClick: () -> Unit) {
        val v = LayoutInflater.from(grid.context).inflate(R.layout.item_tile, grid, false)
        v.findViewById<ImageView>(R.id.tile_icon).setImageResource(icon)
        v.findViewById<TextView>(R.id.tile_label).text = label
        v.contentDescription = label
        v.setOnClickListener { onClick() }
        val lp = GridLayout.LayoutParams(
            GridLayout.spec(GridLayout.UNDEFINED, GridLayout.FILL, 1f),
            GridLayout.spec(GridLayout.UNDEFINED, GridLayout.FILL, 1f)
        )
        lp.width = 0
        lp.setMargins(4, 4, 4, 4)
        grid.addView(v, lp)
    }

    fun toolTiles(grid: GridLayout, ctx: android.content.Context) {
        addTile(grid, "Merge PDF", R.drawable.ic_merge) { ToolActivity.start(ctx, "merge") }
        addTile(grid, "Split PDF", R.drawable.ic_split) { ToolActivity.start(ctx, "split") }
        addTile(grid, "Compress PDF", R.drawable.ic_compress) { ToolActivity.start(ctx, "compress") }
        addTile(grid, "Images to PDF", R.drawable.ic_image) { ToolActivity.start(ctx, "img2pdf") }
        addTile(grid, "PDF to Images", R.drawable.ic_pdf) { ToolActivity.start(ctx, "pdf2img") }
    }
}

class HomeFragment : Fragment(R.layout.fragment_home) {

    private val openDoc = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                requireContext().contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
            }
            openReader(requireContext(), uri)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ctx = requireContext()
        val main = requireActivity() as MainActivity

        view.findViewById<MaterialButton>(R.id.btn_open).setOnClickListener {
            openDoc.launch(arrayOf("application/pdf"))
        }
        view.findViewById<MaterialButton>(R.id.btn_scan).isEnabled = false
        view.findViewById<MaterialButton>(R.id.btn_create).setOnClickListener {
            ToolActivity.start(ctx, "img2pdf")
        }
        view.findViewById<View>(R.id.btn_search).setOnClickListener { main.openFiles(true, "all") }
        view.findViewById<View>(R.id.see_all).setOnClickListener { main.openFiles(false, "all") }
        view.findViewById<ImageButton>(R.id.btn_menu).setOnClickListener { anchor ->
            val pm = PopupMenu(ctx, anchor)
            pm.menu.add("Settings")
            pm.menu.add("Clear recent files")
            pm.setOnMenuItemClickListener {
                if (it.title == "Settings") main.openSettings()
                else {
                    Store.clearRecents(ctx)
                    load()
                }
                true
            }
            pm.show()
        }
        UiUtil.toolTiles(view.findViewById(R.id.tiles), ctx)
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() {
        val app = context?.applicationContext ?: return
        Thread {
            val recent = Store.recents(app).take(5).map { FileUtil.fromEntry(app, it) }
            val starred = Store.starred(app).take(5).map { FileUtil.fromEntry(app, it) }
            activity?.runOnUiThread { if (isAdded && view != null) render(recent, starred) }
        }.start()
    }

    private fun render(recent: List<FileItem>, starred: List<FileItem>) {
        val v = requireView()
        val recentList = v.findViewById<LinearLayout>(R.id.recent_list)
        val starredList = v.findViewById<LinearLayout>(R.id.starred_list)
        recentList.removeAllViews()
        starredList.removeAllViews()
        fill(recentList, recent)
        fill(starredList, starred)
        v.findViewById<View>(R.id.starred_title).visibility = if (starred.isEmpty()) View.GONE else View.VISIBLE
        val none = recent.isEmpty() && starred.isEmpty()
        v.findViewById<View>(R.id.empty).visibility = if (none) View.VISIBLE else View.GONE
        v.findViewById<View>(R.id.recent_title).visibility = if (recent.isEmpty()) View.GONE else View.VISIBLE
        v.findViewById<View>(R.id.see_all).visibility = if (none) View.GONE else View.VISIBLE
    }

    private fun fill(parent: LinearLayout, items: List<FileItem>) {
        val inflater = LayoutInflater.from(parent.context)
        for (item in items) {
            val row = inflater.inflate(R.layout.item_file, parent as ViewGroup, false)
            FileRow.bind(
                row, item,
                onClick = { FileActions.openOrWarn(requireContext(), item) },
                onLong = { FileActions.show(requireActivity(), item) { load() } },
                onStarChanged = { load() }
            )
            parent.addView(row)
        }
    }
}
