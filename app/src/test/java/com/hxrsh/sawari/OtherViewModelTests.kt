package com.hxrsh.sawari

import app.cash.turbine.test
import com.hxrsh.sawari.analytics.Events
import com.hxrsh.sawari.domain.AppError
import com.hxrsh.sawari.domain.AppResult
import com.hxrsh.sawari.domain.model.BookingStatus
import com.hxrsh.sawari.domain.model.Session
import com.hxrsh.sawari.domain.repository.AuthRepository
import com.hxrsh.sawari.domain.usecase.CancelBookingUseCase
import com.hxrsh.sawari.domain.usecase.LoginUseCase
import com.hxrsh.sawari.domain.usecase.LogoutUseCase
import com.hxrsh.sawari.domain.usecase.ObserveBookingsUseCase
import com.hxrsh.sawari.domain.usecase.RefreshBookingsUseCase
import com.hxrsh.sawari.domain.usecase.RegisterUseCase
import com.hxrsh.sawari.ui.auth.AuthViewModel
import com.hxrsh.sawari.ui.bookings.MyBookingsViewModel
import com.hxrsh.sawari.ui.common.UiState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock

class FakeAuthRepository : AuthRepository {
    val sessionFlow = MutableStateFlow<Session?>(null)
    override val session: Flow<Session?> = sessionFlow
    var result: AppResult<Session> = AppResult.Success(Fixtures.session)
    val calls = mutableListOf<String>()
    override suspend fun login(email: String, password: String): AppResult<Session> {
        calls += "login:$email"; return result.also { if (it is AppResult.Success) sessionFlow.value = it.data }
    }
    override suspend fun register(name: String, email: String, password: String): AppResult<Session> {
        calls += "register:$name:$email"; return result.also { if (it is AppResult.Success) sessionFlow.value = it.data }
    }
    override suspend fun logout() { sessionFlow.value = null }
}

class AuthViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val repo = FakeAuthRepository()
    private fun vm() = AuthViewModel(LoginUseCase(repo), RegisterUseCase(repo))

    @Test fun `invalid email and short password are rejected locally`() = runTest {
        val vm = vm()
        vm.onEmailChange("nope"); vm.onPasswordChange("secret12"); vm.submit()
        assertEquals("Enter a valid email address", vm.state.value.error)
        vm.onEmailChange("a@b.co"); vm.onPasswordChange("123"); vm.submit()
        assertEquals("Password must be at least 6 characters", vm.state.value.error)
        assertTrue(repo.calls.isEmpty())
    }

    @Test fun `register requires a name and trims input`() = runTest {
        val vm = vm()
        vm.toggleMode(); vm.onEmailChange(" a@b.co "); vm.onPasswordChange("secret12"); vm.submit()
        assertEquals("Enter your name", vm.state.value.error)
        vm.onNameChange(" Asha "); vm.submit()
        assertEquals(listOf("register:Asha:a@b.co"), repo.calls)
    }

    @Test fun `wrong credentials show the server message and stop loading`() = runTest {
        repo.result = AppResult.Failure(AppError.Unauthorized("Incorrect email or password"))
        val vm = vm()
        vm.onEmailChange("a@b.co"); vm.onPasswordChange("secret12"); vm.submit()
        assertEquals("Incorrect email or password", vm.state.value.error)
        assertFalse(vm.state.value.isLoading)
    }

    @Test fun `successful login clears the error`() = runTest {
        val vm = vm()
        vm.onEmailChange("a@b.co"); vm.onPasswordChange("secret12"); vm.submit()
        assertNull(vm.state.value.error)
        assertEquals(Fixtures.session, repo.sessionFlow.value)
    }
}

class MyBookingsViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val repo = FakeBookingRepository()
    private val analytics = FakeAnalytics()
    private fun vm() = MyBookingsViewModel(
        ObserveBookingsUseCase(repo), RefreshBookingsUseCase(repo), CancelBookingUseCase(repo),
        LogoutUseCase(FakeAuthRepository()), analytics, Clock.systemUTC(),
    )

    @Test fun `empty list after a successful refresh is the empty state`() = runTest {
        vm().state.test {
            assertEquals(UiState.Empty, expectMostRecentItem().content)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `failed refresh with no cache is an error, with cache it is a banner`() = runTest {
        repo.refreshResult = networkFailure
        val vm = vm()
        vm.state.test {
            assertTrue(expectMostRecentItem().content is UiState.Error)
            repo.bookings.value = listOf(Fixtures.booking(1))
            val s = expectMostRecentItem()
            assertTrue(s.content is UiState.Content)
            assertEquals("You're offline. Showing saved bookings.", s.staleNotice)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `cancel success updates the list, logs analytics and emits a message`() = runTest {
        repo.bookings.value = listOf(Fixtures.booking(5))
        val vm = vm()
        vm.messages.test {
            vm.cancel(5)
            assertEquals("Booking cancelled", awaitItem())
        }
        assertEquals(Events.BOOKING_CANCELLED, analytics.events.single().first)
        assertEquals(BookingStatus.CANCELLED, repo.bookings.value.single().status)
    }

    @Test fun `cancel failure reports the error and does not log analytics`() = runTest {
        repo.cancelResult = networkFailure
        val vm = vm()
        vm.messages.test {
            vm.cancel(5)
            assertEquals("Can't reach Sawari. Check your connection.", awaitItem())
        }
        assertTrue(analytics.events.isEmpty())
        assertNull(vm.state.value.cancellingId)
    }
}
