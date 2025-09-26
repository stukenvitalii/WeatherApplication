package com.example.weatherapplication

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.weatherapplication.data.SavedCitiesStore
import com.example.weatherapplication.data.model.City
import com.example.weatherapplication.location.LocationProvider
import com.example.weatherapplication.ui.WeatherScreen
import com.example.weatherapplication.ui.WeatherViewModel
import com.example.weatherapplication.ui.theme.WeatherApplicationTheme
import androidx.activity.compose.rememberLauncherForActivityResult
import kotlinx.coroutines.launch
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WeatherApplicationTheme {
                val context = LocalContext.current
                val store = remember { SavedCitiesStore(context) }
                val vm = remember { WeatherViewModel(store) }
                val locationProvider = remember { LocationProvider(context) }
                var savedCities by remember { mutableStateOf(store.getAll()) }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { grants ->
                    val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
                    if (granted) {
                        locationProvider.getLastKnownLocation()?.let { coords ->
                            vm.loadByCoordinates(coords.latitude, coords.longitude)
                        }
                    }
                }

                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val scope = rememberCoroutineScope()
                val density = LocalDensity.current

                // Saved flag
                val isSaved: Boolean = vm.uiState.weather?.let { w ->
                    savedCities.any { it.latitude == w.city.latitude && it.longitude == w.city.longitude }
                } ?: false

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    gesturesEnabled = false,
                    drawerContent = {
                        ModalDrawerSheet(modifier = Modifier.fillMaxWidth()) {
                            Text("Мои города", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
                            if (savedCities.isEmpty()) {
                                Text("Пока пусто", modifier = Modifier.padding(horizontal = 16.dp))
                            } else {
                                savedCities.forEach { city ->
                                    CityRow(
                                        city = city,
                                        onClick = {
                                            vm.loadByCoordinates(city.latitude, city.longitude, displayName = city.name)
                                            scope.launch { drawerState.close() }
                                        },
                                        onRemove = {
                                            store.remove(city)
                                            savedCities = store.getAll()
                                        }
                                    )
                                }
                            }
                        }
                    }
                ) {
                    // Обработчик свайпа между сохранёнными городами
                    var totalDx by remember { mutableStateOf(0f) }
                    var totalDy by remember { mutableStateOf(0f) }
                    var handled by remember { mutableStateOf(false) }
                    val thresholdPx = with(density) { 96.dp.toPx() }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(savedCities, vm.uiState.isSearching, vm.uiState.weather?.city?.latitude, vm.uiState.weather?.city?.longitude) {
                                detectDragGestures(
                                    onDragStart = {
                                        totalDx = 0f
                                        totalDy = 0f
                                        handled = false
                                    },
                                    onDragEnd = {
                                        totalDx = 0f
                                        totalDy = 0f
                                        handled = false
                                    },
                                    onDragCancel = {
                                        totalDx = 0f
                                        totalDy = 0f
                                        handled = false
                                    }
                                ) { change, dragAmount ->
                                    change.consume()
                                    if (vm.uiState.isSearching || handled) return@detectDragGestures
                                    totalDx += dragAmount.x
                                    totalDy += dragAmount.y
                                    if (abs(totalDx) >= thresholdPx && abs(totalDx) > abs(totalDy)) {
                                        val size = savedCities.size
                                        if (size >= 2) {
                                            val current = vm.uiState.weather?.city
                                            val currIdx = savedCities.indexOfFirst { it.latitude == current?.latitude && it.longitude == current?.longitude }
                                            val baseIdx = if (currIdx >= 0) currIdx else 0
                                            val delta = if (totalDx < 0) 1 else -1 // влево -> следующий, вправо -> предыдущий
                                            val nextIdx = (baseIdx + delta + size) % size
                                            val next = savedCities[nextIdx]
                                            vm.loadByCoordinates(next.latitude, next.longitude, displayName = next.name)
                                        }
                                        handled = true
                                    }
                                }
                            }
                    ) {
                        WeatherScreen(
                            state = vm.uiState,
                            saved = isSaved,
                            onQueryChange = { q -> vm.setQuery(q) },
                            onSearch = { city -> vm.loadWeatherByCity(city) },
                            onUseLocation = {
                                if (locationProvider.hasLocationPermission()) {
                                    locationProvider.getLastKnownLocation()?.let { coords ->
                                        vm.loadByCoordinates(coords.latitude, coords.longitude)
                                    }
                                } else {
                                    permissionLauncher.launch(arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    ))
                                }
                            },
                            onOpenDrawer = { scope.launch { drawerState.open() } },
                            onToggleFavorite = {
                                vm.uiState.weather?.city?.let { c ->
                                    if (isSaved) store.remove(c) else store.add(c)
                                    savedCities = store.getAll()
                                }
                            },
                            onSelectSuggestion = { city -> vm.selectSuggestion(city) },
                            onStartSearch = { vm.startSearch() },
                            onStopSearch = { vm.stopSearch() },
                            onRefresh = { vm.refresh() }
                        )
                    }
                }

                LaunchedEffect(Unit) {
                    val last = store.getLast()
                    if (last != null) {
                        vm.loadByCoordinates(last.latitude, last.longitude, displayName = last.name)
                    } else if (locationProvider.hasLocationPermission()) {
                        locationProvider.getLastKnownLocation()?.let { coords ->
                            vm.loadByCoordinates(coords.latitude, coords.longitude)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CityRow(city: City, onClick: () -> Unit, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(city.name, style = MaterialTheme.typography.bodyLarge)
            val sub = listOfNotNull(city.country).joinToString()
            if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = onRemove) { Text("×") }
    }
}
