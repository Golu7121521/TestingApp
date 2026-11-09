package com.litetube.app.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.litetube.app.data.Repo
import com.litetube.app.databinding.FragmentFeedBinding
import kotlinx.coroutines.launch

class FeedFragment : Fragment() {

    private var _b: FragmentFeedBinding? = null
    private val b get() = _b!!

    private val adapter = VideoAdapter { PlayerActivity.start(requireContext(), it.url) }

    private val mode: String
        get() = requireArguments().getString(ARG_MODE, MODE_HOME)

    companion object {
        const val MODE_HOME = "home"
        const val MODE_SHORTS = "shorts"
        const val MODE_SUBS = "subs"
        const val MODE_LIBRARY = "library"
        private const val ARG_MODE = "mode"

        fun newInstance(mode: String) = FeedFragment().apply {
            arguments = Bundle().apply { putString(ARG_MODE, mode) }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _b = FragmentFeedBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        b.recycler.layoutManager = LinearLayoutManager(requireContext())
        b.recycler.adapter = adapter

        when (mode) {
            MODE_HOME -> {
                b.searchBox.visibility = View.VISIBLE
                b.searchBox.setOnEditorActionListener { _, actionId, _ ->
                    if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                        hideKeyboard()
                        load(b.searchBox.text.toString().trim().ifEmpty { "trending" })
                        true
                    } else false
                }
                load("trending")
            }
            MODE_SHORTS -> load("shorts")
            MODE_SUBS -> showMessage("Subscriptions agle step mein add honge (login / local list)")
            else -> showMessage("Library agle step mein add hogi (history / saved videos)")
        }
    }

    private fun load(query: String) {
        showMessage(null)
        b.progress.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val items = Repo.search(query)
                adapter.submit(items)
                if (items.isEmpty()) showMessage("Kuch nahi mila")
            } catch (e: Exception) {
                showMessage("Error: ${e.message}")
            } finally {
                _b?.progress?.visibility = View.GONE
            }
        }
    }

    private fun showMessage(text: String?) {
        _b?.message?.apply {
            this.text = text.orEmpty()
            visibility = if (text == null) View.GONE else View.VISIBLE
        }
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(b.searchBox.windowToken, 0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
