package com.hxrsh.sawari

import androidx.lifecycle.SavedStateHandle
import com.hxrsh.sawari.domain.AppError
import com.hxrsh.sawari.domain.AppResult
import com.hxrsh.sawari.domain.usecase.GetBusySlotsUseCase
import com.hxrsh.sawari.domain.usecase.ObserveCarUseCase
import com.hxrsh.sawari.domain.usecase.ObserveSessionUseCase
import com.hxrsh.sawari.domain.usecase.RefreshCarsUseCase
import com.hxrsh.sawari.ui.MainViewModel
import com.hxrsh.sawari.ui.common.UiState
import com.hxrsh.sawari.ui.detail.CarDetailViewModel
import com.hxrsh.sawari.ui.nav.DeepLink
import com.hxrsh.sawari.ui.nav.DeepLinkParser
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DeepLinkParserTest {
    private val web = "https://${DeepLinkParser.WEB_HOST}${DeepLinkParser.WEB_PATH_PREFIX}"

    private val table: List<Pair<String?, DeepLink?>> = listOf(
        // valid custom-scheme links
        "sawari://car/12" to DeepLink.OpenCar(12),
        "sawari://car/1" to DeepLink.OpenCar(1),
        "sawari://car/12/" to DeepLink.OpenCar(12),
        "SAWARI://CAR/7" to DeepLink.OpenCar(7),
        "sawari://car/12?utm=x#frag" to DeepLink.OpenCar(12),
        "sawari://bookings" to DeepLink.OpenBookings,
        "sawari://bookings/" to DeepLink.OpenBookings,
        // valid https equivalents
        "$web/car/12" to DeepLink.OpenCar(12),
        "$web/bookings" to DeepLink.OpenBookings,
        // ours, but broken -> Invalid (never a crash)
        "sawari://car/abc" to DeepLink.Invalid("Car id must be a positive number"),
        "sawari://car/0" to DeepLink.Invalid("Car id must be a positive number"),
        "sawari://car/-3" to DeepLink.Invalid("Car id must be a positive number"),
        "sawari://car/99999999999999" to DeepLink.Invalid("Car id must be a positive number"),
        "sawari://car/1.5" to DeepLink.Invalid("Car id must be a positive number"),
        "sawari://car" to DeepLink.Invalid("Car id must be a positive number"),
        "sawari://car/1/2" to DeepLink.Invalid("Car id must be a positive number"),
        "sawari://bookings/5" to DeepLink.Invalid("Unexpected path after bookings"),
        "sawari://settings" to DeepLink.Invalid("Unknown link"),
        "$web/nope" to DeepLink.Invalid("Unknown link"),
        // not ours at all -> null (normal launch)
        null to null,
        "" to null,
        "   " to null,
        "https://example.com/app/car/12" to null,
        "https://${DeepLinkParser.WEB_HOST}/other/car/12" to null,
        "http://${DeepLinkParser.WEB_HOST}/app/car/12" to null,
        "mailto:a@b.co" to null,
        "not a uri at all" to null,
        "sawari://car/ 12" to null, // space makes it syntactically invalid
    )

    @Test fun `table of links parses to the expected target`() {
        table.forEach { (raw, expected) -> assertEquals("for input <$raw>", expected, DeepLinkParser.parse(raw)) }
    }

    @Test fun `builders round-trip through the parser`() {
        assertEquals(DeepLink.OpenCar(42), DeepLinkParser.parse(DeepLinkParser.carUri(42)))
        assertEquals(DeepLink.OpenBookings, DeepLinkParser.parse(DeepLinkParser.bookingsUri()))
    }
}

class DeepLinkFlowTest {
    @get:Rule val main = MainDispatcherRule()

    private fun mainVm(auth: FakeAuthRepository) = MainViewModel(ObserveSessionUseCase(auth), FakeAnalytics())

    @Test fun `link received while logged out is kept until it is handled after login`() = runTest {
        val auth = FakeAuthRepository()
        val vm = mainVm(auth)
        vm.onLinkReceived("sawari://car/2")
        assertEquals(DeepLink.OpenCar(2), vm.pendingLink.value)
        // user logs in: the link must still be there for MainNav to consume
        auth.login("a@b.co", "secret12")
        assertEquals(DeepLink.OpenCar(2), vm.pendingLink.value)
        vm.onLinkHandled(DeepLink.OpenCar(2))
        assertNull(vm.pendingLink.value)
    }

    @Test fun `non sawari links and blanks are ignored`() = runTest {
        val vm = mainVm(FakeAuthRepository())
        vm.onLinkReceived(null)
        vm.onLinkReceived("https://example.com/x")
        assertNull(vm.pendingLink.value)
    }

    @Test fun `newer link replaces an older unhandled one, and handling a stale link does not clear the new one`() = runTest {
        val vm = mainVm(FakeAuthRepository())
        vm.onLinkReceived("sawari://bookings")
        vm.onLinkReceived("sawari://car/3")
        vm.onLinkHandled(DeepLink.OpenBookings)
        assertEquals(DeepLink.OpenCar(3), vm.pendingLink.value)
    }

    @Test fun `invalid link is surfaced as Invalid`() = runTest {
        val vm = mainVm(FakeAuthRepository())
        vm.onLinkReceived("sawari://car/abc")
        assertTrue(vm.pendingLink.value is DeepLink.Invalid)
    }

    private fun detailVm(repo: FakeCarRepository, id: Int) = CarDetailViewModel(
        SavedStateHandle(mapOf("carId" to id)), ObserveCarUseCase(repo), GetBusySlotsUseCase(repo), RefreshCarsUseCase(repo),
    )

    private suspend fun CarDetailViewModel.settled(): UiState<*> {
        val job = state.onEach { }.launchIn(kotlinx.coroutines.CoroutineScope(main.dispatcher))
        return state.first { it.car !is UiState.Loading }.car.also { job.cancel() }
    }

    @Test fun `unknown car id ends in the not-found state, not a crash`() = runTest {
        val vm = detailVm(FakeCarRepository(), 9999)
        assertEquals(UiState.Empty, vm.settled())
    }

    @Test fun `known car id shows content`() = runTest {
        val vm = detailVm(FakeCarRepository(), 2)
        assertEquals(UiState.Content(Fixtures.nexon), vm.settled())
    }

    @Test fun `cold start on an empty cache refreshes and then finds the car`() = runTest {
        val repo = FakeCarRepository().apply { startEmpty = true }
        val vm = detailVm(repo, 3)
        assertEquals(UiState.Content(Fixtures.creta), vm.settled())
        assertEquals(1, repo.refreshCalls)
    }

    @Test fun `offline with an empty cache shows a network error rather than not-found`() = runTest {
        val repo = FakeCarRepository().apply { startEmpty = true; refreshResult = AppResult.Failure(AppError.Network()) }
        val vm = detailVm(repo, 3)
        assertTrue(vm.settled() is UiState.Error)
    }
}
