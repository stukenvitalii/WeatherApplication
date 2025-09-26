package com.example.weatherapplication.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.weatherapplication.data.model.City
import com.example.weatherapplication.data.model.DailyForecast
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherScreen(
    state: WeatherUiState,
    saved: Boolean,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onUseLocation: () -> Unit,
    onOpenDrawer: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSelectSuggestion: (City) -> Unit,
    onStartSearch: () -> Unit,
    onStopSearch: () -> Unit,
    onRefresh: () -> Unit, // сигнатуру пока оставляю, чтобы не менять вызовы
) {
    val isNight = state.weather?.isNight == true
    val gradient = if (isNight) {
        Brush.verticalGradient(listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFF4FACFE), Color(0xFF00F2FE)))
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
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
                            val cityTitle = when {
                                explicitTitle != null -> explicitTitle
                                cityRaw.isNotBlank() && cityRaw != "Текущее местоположение" -> cityRaw
                                state.query.isNotBlank() -> state.query
                                else -> "Выберите город"
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
                    TextButton(onClick = onOpenDrawer) {
                        Text("≡", color = Color.White, fontSize = 20.sp)
                    }
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(gradient)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (state.isSearching) {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = onQueryChange,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        placeholder = { Text("Введите город (например, Москва)") },
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
                    TextButton(onClick = onStopSearch) { Text("Отмена") }

                    Spacer(Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { onSearch(state.query) }, shape = RoundedCornerShape(12.dp)) {
                            Text("Найти")
                        }
                        OutlinedButton(onClick = onUseLocation, shape = RoundedCornerShape(12.dp)) {
                            Text("Моё местоположение")
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
                                Text("Загружаем погоду...", color = Color.White)
                            }
                        }

                        "error" -> {
                            Text(
                                text = state.error ?: "Ошибка",
                                color = Color.White,
                                fontSize = 16.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        "data" -> {
                            val weather = state.weather!!
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
                                Spacer(Modifier.height(8.dp))
                                Text(weather.description, color = Color.White, fontSize = 18.sp)
                                weather.windSpeed?.let {
                                    Spacer(Modifier.height(8.dp))
                                    Text("Ветер: ${"%.1f".format(it)} м/с", color = Color.White)
                                }
                                if (weather.daily.isNotEmpty()) {
                                    Spacer(Modifier.height(16.dp))
                                    DailyForecastRow(items = weather.daily)
                                }
                            }
                        }

                        else -> {
                            Text(
                                text = "Введите город или используйте местоположение",
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
    ) {
        Text(city.name)
    }
}

@Composable
private fun DailyForecastRow(items: List<DailyForecast>) {
    val locale = Locale("ru")
    val dayFmt = DateTimeFormatter.ofPattern("E", locale)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        items.take(5).forEach { d ->
            val day =
                runCatching { LocalDate.parse(d.dateIso).format(dayFmt) }.getOrElse { d.dateIso }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(day, color = Color.White)
                WeatherArt(code = d.code, modifier = Modifier.size(48.dp))
                Text("${d.tempMinC.toInt()}° / ${d.tempMaxC.toInt()}°", color = Color.White)
            }
        }
    }
}
