package com.example.offlineplayer

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.InputType
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.GridLayoutManager
import com.example.offlineplayer.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val videoList = mutableListOf<VideoItem>()

    private val permission =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_VIDEO
        else
            Manifest.permission.READ_EXTERNAL_STORAGE

    private val permissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) loadVideos() else showEmptyState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-edge: draw behind the status/navigation bars ourselves
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyEdgeToEdgeInsets()

        binding.recyclerVideos.layoutManager = GridLayoutManager(this, 2)

        binding.swipeRefresh.setOnRefreshListener {
            loadVideos()
            binding.swipeRefresh.isRefreshing = false
        }
        binding.swipeRefresh.setColorSchemeResources(R.color.accent)

        binding.fabPlayLink.setOnClickListener { showPlayFromLinkDialog() }

        if (ContextCompat.checkSelfPermission(this, permission)
            == PackageManager.PERMISSION_GRANTED
        ) {
            loadVideos()
        } else {
            permissionLauncher.launch(permission)
        }
    }

    /** Push the top bar below the status bar, and the list above the navigation bar. */
    private fun applyEdgeToEdgeInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.topBar) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = bars.top)
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.recyclerVideos) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(bottom = bars.bottom + view.paddingTop)
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.fabPlayLink) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val baseMargin = (24 * resources.displayMetrics.density).toInt()
            val params = view.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            params.bottomMargin = baseMargin + bars.bottom
            params.marginEnd = baseMargin + bars.right
            view.layoutParams = params
            insets
        }
    }

    /** Scans MediaStore for videos already saved on the device (fully offline, no network use). */
    private fun loadVideos() {
        videoList.clear()

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION
        )

        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI

        contentResolver.query(collection, projection, null, null, "${MediaStore.Video.Media.DATE_ADDED} DESC")
            ?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "Untitled"
                    val duration = cursor.getLong(durCol)
                    val contentUri = Uri.withAppendedPath(collection, id.toString())

                    videoList.add(VideoItem(id, name, contentUri.toString(), duration))
                }
            }

        binding.textVideoCount.text = if (videoList.isEmpty()) {
            "Offline library"
        } else {
            "${videoList.size} video${if (videoList.size == 1) "" else "s"} · Offline"
        }

        if (videoList.isEmpty()) {
            showEmptyState()
        } else {
            binding.emptyState.visibility = android.view.View.GONE
            binding.recyclerVideos.adapter = VideoAdapter(videoList) { video ->
                val intent = Intent(this, PlayerActivity::class.java)
                intent.putExtra(PlayerActivity.EXTRA_VIDEO_URI, video.uriString)
                intent.putExtra(PlayerActivity.EXTRA_VIDEO_TITLE, video.title)
                startActivity(intent)
            }
        }
    }

    private fun showEmptyState() {
        binding.emptyState.visibility = android.view.View.VISIBLE
    }

    /** Lets the person paste any direct video link - MP4, MKV, HLS (.m3u8), DASH (.mpd), RTSP - and play it. */
    private fun showPlayFromLinkDialog() {
        val input = EditText(this).apply {
            hint = "https://example.com/video.m3u8"
            inputType = InputType.TYPE_TEXT_VARIATION_URI
            setPadding(48, 32, 48, 32)
        }

        AlertDialog.Builder(this)
            .setTitle("Play from link")
            .setMessage("Paste a direct video URL (MP4, MKV, HLS, DASH, RTSP...)")
            .setView(input)
            .setPositiveButton("Play") { _, _ ->
                val url = input.text.toString().trim()
                if (url.isNotEmpty()) {
                    val intent = Intent(this, PlayerActivity::class.java)
                    intent.putExtra(PlayerActivity.EXTRA_VIDEO_URI, url)
                    intent.putExtra(PlayerActivity.EXTRA_VIDEO_TITLE, Uri.parse(url).lastPathSegment ?: "Streaming video")
                    startActivity(intent)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
