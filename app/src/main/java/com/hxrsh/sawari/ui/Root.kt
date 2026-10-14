package com.hxrsh.sawari.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.hxrsh.sawari.analytics.Analytics
import com.hxrsh.sawari.domain.model.User
import com.hxrsh.sawari.domain.usecase.ObserveSessionUseCase
import com.hxrsh.sawari.ui.auth.AuthScreen
import com.hxrsh.sawari.ui.booking.BookingScreen
import com.hxrsh.sawari.ui.bookings.MyBookingsScreen
import com.hxrsh.sawari.ui.cars.CarListScreen
import com.hxrsh.sawari.ui.detail.CarDetailScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed interface SessionState {
    data object Loading : SessionState
    data object LoggedOut : SessionState
    data class LoggedIn(val user: User) : SessionState
}

@HiltViewModel
class MainViewModel @Inject constructor(
    observeSession: ObserveSessionUseCase,
    analytics: Analytics,
) : ViewModel() {
    val sessionState: StateFlow<SessionState> = observeSession()
        .onEach { analytics.setUser(it?.user?.id?.toString()) }
        .map { if (it == null) SessionState.LoggedOut else SessionState.LoggedIn(it.user) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SessionState.Loading)
}

object Routes {
    const val CARS = "cars"
    const val BOOKINGS = "bookings"
    const val CAR_DETAIL = "car/{carId}"
    const val BOOKING = "book/{carId}"
    fun carDetail(id: Int) = "car/$id"
    fun booking(id: Int) = "book/$id"
}

@Composable
fun SawariRoot(viewModel: MainViewModel = hiltViewModel()) {
    val session by viewModel.sessionState.collectAsStateWithLifecycle()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (session) {
            SessionState.Loading -> Box(Modifier.fillMaxSize())
            SessionState.LoggedOut -> AuthScreen()
            is SessionState.LoggedIn -> MainNav()
        }
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.CARS, "Cars", Icons.Default.DirectionsCar),
    Tab(Routes.BOOKINGS, "My bookings", Icons.Default.Event),
)

@Composable
private fun MainNav() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val showBar = tabs.any { tab -> destination?.hierarchy?.any { it.route == tab.route } == true }
    Scaffold(
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = destination?.hierarchy?.any { it.route == tab.route } == true,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                            modifier = Modifier.testTag("tab_${tab.route}"),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(navController, startDestination = Routes.CARS, modifier = Modifier.padding(padding)) {
            composable(Routes.CARS) { CarListScreen(onCarClick = { navController.navigate(Routes.carDetail(it)) }) }
            composable(Routes.BOOKINGS) { MyBookingsScreen() }
            composable(
                Routes.CAR_DETAIL,
                arguments = listOf(navArgument("carId") { type = NavType.IntType }),
            ) {
                CarDetailScreen(
                    onBack = { navController.popBackStack() },
                    onBook = { navController.navigate(Routes.booking(it)) },
                )
            }
            composable(
                Routes.BOOKING,
                arguments = listOf(navArgument("carId") { type = NavType.IntType }),
            ) {
                BookingScreen(
                    onBack = { navController.popBackStack() },
                    onBooked = {
                        navController.navigate(Routes.BOOKINGS) {
                            popUpTo(Routes.CARS)
                            launchSingleTop = true
                        }
                    },
                )
            }
        }
    }
}
