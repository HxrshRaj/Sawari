package com.hxrsh.sawari

import app.cash.turbine.test
import com.hxrsh.sawari.analytics.Events
import com.hxrsh.sawari.domain.AppError
import com.hxrsh.sawari.domain.AppResult
import com.hxrsh.sawari.domain.usecase.ObserveCarsUseCase
import com.hxrsh.sawari.domain.usecase.ObserveCitiesUseCase
import com.hxrsh.sawari.domain.usecase.RefreshCarsUseCase
import com.hxrsh.sawari.ui.cars.CarListViewModel
import com.hxrsh.sawari.ui.common.UiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CarListViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val repo = FakeCarRepository()
    private val analytics = FakeAnalytics()

    private fun vm() = CarListViewModel(ObserveCarsUseCase(repo), ObserveCitiesUseCase(repo), RefreshCarsUseCase(repo), analytics)

    @Test fun `shows cached cars and refreshes on start`() = runTest {
        val vm = vm()
        vm.state.test {
            val state = expectMostRecentItem()
            assertEquals(UiState.Content(Fixtures.allCars), state.content)
            assertEquals(listOf("Bengaluru", "Mumbai"), state.cities)
            assertNull(state.staleNotice)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, repo.refreshCalls)
    }

    @Test fun `city and fuel filters narrow the list`() = runTest {
        val vm = vm()
        vm.state.test {
            vm.onCityChange("Bengaluru")
            assertEquals(listOf(Fixtures.swift, Fixtures.nexon), (expectMostRecentItem().content as UiState.Content).data)
            vm.onFuelChange("Electric")
            assertEquals(listOf(Fixtures.nexon), (expectMostRecentItem().content as UiState.Content).data)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `search with no match gives the empty state, not an error`() = runTest {
        val vm = vm()
        vm.state.test {
            vm.onQueryChange("zzz")
            assertEquals(UiState.Empty, expectMostRecentItem().content)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `offline with a warm cache keeps the list and sets the stale notice`() = runTest {
        repo.refreshResult = networkFailure
        vm().state.test {
            val s = expectMostRecentItem()
            assertTrue(s.content is UiState.Content)
            assertEquals("You're offline. Showing saved cars.", s.staleNotice)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `offline with an empty cache shows an error state`() = runTest {
        repo.startEmpty = true
        repo.refreshResult = AppResult.Failure(AppError.Network())
        vm().state.test {
            val s = expectMostRecentItem()
            assertTrue("was ${s.content}", s.content is UiState.Error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `retry after a failed first load recovers`() = runTest {
        repo.startEmpty = true
        repo.refreshResult = AppResult.Failure(AppError.Server(503))
        val vm = vm()
        vm.state.test {
            assertTrue(expectMostRecentItem().content is UiState.Error)
            repo.refreshResult = AppResult.Success(Unit)
            vm.refresh()
            assertTrue(expectMostRecentItem().content is UiState.Content)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `search analytics fires once after the debounce`() = runTest {
        val vm = vm()
        vm.onQueryChange("n"); vm.onQueryChange("ne"); vm.onQueryChange("nex")
        main.dispatcher.scheduler.advanceTimeBy(1_000)
        assertEquals(listOf(Events.SEARCH to mapOf("term" to "nex")), analytics.events)
    }
}
