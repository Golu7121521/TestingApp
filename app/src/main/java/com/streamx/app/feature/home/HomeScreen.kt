package com.streamx.app.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.streamx.app.core.common.UiState
import com.streamx.app.core.designsystem.StreamXRed
import com.streamx.app.feature.components.EmptyState
import com.streamx.app.feature.components.ErrorState
import com.streamx.app.feature.components.VideoCard
import com.streamx.app.feature.components.VideoCardSkeleton

/** Category chips shown under the app bar (spec section 3). */
val HomeCategories = listOf(
    "All", "Music", "Gaming", "Live", "News",
    "Movies", "Education", "Technology", "Sports", "Podcasts",
)

@Composable
fun HomeScreen(viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        HomeTopBar()
        CategoryRow(selected = selectedCategory, onSelect = viewModel::onCategorySelected)

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            when (val s = state) {
                UiState.Loading -> LazyColumn {
                    items(4) { VideoCardSkeleton() }
                }
                UiState.Empty -> EmptyState(
                    title = "Nothing to show yet",
                    message = "Your feed will appear here once content is available.",
                )
                is UiState.Error -> ErrorState(
                    message = s.message,
                    onRetry = viewModel::refresh,
                )
                is UiState.Success -> LazyColumn {
                    items(s.data, key = { it.id }) { video ->
                        VideoCard(video = video, onClick = { /* Phase 3: open player */ })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopBar() {
    TopAppBar(
        title = {
            Text(
                text = "StreamX",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = StreamXRed,
            )
        },
        actions = {
            IconButton(onClick = { /* Phase 2: search screen */ }) {
                Icon(Icons.Filled.Search, contentDescription = "Search")
            }
            // Cast is shown only when a cast route exists (Phase 6); hidden for now.
            IconButton(onClick = { /* Phase 5: notifications */ }) {
                Icon(Icons.Filled.Notifications, contentDescription = "Notifications")
            }
            // Profile avatar placeholder; replaced by the real account/avatar in Phase 5.
            IconButton(onClick = { /* Phase 5: profile */ }) {
                Icon(Icons.Filled.AccountCircle, contentDescription = "Profile")
            }
        },
    )
}

@Composable
private fun CategoryRow(selected: String, onSelect: (String) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        items(HomeCategories) { category ->
            FilterChip(
                selected = category == selected,
                onClick = { onSelect(category) },
                label = { Text(category) },
            )
        }
    }
}
