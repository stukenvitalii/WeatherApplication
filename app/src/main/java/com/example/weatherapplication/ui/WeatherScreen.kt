package com.example.weatherapplication.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.weatherapplication.R
import com.example.weatherapplication.data.model.City
import com.example.weatherapplication.data.model.DailyForecast
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherScreen(
    state: WeatherUiState,
    saved: Boolean,
    language: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onUseLocation: () -> Unit,
    onOpenDrawer: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSelectSuggestion: (City) -> Unit,
    onStartSearch: () -> Unit,
    onStopSearch: () -> Unit,
    onRefresh: () -> Unit,
) {
    val isNight = state.weather?.isNight == true
    val gradient = if (isNight) {
        Brush.verticalGradient(listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFF4FACFE), Color(0xFF00F2FE)))
    }

    // Градиент теперь оборачивает Scaffold, чтобы фон был и за TopAppBar
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradient)
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                        actionIconContentColor = Color.White
                    ),
                    title = {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = Color(0x33FFFFFF)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .heightIn(min = 36.dp)
                                    .clickable { onStartSearch() }
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                val explicitTitle = state.topTitle?.takeIf { it.isNotBlank() }
                                val cityRaw = state.weather?.city?.name.orEmpty()
                                val currentLocationLabel = stringResource(R.string.current_location)
                                val cityTitle = when {
                                    explicitTitle != null -> explicitTitle
                                    cityRaw.isNotBlank() && cityRaw != currentLocationLabel -> cityRaw
                                    state.query.isNotBlank() -> state.query
                                    else -> stringResource(R.string.choose_city)
                                }
                                Text(
                                    cityTitle,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(10.dp))
                                Text("🔍", fontSize = 16.sp)
                            }
                        }
                    },
                    navigationIcon = {
                        TextButton(onClick = onOpenDrawer) { Text("≡", color = Color.White, fontSize = 20.sp) }
                    },
                    actions = {
                        if (state.weather != null) {
                            TextButton(onClick = onToggleFavorite) {
                                Text(if (saved) "★" else "☆", color = Color.Yellow, fontSize = 20.sp)
                            }
                        }
                    }
                )
            },
            containerColor = Color.Transparent
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Время последнего обновления (если есть данные) — сразу под AppBar
                if (!state.isSearching && state.weather != null) {
                    val dateFmt = SimpleDateFormat("HH:mm", if (language == "ru") Locale("ru") else Locale.ENGLISH)
                    val timeStr = dateFmt.format(Date(state.weather.fetchedAt))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.last_updated_format, timeStr),
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 12.sp
                        )
                        if (state.isCached) {
                            Text(
                                text = stringResource(R.string.cached_data_notice),
                                color = Color.Yellow,
                                fontSize = 12.sp
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                if (state.isSearching) {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = onQueryChange,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        placeholder = { Text(stringResource(R.string.search_placeholder)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onSearch(state.query) })
                    )

                    if (state.suggestions.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                state.suggestions.forEach { city ->
                                    SuggestionRow(city = city, onClick = { onSelectSuggestion(city) })
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onStopSearch) { Text(stringResource(R.string.cancel)) }

                    Spacer(Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { onSearch(state.query) }, shape = RoundedCornerShape(12.dp)) {
                            Text(stringResource(R.string.find))
                        }
                        OutlinedButton(onClick = onUseLocation, shape = RoundedCornerShape(12.dp)) {
                            Text(stringResource(R.string.my_location))
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                }

                val mode = when {
                    state.isLoading -> "loading"
                    state.error != null -> "error"
                    state.weather != null -> "data"
                    else -> "empty"
                }

                Crossfade(targetState = mode, label = "contentFade") { target ->
                    when (target) {
                        "loading" -> {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(color = Color.White)
                                Spacer(Modifier.height(8.dp))
                                Text(stringResource(R.string.loading_weather), color = Color.White)
                            }
                        }
                        "error" -> {
                            Text(
                                text = state.error ?: stringResource(R.string.error_generic),
                                color = Color.White,
                                fontSize = 16.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        "data" -> {
                            val weather = state.weather
                            if (weather == null) {
                                // защита от гонки состояния
                                Text(stringResource(R.string.loading_weather), color = Color.White)
                            } else {
                                val animTemp = animateFloatAsState(
                                    targetValue = weather.temperatureC.toFloat(),
                                    label = "tempAnim"
                                )
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .animateContentSize()
                                ) {
                                    WeatherArt(
                                        code = weather.code,
                                        isNight = weather.isNight,
                                        modifier = Modifier.size(180.dp)
                                    )
                                    Spacer(Modifier.height(16.dp))
                                    Text(
                                        "${"%.1f".format(animTemp.value)}°C",
                                        color = Color.White,
                                        fontSize = 56.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    // Feels like сразу под основной температурой
                                    weather.feelsLikeC?.let {
                                        Spacer(Modifier.height(4.dp))
                                        Text(stringResource(R.string.feels_like_format, it), color = Color.White, fontSize = 16.sp)
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Text(weather.description, color = Color.White, fontSize = 18.sp)
                                    weather.windSpeed?.let {
                                        Spacer(Modifier.height(8.dp))
                                        Text(stringResource(R.string.wind_format, it), color = Color.White)
                                    }
                                    // Прогноз на дни в виде карусели
                                    if (weather.daily.isNotEmpty()) {
                                        Spacer(Modifier.height(20.dp))
                                        DailyForecastCarousel(items = weather.daily, language = language)
                                    }
                                    // Раскрываемые дополнительные показатели (кроме feels like)
                                    var detailsExpanded by remember { mutableStateOf(false) }
                                    if (weather.uvIndex != null || weather.cloudCoverPct != null || weather.visibilityKm != null || weather.pressureHpa != null || weather.dewPointC != null) {
                                        Spacer(Modifier.height(16.dp))
                                        TextButton(onClick = { detailsExpanded = !detailsExpanded }) {
                                            Text(
                                                text = if (detailsExpanded) stringResource(R.string.hide_details) else stringResource(R.string.show_details),
                                                color = Color.White
                                            )
                                        }
                                        if (detailsExpanded) {
                                            ElevatedCard(
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(16.dp),
                                                colors = CardDefaults.elevatedCardColors(containerColor = Color(0x33111111))
                                            ) {
                                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    weather.uvIndex?.let { Text(stringResource(R.string.uv_index_format, it), color = Color.White, fontSize = 14.sp) }
                                                    weather.cloudCoverPct?.let { Text(stringResource(R.string.cloud_cover_format, it), color = Color.White, fontSize = 14.sp) }
                                                    weather.visibilityKm?.let { Text(stringResource(R.string.visibility_format, it), color = Color.White, fontSize = 14.sp) }
                                                    weather.pressureHpa?.let { Text(stringResource(R.string.pressure_format, it), color = Color.White, fontSize = 14.sp) }
                                                    weather.dewPointC?.let { Text(stringResource(R.string.dew_point_format, it), color = Color.White, fontSize = 14.sp) }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        else -> {
                            Text(
                                text = stringResource(R.string.enter_city_or_use_location),
                                color = Color.White,
                                fontSize = 16.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                Spacer(Modifier.height(56.dp))
            }
        }
    }
}

@Composable
private fun SuggestionRow(city: City, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) { Text(city.name) }
}

@Composable
private fun DailyForecastCarousel(items: List<DailyForecast>, language: String) {
    val locale = if (language == "ru") Locale("ru") else Locale.ENGLISH
    val dayFmt = DateTimeFormatter.ofPattern("EEE", locale)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(items.take(7)) { d ->
            val day = runCatching { LocalDate.parse(d.dateIso).format(dayFmt) }.getOrElse { d.dateIso }
            ElevatedCard(shape = RoundedCornerShape(16.dp), colors = CardDefaults.elevatedCardColors(containerColor = Color(0x33FFFFFF))) {
                Column(
                    modifier = Modifier
                        .width(100.dp)
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(day.uppercase(locale), color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(6.dp))
                    WeatherArt(code = d.code, modifier = Modifier.size(42.dp))
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "${d.tempMinC.toInt()}° / ${d.tempMaxC.toInt()}°",
                        color = Color.White,
                        fontSize = 14.sp,
                        lineHeight = 16.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
