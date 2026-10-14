package com.hxrsh.sawari.ui.cars

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hxrsh.sawari.domain.model.Car
import com.hxrsh.sawari.ui.common.EmptyState
import com.hxrsh.sawari.ui.common.ErrorState
import com.hxrsh.sawari.ui.common.LoadingState
import com.hxrsh.sawari.ui.common.OfflineBanner
import com.hxrsh.sawari.ui.common.UiState

private val FUELS = listOf("Petrol", "Diesel", "Electric", "CNG")

@Composable
fun CarListScreen(onCarClick: (Int) -> Unit, viewModel: CarListViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CarListContent(
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onCityChange = viewModel::onCityChange,
        onFuelChange = viewModel::onFuelChange,
        onRefresh = viewModel::refresh,
        onCarClick = onCarClick,
    )
}

/** Stateless: everything comes in as parameters, which keeps it previewable and UI-testable. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarListContent(
    state: CarListState,
    onQueryChange: (String) -> Unit,
    onCityChange: (String?) -> Unit,
    onFuelChange: (String?) -> Unit,
    onRefresh: () -> Unit,
    onCarClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.filter.query,
            onValueChange = onQueryChange,
            placeholder = { Text("Search cars") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (state.filter.query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Default.Close, contentDescription = "Clear search") }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("search_field"),
        )
        FilterRow(
            label = "City",
            options = state.cities,
            selected = state.filter.city,
            onSelected = onCityChange,
            tag = "city_chip_",
        )
        FilterRow(label = "Fuel", options = FUELS, selected = state.filter.fuel, onSelected = onFuelChange, tag = "fuel_chip_")
        if (state.staleNotice != null) {
            OfflineBanner(state.staleNotice, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }
        PullToRefreshBox(isRefreshing = state.isRefreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
            when (val content = state.content) {
                UiState.Loading -> LoadingState()
                UiState.Empty -> EmptyState("No cars found", "Try a different search or clear the filters.")
                is UiState.Error -> ErrorState(content.message, onRetry = onRefresh)
                is UiState.Content -> CarList(content.data, onCarClick)
            }
        }
    }
}

@Composable
private fun FilterRow(
    label: String,
    options: List<String>,
    selected: String?,
    onSelected: (String?) -> Unit,
    tag: String,
) {
    if (options.isEmpty()) return
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(options, key = { it }) { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelected(if (option == selected) null else option) },
                label = { Text(option) },
                modifier = Modifier.testTag(tag + option),
            )
        }
    }
}

@Composable
private fun CarList(cars: List<Car>, onCarClick: (Int) -> Unit) {
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().testTag("car_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Stable key + contentType: item state survives reordering/refresh and Compose can reuse item slots.
        items(cars, key = { it.id }, contentType = { "car" }) { car ->
            CarCard(car = car, onClick = onCarClick)
        }
    }
}
