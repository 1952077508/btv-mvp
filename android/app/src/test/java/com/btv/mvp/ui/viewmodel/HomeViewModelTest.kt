package com.btv.mvp.ui.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.btv.mvp.data.PrefsManager
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var viewModel: HomeViewModel
    private lateinit var mockClient: OkHttpClient
    private lateinit var mockCall: Call

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockClient = mockk(relaxed = true)
        mockCall = mockk(relaxed = true)
        viewModel = HomeViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is Idle`() {
        assertEquals(HomeViewModel.UiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun `roomCode is initially empty`() {
        assertEquals("", viewModel.roomCode.value)
    }

    @Test
    fun `updateRoomCode uppercases and limits to 6 chars`() {
        viewModel.updateRoomCode("abc")
        assertEquals("ABC", viewModel.roomCode.value)

        viewModel.updateRoomCode("abcdefg")
        assertEquals("ABCDEF", viewModel.roomCode.value)
    }

    @Test
    fun `updateRoomCode ignores when at max length`() {
        viewModel.updateRoomCode("ABCDEF")
        viewModel.updateRoomCode("ABCDEFG")
        assertEquals("ABCDEF", viewModel.roomCode.value)
    }

    @Test
    fun `createRoom sets Loading state`() {
        viewModel.createRoom("http://test:8000")
        assertEquals(HomeViewModel.UiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun `joinRoom sets Loading state`() {
        viewModel.joinRoom("http://test:8000", "ABC123")
        assertEquals(HomeViewModel.UiState.Loading, viewModel.uiState.value)
    }
}
