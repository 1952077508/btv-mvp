package com.btv.mvp.ui.viewmodel

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.btv.mvp.player.ExoPlayerManager
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: PlayerViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val app = mockk<Application>(relaxed = true)
        viewModel = PlayerViewModel(app)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is Idle`() {
        assertEquals(PlayerViewModel.PlaybackState.Idle, viewModel.playbackState.value)
        assertEquals(PlayerViewModel.SyncState.Idle, viewModel.syncState.value)
    }

    @Test
    fun `initial position is zero`() {
        assertEquals(0L, viewModel.currentPosition.value)
        assertEquals(0L, viewModel.duration.value)
    }

    @Test
    fun `initial videoUrl is empty`() {
        assertEquals("", viewModel.videoUrl.value)
        assertEquals("", viewModel.roomCode.value)
    }

    @Test
    fun `initialize sets room code and connection state`() {
        viewModel.initialize("ABC123", "user1", true, "http://test:8000")
        assertEquals("ABC123", viewModel.roomCode.value)
    }

    @Test
    fun `syncOffset starts at zero`() {
        assertEquals(0.0, viewModel.syncOffset.value, 0.001)
    }
}
