package com.advancedpdf.toolkit

import android.os.Bundle
import android.view.View
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment

class ToolsFragment : Fragment(R.layout.fragment_tools) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ctx = requireContext()
        val box = view.findViewById<LinearLayout>(R.id.tools_container)

        fun section(title: String, build: (GridLayout) -> Unit) {
            val t = TextView(ctx, null, 0, R.style.SectionTitle)
            t.text = title
            box.addView(t)
            val g = GridLayout(ctx)
            g.columnCount = 3
            build(g)
            box.addView(g)
        }

        section("Organize") {
            UiUtil.addTile(it, "Merge PDF", R.drawable.ic_merge) { ToolActivity.start(ctx, "merge") }
            UiUtil.addTile(it, "Split / Extract pages", R.drawable.ic_split) { ToolActivity.start(ctx, "split") }
        }
        section("Optimize") {
            UiUtil.addTile(it, "Compress PDF", R.drawable.ic_compress) { ToolActivity.start(ctx, "compress") }
        }
        section("Convert") {
            UiUtil.addTile(it, "Images to PDF", R.drawable.ic_image) { ToolActivity.start(ctx, "img2pdf") }
            UiUtil.addTile(it, "PDF to Images", R.drawable.ic_pdf) { ToolActivity.start(ctx, "pdf2img") }
        }

        val note = TextView(ctx)
        note.text = "Not available yet: page rotation and reordering, annotations, password protection, " +
                "watermarks, OCR, scanning and Office conversions. These are planned for later versions."
        note.setTextColor(androidx.core.content.ContextCompat.getColor(ctx, R.color.text2))
        note.textSize = 13f
        note.setPadding(0, ctx.dp(24), 0, 0)
        box.addView(note)
    }
}
