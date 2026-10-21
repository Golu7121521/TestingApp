package com.advancedpdf.toolkit

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.ChipGroup

class FilesFragment : Fragment(R.layout.fragment_files) {

    private var filter = "recent"
    private var sort = "date"
    private var query = ""
    private var recentItems: List<FileItem> = emptyList()
    private var mineItems: List<FileItem> = emptyList()
    private var starredItems: List<FileItem> = emptyList()
    private var discovered: List<FileItem> = emptyList()
    private lateinit var adapter: Adapter
    private lateinit var empty: TextView
    private lateinit var sortBtn: MaterialButton

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

    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) scan() else requireContext().toast("Permission denied. You can still use Browse to pick files.")
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ctx = requireContext()
        val main = requireActivity() as MainActivity
        sort = Store.sort(ctx)

        empty = view.findViewById(R.id.empty)
        sortBtn = view.findViewById(R.id.btn_sort)
        val list = view.findViewById<RecyclerView>(R.id.list)
        list.layoutManager = LinearLayoutManager(ctx)
        adapter = Adapter()
        list.adapter = adapter

        val chips = view.findViewById<ChipGroup>(R.id.chips)
        main.pendingFilter?.let {
            filter = it
            chips.check(
                when (it) {
                    "all" -> R.id.chip_all
                    "starred" -> R.id.chip_starred
                    "mine" -> R.id.chip_mine
                    else -> R.id.chip_recent
                }
            )
        }
        main.pendingFilter = null
        chips.setOnCheckedStateChangeListener { _, ids ->
            filter = when (ids.firstOrNull()) {
                R.id.chip_all -> "all"
                R.id.chip_starred -> "starred"
                R.id.chip_mine -> "mine"
                else -> "recent"
            }
            refreshList()
        }

        val search = view.findViewById<EditText>(R.id.search)
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString()?.trim() ?: ""
                refreshList()
            }
        })
        if (main.pendingFocusSearch) {
            main.pendingFocusSearch = false
            search.requestFocus()
            search.post {
                (ctx.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .showSoftInput(search, InputMethodManager.SHOW_IMPLICIT)
            }
        }

        view.findViewById<View>(R.id.btn_browse).setOnClickListener { openDoc.launch(arrayOf("application/pdf")) }
        view.findViewById<View>(R.id.btn_scan).setOnClickListener { requestScan() }
        sortBtn.setOnClickListener { anchor ->
            val pm = PopupMenu(ctx, anchor)
            pm.menu.add(0, 1, 0, "Name")
            pm.menu.add(0, 2, 1, "Date modified")
            pm.menu.add(0, 3, 2, "Size")
            pm.menu.add(0, 4, 3, "Last opened")
            pm.setOnMenuItemClickListener {
                sort = when (it.itemId) { 1 -> "name"; 3 -> "size"; 4 -> "opened"; else -> "date" }
                refreshList()
                true
            }
            pm.show()
        }
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun reload() {
        val app = context?.applicationContext ?: return
        Thread {
            val recent = Store.recents(app).map { FileUtil.fromEntry(app, it) }
            val starred = Store.starred(app).map { FileUtil.fromEntry(app, it) }
            val mine = FileUtil.listMine(app)
            activity?.runOnUiThread {
                if (isAdded && view != null) {
                    recentItems = recent
                    starredItems = starred
                    mineItems = mine
                    refreshList()
                }
            }
        }.start()
    }

    private fun requestScan() {
        val ctx = requireContext()
        if (Build.VERSION.SDK_INT <= 32 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permission.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        } else {
            scan()
        }
    }

    private fun scan() {
        val app = requireContext().applicationContext
        if (Build.VERSION.SDK_INT >= 33) {
            requireContext().toast("On Android 13+, this lists PDFs the app can see. Use Browse to pick any other file.")
        }
        Thread {
            val found = FileUtil.scanMediaStore(app)
            activity?.runOnUiThread {
                if (isAdded && view != null) {
                    discovered = found
                    requireView().findViewById<ChipGroup>(R.id.chips).check(R.id.chip_all)
                    filter = "all"
                    refreshList()
                    if (found.isEmpty()) requireContext().toast("No PDFs were found. Use Browse to pick a file.")
                }
            }
        }.start()
    }

    private fun refreshList() {
        if (view == null) return
        var items: List<FileItem> = when (filter) {
            "all" -> (mineItems + recentItems + discovered).distinctBy { it.uri.toString() }
            "starred" -> starredItems
            "mine" -> mineItems
            else -> recentItems
        }
        if (query.isNotEmpty()) items = items.filter { it.name.contains(query, ignoreCase = true) }
        items = when {
            filter == "recent" -> items.sortedByDescending { it.opened }
            sort == "name" -> items.sortedBy { it.name.lowercase() }
            sort == "size" -> items.sortedByDescending { it.size }
            sort == "opened" -> items.sortedByDescending { it.opened }
            else -> items.sortedByDescending { it.modified }
        }
        sortBtn.text = "Sort: " + when (sort) {
            "name" -> "Name"; "size" -> "Size"; "opened" -> "Opened"; else -> "Date"
        }
        adapter.items = items
        adapter.notifyDataSetChanged()
        empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        empty.text = when {
            query.isNotEmpty() -> "No files match your search."
            filter == "starred" -> "No starred files yet. Tap the star on a file to add it."
            filter == "mine" -> "Files you create with the tools appear here."
            filter == "all" -> "No PDFs found yet. Use Browse or Scan device."
            else -> "No recent files. Tap Browse to open a PDF."
        }
    }

    private inner class Adapter : RecyclerView.Adapter<Adapter.VH>() {
        var items: List<FileItem> = emptyList()

        inner class VH(v: View) : RecyclerView.ViewHolder(v)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            FileRow.bind(
                holder.itemView, item,
                onClick = { FileActions.openOrWarn(requireContext(), item) },
                onLong = { FileActions.show(requireActivity(), item) { reload() } },
                onStarChanged = { reload() }
            )
        }
    }
}
