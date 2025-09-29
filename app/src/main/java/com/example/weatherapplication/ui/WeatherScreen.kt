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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource


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
    // состояние выбранного дня и стейт bottom sheet
    var selectedDaily by remember { mutableStateOf<DailyForecast?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
                    val now = System.currentTimeMillis()
                    val deltaMin = ((now - state.weather.fetchedAt) / 60000L).coerceAtLeast(0)
                    val updatedText = if (deltaMin <= 15) {
                        when {
                            deltaMin == 0L -> stringResource(R.string.just_now)
                            else -> stringResource(R.string.minutes_ago_format, deltaMin.toInt())
                        }
                    } else {
                        val dateFmt = SimpleDateFormat("HH:mm", if (language == "ru") Locale("ru") else Locale.ENGLISH)
                        dateFmt.format(Date(state.weather.fetchedAt))
                    }
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.last_updated_prefix) + " " + updatedText,
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                        if (state.isCached) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.cached_data_notice),
                                color = Color.Yellow,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
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
                                        DailyForecastCarousel(
                                            items = weather.daily,
                                            language = language,
                                            onSelect = { selectedDaily = it }
                                        )
                                    }
                                    // Раскрываемые дополнительные показатели (кроме feels like)
                                    var detailsExpanded by remember { mutableStateOf(false) }
                                    val hasAnyMetric = listOf(
                                        weather.uvIndex, weather.cloudCoverPct, weather.visibilityKm, weather.pressureHpa, weather.dewPointC
                                    ).any { it != null }
                                    if (hasAnyMetric) {
                                        Spacer(Modifier.height(16.dp))
                                        TextButton(onClick = { detailsExpanded = !detailsExpanded }) {
                                            Text(
                                                text = if (detailsExpanded) stringResource(R.string.hide_details) else stringResource(R.string.show_details),
                                                color = Color.White
                                            )
                                        }
                                        if (detailsExpanded) {
                                            MetricChipsRow(weather = weather, language = language)
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
    // Bottom sheet details for selected day
    if (selectedDaily != null) {
        val day = selectedDaily!!
        ModalBottomSheet(
            onDismissRequest = { selectedDaily = null },
            sheetState = sheetState,
            containerColor = if (isNight) Color(0xFF1E2A33) else Color(0xFFF2F8FF)
        ) {
            DailyDetailsContent(day = day, language = language, isNight = isNight)
        }
    }
}

private fun weatherCodeDescription(code: Int, language: String): String = when (language) {
    "ru" -> when (code) {
        0 -> "Ясно"; 1,2 -> "Малооблачно"; 3 -> "Пасмурно"; 45,48 -> "Туман"; 51,53,55 -> "Моросящий дождь"; 61,63,65 -> "Дождь";
        66,67 -> "Ледяной дождь"; 71,73,75 -> "Снег"; 77 -> "Снегопад"; 80,81,82 -> "Ливни"; 85,86 -> "Снеговые ливни"; 95 -> "Гроза"; 96,99 -> "Гроза с градом"; else -> "Неизвестно"
    }
    else -> when (code) {
        0 -> "Clear"; 1,2 -> "Partly cloudy"; 3 -> "Overcast"; 45,48 -> "Fog"; 51,53,55 -> "Drizzle"; 61,63,65 -> "Rain";
        66,67 -> "Freezing rain"; 71,73,75 -> "Snow"; 77 -> "Snowfall"; 80,81,82 -> "Showers"; 85,86 -> "Snow showers"; 95 -> "Thunderstorm"; 96,99 -> "Thunderstorm hail"; else -> "Unknown"
    }
}

@Composable
private fun DailyDetailsContent(day: DailyForecast, language: String, isNight: Boolean) {
    val locale = if (language == "ru") Locale("ru") else Locale.ENGLISH
    val fullFmt = DateTimeFormatter.ofPattern("EEEE, dd MMM", locale)
    val title = runCatching { LocalDate.parse(day.dateIso).format(fullFmt) }.getOrElse { day.dateIso }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title.replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = if (isNight) Color.White else Color(0xFF102027), textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        WeatherArt(code = day.code, modifier = Modifier.size(96.dp), isNight = isNight)
        Spacer(Modifier.height(12.dp))
        Text(weatherCodeDescription(day.code, language), color = if (isNight) Color.White else Color(0xFF102027))
        Spacer(Modifier.height(8.dp))
        Text(
            text = "${day.tempMinC.toInt()}° / ${day.tempMaxC.toInt()}°",
            color = if (isNight) Color.White else Color(0xFF102027),
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(20.dp))
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
private fun DailyForecastCarousel(
    items: List<DailyForecast>,
    language: String,
    onSelect: (DailyForecast) -> Unit
) {
    val locale = if (language == "ru") Locale("ru") else Locale.ENGLISH
    val dayFmt = DateTimeFormatter.ofPattern("EEE", locale)
    val todayIso = LocalDate.now().toString()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = Color(0x1FFFFFFF)
    ) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
        ) {
            items(items.take(7)) { d ->
                val day = runCatching { LocalDate.parse(d.dateIso).format(dayFmt) }.getOrElse { d.dateIso }
                val isToday = d.dateIso == todayIso
                val itemBg = if (isToday) Brush.verticalGradient(listOf(Color(0x66FFFFFF), Color(0x33FFFFFF))) else null
                Box(
                    modifier = Modifier
                        .width(80.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .then(if (itemBg != null) Modifier.background(itemBg) else Modifier)
                        .clickable { onSelect(d) }
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isToday) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFFFD54F))
                                )
                                Spacer(Modifier.width(4.dp))
                            }
                            Text(
                                day.uppercase(locale),
                                color = Color.White,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        WeatherArt(code = d.code, modifier = Modifier.size(38.dp))
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "${d.tempMinC.toInt()}° / ${d.tempMaxC.toInt()}°",
                            color = Color.White,
                            fontSize = 13.sp,
                            lineHeight = 15.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricChipsRow(weather: com.example.weatherapplication.data.model.WeatherInfo, language: String) {
    val isRu = language == "ru"
    val items = buildMetricItems(weather = weather, isRu = isRu)
    if (items.isEmpty()) {
        Text(text = stringResource(R.string.metric_no_data), color = Color.White)
        return
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
        items(items) { metric ->
            ElevatedCard(
                modifier = Modifier
                    .width(150.dp)
                    .heightIn(min = 132.dp)
                    .clip(RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = Color(0x33FFFFFF)),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Icon(
                        painter = painterResource(metric.icon),
                        contentDescription = metric.label,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            metric.value,
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            metric.label,
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        metric.extra?.let { extra ->
                            Spacer(Modifier.height(2.dp))
                            Text(
                                extra,
                                color = Color.White.copy(alpha = 0.65f),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class MetricChipData(
    val icon: Int,
    val label: String,
    val value: String,
    val extra: String? = null,
)

@Composable
private fun buildMetricItems(weather: com.example.weatherapplication.data.model.WeatherInfo, isRu: Boolean): List<MetricChipData> {
    val list = mutableListOf<MetricChipData>()
    weather.uvIndex?.let {
        list.add(
            MetricChipData(
                icon = R.drawable.ic_metric_uv,
                label = if (isRu) stringResource(R.string.metric_uv) else stringResource(R.string.metric_uv),
                value = "${"%.1f".format(it)}",
                extra = if (it < 3) (if (isRu) "низкий" else "low") else if (it < 6) (if (isRu) "умеренный" else "moderate") else if (it < 8) (if (isRu) "высокий" else "high") else if (it < 11) (if (isRu) "оч. выс." else "very high") else (if (isRu) "экстрим" else "extreme")
            )
        )
    }
    weather.cloudCoverPct?.let {
        list.add(
            MetricChipData(
                icon = R.drawable.ic_metric_cloud,
                label = if (isRu) stringResource(R.string.metric_cloud) else stringResource(R.string.metric_cloud),
                value = "$it%",
                extra = when {
                    it < 20 -> if (isRu) "ясно" else "clear"
                    it < 60 -> if (isRu) "переменно" else "partly"
                    else -> if (isRu) "пасмурно" else "overcast"
                }
            )
        )
    }
    weather.visibilityKm?.let {
        val unit = if (isRu) "км" else "km"
        list.add(
            MetricChipData(
                icon = R.drawable.ic_metric_visibility,
                label = if (isRu) stringResource(R.string.metric_visibility) else stringResource(R.string.metric_visibility),
                value = "${"%.1f".format(it)}",
                extra = unit
            )
        )
    }
    weather.pressureHpa?.let {
        val mm = it * 0.75006
        val unit = if (isRu) "мм" else "mmHg"
        list.add(
            MetricChipData(
                icon = R.drawable.ic_metric_pressure,
                label = if (isRu) stringResource(R.string.metric_pressure) else stringResource(R.string.metric_pressure),
                value = mm.toInt().toString(),
                extra = unit
            )
        )
    }
    weather.dewPointC?.let {
        list.add(
            MetricChipData(
                icon = R.drawable.ic_metric_dew,
                label = if (isRu) stringResource(R.string.metric_dew) else stringResource(R.string.metric_dew),
                value = "${"%.1f".format(it)}°",
                extra = if (isRu) "точка" else "point"
            )
        )
    }
    return list
}
