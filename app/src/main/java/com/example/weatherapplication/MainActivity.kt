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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.weatherapplication.data.LanguagePreferenceStore
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
                val langStore = remember { LanguagePreferenceStore(context) }
                var language by remember { mutableStateOf(langStore.getLanguage().let { if (it.startsWith("ru")) "ru" else "en" }) }
                val vm = remember(language) { WeatherViewModel(store, language) }
                val locationProvider = remember { LocationProvider(context) }
                var savedCities by remember { mutableStateOf(store.getAll()) }

                // При смене языка перезагружаем последний город
                LaunchedEffect(language) {
                    val last = store.getLast()
                    if (last != null) {
                        vm.loadByCoordinates(last.latitude, last.longitude, displayName = last.name)
                    }
                }

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

                val isSaved: Boolean = vm.uiState.weather?.let { w ->
                    savedCities.any { it.latitude == w.city.latitude && it.longitude == w.city.longitude }
                } ?: false

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    gesturesEnabled = false,
                    drawerContent = {
                        ModalDrawerSheet(modifier = Modifier.fillMaxWidth()) {
                            val scrollState = rememberScrollState()
                            Column(Modifier.fillMaxWidth().verticalScroll(scrollState)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(R.string.my_cities),
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = { scope.launch { drawerState.close() } }) {
                                        Text("✕", color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                Divider()
                                if (savedCities.isEmpty()) {
                                    Text(stringResource(R.string.empty_list), modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
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
                                Divider(Modifier.padding(vertical = 8.dp))
                                Text(stringResource(R.string.drawer_language), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    FilterChip(
                                        selected = language == "ru",
                                        onClick = { language = "ru"; langStore.setLanguage("ru") },
                                        label = { Text(stringResource(R.string.language_russian)) }
                                    )
                                    FilterChip(
                                        selected = language == "en",
                                        onClick = { language = "en"; langStore.setLanguage("en") },
                                        label = { Text(stringResource(R.string.language_english)) }
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                ) {
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
                                            val delta = if (totalDx < 0) 1 else -1
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
                            language = language,
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
