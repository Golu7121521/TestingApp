package com.streamx.app.feature.home

import com.streamx.app.core.common.UiState
import com.streamx.app.domain.model.Video
import com.streamx.app.domain.repository.VideoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeRepo(private val videos: List<Video>, private val error: Throwable? = null) : VideoRepository {
        var lastCategory: String? = null
        override suspend fun getFeed(category: String): List<Video> {
            lastCategory = category
            error?.let { throw it }
            return videos
        }
    }

    private fun video(id: String) = Video(
        id = id, title = "t$id", channelName = "c", channelAvatarUrl = null,
        thumbnailUrl = null, durationSeconds = 60, viewCountText = null, uploadedText = null,
    )

    @Test
    fun emptyRepositoryProducesEmptyState() = runTest {
        val vm = HomeViewModel(FakeRepo(emptyList()))
        advanceUntilIdle()
        assertEquals(UiState.Empty, vm.uiState.value)
    }

    @Test
    fun repositoryVideosProduceSuccessState() = runTest {
        val vm = HomeViewModel(FakeRepo(listOf(video("1"), video("2"))))
        advanceUntilIdle()
        val state = vm.uiState.value
        assertTrue(state is UiState.Success)
        assertEquals(2, (state as UiState.Success).data.size)
    }

    @Test
    fun failureProducesRetryableErrorState() = runTest {
        val vm = HomeViewModel(FakeRepo(emptyList(), error = IllegalStateException("offline")))
        advanceUntilIdle()
        val state = vm.uiState.value
        assertTrue(state is UiState.Error)
        assertEquals("offline", (state as UiState.Error).message)
    }

    @Test
    fun selectingCategoryReloadsWithThatCategory() = runTest {
        val repo = FakeRepo(emptyList())
        val vm = HomeViewModel(repo)
        advanceUntilIdle()
        vm.onCategorySelected("Music")
        advanceUntilIdle()
        assertEquals("Music", repo.lastCategory)
        assertEquals("Music", vm.selectedCategory.value)
    }
}
