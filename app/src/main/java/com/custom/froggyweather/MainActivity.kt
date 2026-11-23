package com.custom.froggyweather

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

const val APP_VERSION_LABEL = "v9  1.8.0 (code 9)"

data class HourlyItem(
    val dateStr: String,
    val hourInt: Int,
    val timeLabel: String,
    val temp: Int,
    val apparentTemp: Int,
    val weatherCode: Int,
    val isDay: Boolean,
    val precipProb: Int,
    val precipMm: Double,
    val windSpeed: Int,
    val windDir: Float,
    val humidity: Int,
    val isCurrentHour: Boolean
)

data class DailyItem(
    val dateStr: String,
    val dayName: String,
    val maxTemp: Int,
    val minTemp: Int,
    val maxWind: Int,
    val weatherCode: Int
)

data class WeatherState(
    val cityName: String = "Хмельницький",
    val regionName: String = "Хмельницька область",
    val currentTemp: Int = 0,
    val feelsLike: Int = 0,
    val currentCode: Int = 0,
    val isDay: Boolean = true,
    val currentWind: Int = 0,
    val currentPrecipProb: Int = 0,
    val currentHumidity: Int = 0,
    val hourly: List<HourlyItem> = emptyList(),
    val daily: List<DailyItem> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                WeatherScreen()
            }
        }
    }
}

@Composable
fun WeatherScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var state by remember { mutableStateOf(WeatherState()) }
    var selectedDayIndex by remember { mutableIntStateOf(0) }
    var selectedHourIndex by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var showSearchDialog by remember { mutableStateOf(false) }
    var coords by remember { mutableStateOf(49.4229 to 26.9871) }
    var manualOverride by remember { mutableStateOf(false) }
    var autoLocationStatus by remember { mutableStateOf("Автогеолокація") }

    suspend fun loadWeatherAndSyncWidget(city: String, region: String, lat: Double, lon: Double) {
        val loaded = fetchWeather(city, region, lat, lon)
        state = loaded
        if (loaded.error == null) {
            FroggyWeatherWidget.saveWeatherSnapshot(
                context = context,
                city = loaded.cityName,
                temp = loaded.currentTemp,
                weatherCode = loaded.currentCode,
                lat = lat,
                lon = lon
            )
            if (selectedDayIndex == 0) {
                val nowIdx = loaded.hourly
                    .filter { it.dateStr == loaded.daily.firstOrNull()?.dateStr }
                    .indexOfFirst { it.isCurrentHour }
                selectedHourIndex = if (nowIdx >= 0) nowIdx else 0
            } else {
                selectedHourIndex = 12
            }
        }
    }

    suspend fun detectAndLoadCurrentLocation() {
        val loc = getLastKnownOrSingleLocation(context)
        if (loc != null) {
            coords = loc.latitude to loc.longitude
            val place = resolvePlaceName(context, loc.latitude, loc.longitude)
            state = state.copy(
                cityName = place.first,
                regionName = place.second,
                isLoading = true,
                error = null
            )
            autoLocationStatus = "GPS: ${place.first}"
            loadWeatherAndSyncWidget(place.first, place.second, loc.latitude, loc.longitude)
        } else {
            loadWeatherAndSyncWidget(state.cityName, state.regionName, coords.first, coords.second)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        scope.launch {
            if (granted && !manualOverride) {
                detectAndLoadCurrentLocation()
            } else {
                loadWeatherAndSyncWidget(state.cityName, state.regionName, coords.first, coords.second)
            }
        }
    }

    LaunchedEffect(Unit) {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (hasFine || hasCoarse) {
            detectAndLoadCurrentLocation()
        } else {
            loadWeatherAndSyncWidget(state.cityName, state.regionName, coords.first, coords.second)
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    val bgDark = Color(0xFF141619)
    val cardDark = Color(0xFF22252A)
    val accentBlue = Color(0xFF8AB4F8)

    Scaffold(
        containerColor = bgDark,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { showSearchDialog = true }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (!manualOverride) Icons.Rounded.MyLocation else Icons.Rounded.LocationOn,
                        contentDescription = null,
                        tint = Color(0xFFA8C7FA),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (state.regionName.isNotEmpty()) "${state.cityName}, ${state.regionName}" else state.cityName,
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Icon(
                        Icons.Rounded.ArrowDropDown,
                        contentDescription = null,
                        tint = Color(0xFFD0D5DD),
                        modifier = Modifier.size(22.dp)
                    )
                }

                IconButton(onClick = {
                    scope.launch {
                        manualOverride = false
                        state = state.copy(isLoading = true)
                        detectAndLoadCurrentLocation()
                    }
                }) {
                    Icon(
                        Icons.Rounded.GpsFixed,
                        contentDescription = autoLocationStatus,
                        tint = if (!manualOverride) Color(0xFF8AB4F8) else Color(0xFF9AA0A6)
                    )
                }

                IconButton(onClick = {
                    scope.launch {
                        state = state.copy(isLoading = true)
                        loadWeatherAndSyncWidget(state.cityName, state.regionName, coords.first, coords.second)
                    }
                }) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Оновити", tint = Color(0xFFD0D5DD))
                }
            }
        }
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = accentBlue)
            }
        } else if (state.error != null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Помилка: ${state.error}", color = Color(0xFFF28B82), fontWeight = FontWeight.Bold)
            }
        } else {
            val activeDay = state.daily.getOrNull(selectedDayIndex)
            val dayHours = if (activeDay != null) {
                state.hourly.filter { it.dateStr == activeDay.dateStr }
            } else {
                state.hourly.take(24)
            }
            val safeHourIndex = selectedHourIndex.coerceIn(0, (dayHours.size - 1).coerceAtLeast(0))
            val selectedHour = dayHours.getOrNull(safeHourIndex)

            val isNowSelected = selectedDayIndex == 0 && (selectedHour?.isCurrentHour == true)
            val displayTemp = if (isNowSelected) state.currentTemp else (selectedHour?.temp ?: activeDay?.maxTemp ?: 0)
            val displayFeels = if (isNowSelected) state.feelsLike else (selectedHour?.apparentTemp ?: displayTemp)
            val displayCode = if (isNowSelected) state.currentCode else (selectedHour?.weatherCode ?: activeDay?.weatherCode ?: 0)
            val displayIsDay = selectedHour?.isDay ?: (if (selectedDayIndex == 0) state.isDay else true)
            val displayWind = if (isNowSelected) state.currentWind else (selectedHour?.windSpeed ?: activeDay?.maxWind ?: 0)
            val displayPrecipProb = if (isNowSelected) state.currentPrecipProb else (selectedHour?.precipProb ?: 0)
            val displayHumidity = if (isNowSelected) state.currentHumidity else (selectedHour?.humidity ?: 0)
            val maxWindToday = maxOf(displayWind, dayHours.maxOfOrNull { it.windSpeed } ?: 0)

            val headerTimeTitle = when {
                isNowSelected -> "Зараз"
                selectedDayIndex == 0 && selectedHour != null -> "Сьогодні о ${selectedHour.timeLabel}"
                activeDay != null && selectedHour != null -> "${activeDay.dayName.uppercase()} о ${selectedHour.timeLabel}"
                else -> "Прогноз"
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .background(cardDark)
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = headerTimeTitle,
                                color = Color(0xFFE4E7EC),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (displayIsDay) " День" else " Ніч",
                                color = Color(0xFFCBD5E1),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "$displayTemp",
                                    color = Color.White,
                                    fontSize = 54.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Spacer(Modifier.width(10.dp))
                                WeatherCodeIcon(displayCode, isDay = displayIsDay, size = 38)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = weatherDescription(displayCode),
                                    color = Color.White,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Відчувається як $displayFeels",
                                    color = Color(0xFFD0D5DD),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        if (maxWindToday >= 14) {
                            Spacer(Modifier.height(10.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFF3A3532))
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Rounded.Air,
                                    contentDescription = null,
                                    tint = Color(0xFFF2B8B5),
                                    modifier = Modifier.size(19.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Вітряно  пориви до $maxWindToday км/год",
                                    color = Color(0xFFF3EAE8),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        Text(
                            text = "Погода за добу (натисніть годину):",
                            color = Color(0xFF98A2B3),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(8.dp))

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            itemsIndexed(dayHours) { idx, h ->
                                val isHourSelected = idx == safeHourIndex
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .width(58.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(if (isHourSelected) Color(0xFF334155) else Color(0xFF1B1E23))
                                        .border(
                                            width = if (isHourSelected) 1.5.dp else 0.dp,
                                            color = if (isHourSelected) Color(0xFF8AB4F8) else Color.Transparent,
                                            shape = RoundedCornerShape(14.dp)
                                        )
                                        .clickable { selectedHourIndex = idx }
                                        .padding(vertical = 8.dp, horizontal = 4.dp)
                                ) {
                                    Text(
                                        text = "${h.temp}",
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    WeatherCodeIcon(h.weatherCode, isDay = h.isDay, size = 24)
                                    if (h.precipProb > 0) {
                                        Text(
                                            text = "${h.precipProb}%",
                                            color = accentBlue,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    } else {
                                        Spacer(Modifier.height(15.dp))
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = if (h.isCurrentHour && selectedDayIndex == 0) "Зараз" else h.timeLabel,
                                        color = if (isHourSelected) Color.White else Color(0xFFCBD5E1),
                                        fontSize = 12.sp,
                                        fontWeight = if (isHourSelected) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        FrogDynamicScene(
                            weatherCode = displayCode,
                            isDay = displayIsDay,
                            temp = displayTemp,
                            windSpeed = displayWind
                        )
                    }
                }

                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(state.daily) { index, day ->
                            val isSelected = index == selectedDayIndex
                            Column(
                                modifier = Modifier
                                    .width(74.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (isSelected) Color(0xFF32353B) else cardDark)
                                    .border(
                                        width = if (isSelected) 1.5.dp else 0.dp,
                                        color = if (isSelected) Color(0xFFA8C7FA) else Color.Transparent,
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .clickable {
                                        selectedDayIndex = index
                                        if (index == 0) {
                                            val curIdx = state.hourly
                                                .filter { it.dateStr == day.dateStr }
                                                .indexOfFirst { it.isCurrentHour }
                                            selectedHourIndex = if (curIdx >= 0) curIdx else 0
                                        } else {
                                            selectedHourIndex = 12
                                        }
                                    }
                                    .padding(vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = day.dayName.uppercase(),
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(8.dp))
                                WeatherCodeIcon(day.weatherCode, isDay = true, size = 26)
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "${day.maxTemp}/${day.minTemp}",
                                    color = Color(0xFFE4E7EC),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                item {
                    val totalMm = dayHours.sumOf { it.precipMm }
                    val chartHours = dayHours.filterIndexed { i, _ -> i % 3 == 0 }.take(8)
                    ExpandableWeatherCard(
                        icon = Icons.Rounded.WaterDrop,
                        title = "Опади",
                        summary = "$displayPrecipProb%",
                        cardColor = cardDark,
                        initiallyExpanded = true
                    ) {
                        Text(
                            text = "Загалом за добу: ${String.format(Locale.US, "%.1f", totalMm)} мм",
                            color = Color(0xFFD0D5DD),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            chartHours.forEach { h ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (h.precipMm == 0.0) "0,0" else String.format(Locale.US, "%.1f", h.precipMm),
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .width(24.dp)
                                            .height(5.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(if (h.precipProb > 0) accentBlue else Color(0xFF5F6368))
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text("${h.precipProb}%", color = accentBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Text(h.timeLabel, color = Color(0xFF98A2B3), fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                item {
                    val chartHours = dayHours.filterIndexed { i, _ -> i % 3 == 0 }.take(8)
                    ExpandableWeatherCard(
                        icon = Icons.Rounded.Air,
                        title = "Вітер",
                        summary = "$displayWind км/год",
                        cardColor = cardDark,
                        initiallyExpanded = false
                    ) {
                        Text(
                            text = "Максимум за добу: $maxWindToday км/год",
                            color = Color(0xFFD0D5DD),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            chartHours.forEach { h ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                    Text("${h.windSpeed}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(8.dp))
                                    Icon(
                                        imageVector = Icons.Rounded.Navigation,
                                        contentDescription = null,
                                        tint = accentBlue,
                                        modifier = Modifier
                                            .size(18.dp)
                                            .rotate(h.windDir + 180f)
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(h.timeLabel, color = Color(0xFF98A2B3), fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                item {
                    val chartHours = dayHours.filterIndexed { i, _ -> i % 3 == 0 }.take(8)
                    val avgHum = if (dayHours.isNotEmpty()) dayHours.map { it.humidity }.average().roundToInt() else displayHumidity
                    ExpandableWeatherCard(
                        icon = Icons.Rounded.Opacity,
                        title = "Вологість",
                        summary = "$displayHumidity%",
                        cardColor = cardDark,
                        initiallyExpanded = false
                    ) {
                        Text("У середньому за добу: $avgHum%", color = Color(0xFFD0D5DD), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(12.dp))
                        HumidityChart(chartHours, accentBlue)
                    }
                }

                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Фроггі Погода  $APP_VERSION_LABEL",
                            color = Color(0xFF98A2B3),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                item { Spacer(Modifier.height(18.dp)) }
            }
        }
    }

    if (showSearchDialog) {
        AlertDialog(
            onDismissRequest = { showSearchDialog = false },
            title = { Text("Місто або автогеолокація", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Наприклад: Київ, Львів, Одеса") },
                        singleLine = true
                    )
                    Spacer(Modifier.height(10.dp))
                    TextButton(onClick = {
                        showSearchDialog = false
                        manualOverride = false
                        scope.launch {
                            state = state.copy(isLoading = true)
                            detectAndLoadCurrentLocation()
                        }
                    }) {
                        Icon(Icons.Rounded.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Визначити автоматично (GPS)", fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val q = searchQuery.trim()
                    if (q.isNotEmpty()) {
                        showSearchDialog = false
                        manualOverride = true
                        scope.launch {
                            val found = geocodeCity(q)
                            if (found != null) {
                                coords = found.second to found.third
                                state = state.copy(cityName = found.first, regionName = found.fourth, isLoading = true)
                                loadWeatherAndSyncWidget(found.first, found.fourth, found.second, found.third)
                            }
                        }
                    }
                }) { Text("Знайти", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showSearchDialog = false }) { Text("Скасувати") }
            }
        )
    }
}

@Composable
fun FrogDynamicScene(weatherCode: Int, isDay: Boolean, temp: Int, windSpeed: Int) {
    val isRainOrDrizzle = weatherCode in listOf(51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82, 95, 96, 99)
    val isSnow = weatherCode in listOf(71, 73, 75, 77, 85, 86) || temp <= -1
    val isCloudy = weatherCode in listOf(1, 2, 3, 45, 48) || isRainOrDrizzle || isSnow
    val isCold = temp in 0..8 || windSpeed >= 18
    val isSunnyClear = isDay && !isCloudy && !isRainOrDrizzle && !isSnow

    val skyColors = when {
        !isDay && isRainOrDrizzle -> listOf(Color(0xFF0B1120), Color(0xFF1E293B), Color(0xFF0F172A))
        !isDay -> listOf(Color(0xFF091224), Color(0xFF162447), Color(0xFF1F4068))
        isRainOrDrizzle -> listOf(Color(0xFF334155), Color(0xFF475569), Color(0xFF3B4954))
        isSnow -> listOf(Color(0xFF475569), Color(0xFF64748B), Color(0xFF526373))
        isCloudy -> listOf(Color(0xFF3B5998), Color(0xFF5B86E5).copy(alpha = 0.65f), Color(0xFF3D6B66))
        else -> listOf(Color(0xFF1D70B8), Color(0xFF38A3D1), Color(0xFF4EA885))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(176.dp)
            .clip(RoundedCornerShape(18.dp))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Небо
            drawRect(brush = Brush.verticalGradient(skyColors))

            // День (сонце) або Ніч (місяць і зірки)
            if (!isDay) {
                drawCircle(color = Color(0xFFFFF3B0), radius = 21f, center = Offset(w * 0.82f, h * 0.23f))
                drawCircle(color = Color(0xFF0B1528), radius = 17f, center = Offset(w * 0.85f, h * 0.20f))
                listOf(
                    Offset(w * 0.14f, h * 0.18f),
                    Offset(w * 0.28f, h * 0.13f),
                    Offset(w * 0.44f, h * 0.22f),
                    Offset(w * 0.63f, h * 0.15f),
                    Offset(w * 0.72f, h * 0.28f)
                ).forEach { star ->
                    drawCircle(color = Color(0xFFFFF8D6), radius = 2.6f, center = star)
                }
            } else if (!isRainOrDrizzle && !isSnow) {
                drawCircle(
                    color = Color(0xFFFFD166).copy(alpha = 0.28f),
                    radius = 40f,
                    center = Offset(w * 0.82f, h * 0.23f)
                )
                drawCircle(
                    color = Color(0xFFFFCA3A),
                    radius = 23f,
                    center = Offset(w * 0.82f, h * 0.23f)
                )
            }

            // Хмари
            if (isCloudy) {
                val cloudColor = if (isDay) Color(0xFFE2E8F0).copy(alpha = 0.88f) else Color(0xFF64748B).copy(alpha = 0.85f)
                drawCircle(color = cloudColor, radius = 26f, center = Offset(w * 0.20f, h * 0.24f))
                drawCircle(color = cloudColor, radius = 32f, center = Offset(w * 0.27f, h * 0.21f))
                drawCircle(color = cloudColor, radius = 22f, center = Offset(w * 0.34f, h * 0.26f))
            }

            // Пагорби та земля (без жодних грибків)
            drawCircle(
                color = if (isSnow) Color(0xFF94A3B8) else if (isDay) Color(0xFF3A7D5C) else Color(0xFF1A3636),
                radius = w * 0.46f,
                center = Offset(w * 0.20f, h * 1.02f)
            )
            drawCircle(
                color = if (isSnow) Color(0xFFCBD5E1) else if (isDay) Color(0xFF2D6A4F) else Color(0xFF142B2B),
                radius = w * 0.52f,
                center = Offset(w * 0.80f, h * 1.04f)
            )
            drawRect(
                color = if (isSnow) Color(0xFFE2E8F0) else if (isDay) Color(0xFF23533E) else Color(0xFF0F2022),
                topLeft = Offset(0f, h * 0.80f),
                size = Size(w, h * 0.20f)
            )

            // Жабка по центру
            val frogX = w * 0.50f
            val frogY = h * 0.67f

            // Лапки жабки
            drawOval(
                color = Color(0xFF52B788),
                topLeft = Offset(frogX - 38f, frogY + 14f),
                size = Size(24f, 13f)
            )
            drawOval(
                color = Color(0xFF52B788),
                topLeft = Offset(frogX + 14f, frogY + 14f),
                size = Size(24f, 13f)
            )

            // Тулуб жабки
            drawOval(
                color = Color(0xFF74C67A),
                topLeft = Offset(frogX - 28f, frogY - 8f),
                size = Size(56f, 36f)
            )

            // Одяг залежно від погоди
            when {
                isRainOrDrizzle -> {
                    // Жовтий дощовик
                    drawRoundRect(
                        color = Color(0xFFFFB703),
                        topLeft = Offset(frogX - 27f, frogY - 2f),
                        size = Size(54f, 27f),
                        cornerRadius = CornerRadius(14f, 14f)
                    )
                    drawLine(
                        color = Color(0xFFFB8500),
                        start = Offset(frogX, frogY - 1f),
                        end = Offset(frogX, frogY + 24f),
                        strokeWidth = 3f
                    )
                }
                isSnow -> {
                    // Теплий зимовий светр і червоний шарф
                    drawRoundRect(
                        color = Color(0xFF3A86FF),
                        topLeft = Offset(frogX - 27f, frogY - 2f),
                        size = Size(54f, 27f),
                        cornerRadius = CornerRadius(14f, 14f)
                    )
                    drawRoundRect(
                        color = Color(0xFFE63946),
                        topLeft = Offset(frogX - 25f, frogY - 7f),
                        size = Size(50f, 10f),
                        cornerRadius = CornerRadius(6f, 6f)
                    )
                    drawRoundRect(
                        color = Color(0xFFE63946),
                        topLeft = Offset(frogX + 9f, frogY - 2f),
                        size = Size(10f, 22f),
                        cornerRadius = CornerRadius(4f, 4f)
                    )
                }
                isCold -> {
                    // Теплий светр і шарфик у вітер/прохолоду
                    drawRoundRect(
                        color = Color(0xFFE07A5F),
                        topLeft = Offset(frogX - 27f, frogY - 2f),
                        size = Size(54f, 27f),
                        cornerRadius = CornerRadius(14f, 14f)
                    )
                    drawRoundRect(
                        color = Color(0xFFF2CC8F),
                        topLeft = Offset(frogX - 24f, frogY - 6f),
                        size = Size(48f, 8f),
                        cornerRadius = CornerRadius(5f, 5f)
                    )
                }
                else -> {
                    // Світлий животик у теплу погоду
                    drawOval(
                        color = Color(0xFFB7E4C7),
                        topLeft = Offset(frogX - 18f, frogY + 1f),
                        size = Size(36f, 22f)
                    )
                }
            }

            // Голова та очі жабки
            drawOval(
                color = Color(0xFF74C67A),
                topLeft = Offset(frogX - 26f, frogY - 26f),
                size = Size(52f, 28f)
            )
            drawCircle(color = Color(0xFFF4978E).copy(alpha = 0.85f), radius = 4.5f, center = Offset(frogX - 15f, frogY - 10f))
            drawCircle(color = Color(0xFFF4978E).copy(alpha = 0.85f), radius = 4.5f, center = Offset(frogX + 15f, frogY - 10f))

            drawCircle(color = Color(0xFF74C67A), radius = 9.5f, center = Offset(frogX - 13f, frogY - 27f))
            drawCircle(color = Color(0xFF74C67A), radius = 9.5f, center = Offset(frogX + 13f, frogY - 27f))
            drawCircle(color = Color.White, radius = 6f, center = Offset(frogX - 13f, frogY - 27f))
            drawCircle(color = Color.White, radius = 6f, center = Offset(frogX + 13f, frogY - 27f))
            drawCircle(color = Color(0xFF14181A), radius = 3f, center = Offset(frogX - 12f, frogY - 27f))
            drawCircle(color = Color(0xFF14181A), radius = 3f, center = Offset(frogX + 14f, frogY - 27f))

            // Посмішка
            drawArc(
                color = Color(0xFF1B4332),
                startAngle = 15f,
                sweepAngle = 150f,
                useCenter = false,
                topLeft = Offset(frogX - 10f, frogY - 16f),
                size = Size(20f, 10f),
                style = Stroke(width = 2.6f, cap = StrokeCap.Round)
            )

            // Головний убір / аксесуари під погоду
            if (isSnow) {
                // В'язана шапочка з помпоном
                drawArc(
                    color = Color(0xFFE63946),
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = true,
                    topLeft = Offset(frogX - 16f, frogY - 46f),
                    size = Size(32f, 22f)
                )
                drawCircle(color = Color.White, radius = 5f, center = Offset(frogX, frogY - 48f))
            } else if (isSunnyClear) {
                // Сонцезахисні окуляри у ясну сонячну погоду
                drawRoundRect(
                    color = Color(0xFF111827),
                    topLeft = Offset(frogX - 21f, frogY - 32f),
                    size = Size(16f, 10f),
                    cornerRadius = CornerRadius(3f, 3f)
                )
                drawRoundRect(
                    color = Color(0xFF111827),
                    topLeft = Offset(frogX + 5f, frogY - 32f),
                    size = Size(16f, 10f),
                    cornerRadius = CornerRadius(3f, 3f)
                )
                drawLine(
                    color = Color(0xFF111827),
                    start = Offset(frogX - 5f, frogY - 28f),
                    end = Offset(frogX + 5f, frogY - 28f),
                    strokeWidth = 3f
                )
            }

            // Парасолька і краплі дощу
            if (isRainOrDrizzle) {
                drawArc(
                    color = Color(0xFFEF476F),
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = true,
                    topLeft = Offset(frogX - 42f, frogY - 68f),
                    size = Size(84f, 42f)
                )
                drawLine(
                    color = Color(0xFFE2E8F0),
                    start = Offset(frogX, frogY - 47f),
                    end = Offset(frogX, frogY - 16f),
                    strokeWidth = 3.2f
                )
                listOf(
                    Offset(w * 0.14f, h * 0.34f),
                    Offset(w * 0.24f, h * 0.56f),
                    Offset(w * 0.33f, h * 0.42f),
                    Offset(w * 0.68f, h * 0.36f),
                    Offset(w * 0.78f, h * 0.58f),
                    Offset(w * 0.87f, h * 0.40f)
                ).forEach { drop ->
                    drawLine(
                        color = Color(0xFF90CDF4),
                        start = drop,
                        end = Offset(drop.x - 5f, drop.y + 14f),
                        strokeWidth = 2.8f,
                        cap = StrokeCap.Round
                    )
                }
            }

            // Сніжинки навколо
            if (isSnow) {
                listOf(
                    Offset(w * 0.15f, h * 0.28f),
                    Offset(w * 0.26f, h * 0.50f),
                    Offset(w * 0.36f, h * 0.34f),
                    Offset(w * 0.65f, h * 0.30f),
                    Offset(w * 0.76f, h * 0.52f),
                    Offset(w * 0.86f, h * 0.36f)
                ).forEach { flake ->
                    drawCircle(color = Color.White, radius = 4.2f, center = flake)
                }
            }
        }
    }
}

@Composable
fun ExpandableWeatherCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    summary: String,
    cardColor: Color,
    initiallyExpanded: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(cardColor)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF34373D)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = Color(0xFFA8C7FA), modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(10.dp))
                Text(summary, color = Color(0xFFE4E7EC), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Icon(
                imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = Color(0xFFD0D5DD)
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(top = 12.dp)) {
                content()
            }
        }
    }
}

@Composable
fun HumidityChart(hours: List<HourlyItem>, accentColor: Color) {
    if (hours.isEmpty()) return
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            hours.forEach { h ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text("${h.humidity}%", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(vertical = 6.dp)
        ) {
            val stepX = size.width / hours.size
            val points = hours.mapIndexed { i, h ->
                val x = stepX * i + stepX / 2f
                val y = size.height * (1f - (h.humidity.coerceIn(0, 100) / 100f))
                Offset(x, y)
            }
            if (points.size > 1) {
                val path = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    for (i in 0 until points.size - 1) {
                        val midX = (points[i].x + points[i + 1].x) / 2f
                        cubicTo(midX, points[i].y, midX, points[i + 1].y, points[i + 1].x, points[i + 1].y)
                    }
                }
                val fillPath = Path().apply {
                    addPath(path)
                    lineTo(points.last().x, size.height)
                    lineTo(points.first().x, size.height)
                    close()
                }
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(listOf(accentColor.copy(alpha = 0.35f), Color.Transparent))
                )
                drawPath(path = path, color = accentColor, style = Stroke(width = 2.5.dp.toPx()))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            hours.forEach { h ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(h.timeLabel, color = Color(0xFF98A2B3), fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
fun WeatherCodeIcon(code: Int, isDay: Boolean, size: Int) {
    val icon = when (code) {
        0 -> if (isDay) Icons.Rounded.WbSunny else Icons.Rounded.NightsStay
        1, 2 -> if (isDay) Icons.Rounded.WbCloudy else Icons.Rounded.NightsStay
        3, 45, 48 -> Icons.Rounded.Cloud
        in 51..67, in 80..82 -> Icons.Rounded.Grain
        in 71..77, in 85..86 -> Icons.Rounded.AcUnit
        else -> Icons.Rounded.Thunderstorm
    }
    val tint = when {
        code in listOf(0, 1, 2) && isDay -> Color(0xFFFDD663)
        code in listOf(0, 1, 2) && !isDay -> Color(0xFFA8C7FA)
        code in listOf(3, 45, 48) -> Color(0xFFBDC1C6)
        code in 51..67 || code in 80..82 -> Color(0xFF8AB4F8)
        code in 71..77 || code in 85..86 -> Color(0xFFD2E3FC)
        else -> Color(0xFFF28B82)
    }
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size.dp))
}

fun weatherDescription(code: Int): String = when (code) {
    0 -> "Ясно"
    1, 2 -> "Мінлива хмарність"
    3 -> "Хмарно"
    45, 48 -> "Туман"
    in 51..67, in 80..82 -> "Дощ"
    in 71..77, in 85..86 -> "Сніг"
    else -> "Гроза"
}

@SuppressLint("MissingPermission")
suspend fun getLastKnownOrSingleLocation(context: Context): Location? = withContext(Dispatchers.IO) {
    try {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        val candidates = providers.mapNotNull { provider ->
            try {
                if (lm.isProviderEnabled(provider) || provider == LocationManager.PASSIVE_PROVIDER) {
                    lm.getLastKnownLocation(provider)
                } else null
            } catch (_: Exception) {
                null
            }
        }
        candidates.maxByOrNull { it.time }
    } catch (_: Exception) {
        null
    }
}

suspend fun resolvePlaceName(context: Context, lat: Double, lon: Double): Pair<String, String> = withContext(Dispatchers.IO) {
    try {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            @Suppress("DEPRECATION")
            val geocoder = Geocoder(context, Locale.forLanguageTag("uk"))
            @Suppress("DEPRECATION")
            val list = geocoder.getFromLocation(lat, lon, 1)
            val addr = list?.firstOrNull()
            val city = addr?.locality ?: addr?.subAdminArea
            val region = addr?.adminArea ?: ""
            if (!city.isNullOrBlank()) return@withContext city to region
        }
    } catch (_: Exception) {
    }

    try {
        val revUrl = "https://api.bigdatacloud.net/data/reverse-geocode-client?latitude=$lat&longitude=$lon&localityLanguage=uk"
        val json = JSONObject(URL(revUrl).readText())
        val city = json.optString("city").ifBlank { json.optString("locality") }.ifBlank { "Поточне місце" }
        val region = json.optString("principalSubdivision")
        city to region
    } catch (_: Exception) {
        "Хмельницький" to "Хмельницька область"
    }
}

data class CityMatch(val first: String, val second: Double, val third: Double, val fourth: String)

suspend fun geocodeCity(query: String): CityMatch? = withContext(Dispatchers.IO) {
    try {
        val url = "https://geocoding-api.open-meteo.com/v1/search?name=${java.net.URLEncoder.encode(query, "UTF-8")}&count=1&language=uk&format=json"
        val json = JSONObject(URL(url).readText())
        val results = json.optJSONArray("results") ?: return@withContext null
        if (results.length() == 0) return@withContext null
        val first = results.getJSONObject(0)
        CityMatch(
            first = first.getString("name"),
            second = first.getDouble("latitude"),
            third = first.getDouble("longitude"),
            fourth = first.optString("admin1", "")
        )
    } catch (_: Exception) {
        null
    }
}

suspend fun fetchWeather(city: String, region: String, lat: Double, lon: Double): WeatherState = withContext(Dispatchers.IO) {
    try {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,apparent_temperature,relative_humidity_2m,precipitation_probability,weather_code,wind_speed_10m,is_day" +
                "&hourly=temperature_2m,apparent_temperature,weather_code,is_day,precipitation_probability,precipitation,wind_speed_10m,wind_direction_10m,relative_humidity_2m" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min,wind_speed_10m_max" +
                "&timezone=auto&forecast_days=6"
        val json = JSONObject(URL(url).readText())

        val current = json.getJSONObject("current")
        val curTemp = current.getDouble("temperature_2m").roundToInt()
        val curFeels = current.optDouble("apparent_temperature", curTemp.toDouble()).roundToInt()
        val curCode = current.getInt("weather_code")
        val curIsDay = current.optInt("is_day", 1) == 1
        val curWind = current.optDouble("wind_speed_10m", 0.0).roundToInt()
        val curProb = current.optInt("precipitation_probability", 0)
        val curHum = current.optInt("relative_humidity_2m", 0)

        val hourlyObj = json.getJSONObject("hourly")
        val times = hourlyObj.getJSONArray("time")
        val temps = hourlyObj.getJSONArray("temperature_2m")
        val feelsArr = hourlyObj.getJSONArray("apparent_temperature")
        val codesArr = hourlyObj.getJSONArray("weather_code")
        val isDayArr = hourlyObj.optJSONArray("is_day")
        val probs = hourlyObj.getJSONArray("precipitation_probability")
        val mms = hourlyObj.getJSONArray("precipitation")
        val winds = hourlyObj.getJSONArray("wind_speed_10m")
        val dirs = hourlyObj.getJSONArray("wind_direction_10m")
        val hums = hourlyObj.getJSONArray("relative_humidity_2m")

        val nowDateStr = LocalDate.now().toString()
        val nowHour = LocalDateTime.now().hour
        val hourlyList = mutableListOf<HourlyItem>()
        for (i in 0 until times.length()) {
            val rawTime = times.getString(i)
            val datePart = rawTime.substringBefore("T")
            val hourStr = rawTime.substringAfter("T")
            val hourInt = hourStr.substringBefore(":").toIntOrNull() ?: 0
            val isCur = (datePart == nowDateStr && hourInt == nowHour)
            hourlyList.add(
                HourlyItem(
                    dateStr = datePart,
                    hourInt = hourInt,
                    timeLabel = hourStr,
                    temp = temps.getDouble(i).roundToInt(),
                    apparentTemp = feelsArr.getDouble(i).roundToInt(),
                    weatherCode = codesArr.getInt(i),
                    isDay = (isDayArr?.optInt(i, 1) ?: 1) == 1,
                    precipProb = probs.optInt(i, 0),
                    precipMm = mms.optDouble(i, 0.0),
                    windSpeed = winds.getDouble(i).roundToInt(),
                    windDir = dirs.getDouble(i).toFloat(),
                    humidity = hums.getInt(i),
                    isCurrentHour = isCur
                )
            )
        }

        val dailyObj = json.getJSONObject("daily")
        val dTimes = dailyObj.getJSONArray("time")
        val dCodes = dailyObj.getJSONArray("weather_code")
        val dMax = dailyObj.getJSONArray("temperature_2m_max")
        val dMin = dailyObj.getJSONArray("temperature_2m_min")
        val dWindMax = dailyObj.getJSONArray("wind_speed_10m_max")
        val ukLocale = Locale.forLanguageTag("uk")

        val dailyList = mutableListOf<DailyItem>()
        for (i in 0 until dTimes.length()) {
            val rawDate = dTimes.getString(i)
            val date = LocalDate.parse(rawDate)
            val dayShort = date.dayOfWeek.getDisplayName(TextStyle.SHORT, ukLocale).lowercase()
            dailyList.add(
                DailyItem(
                    dateStr = rawDate,
                    dayName = dayShort,
                    maxTemp = dMax.getDouble(i).roundToInt(),
                    minTemp = dMin.getDouble(i).roundToInt(),
                    maxWind = dWindMax.optDouble(i, 0.0).roundToInt(),
                    weatherCode = dCodes.getInt(i)
                )
            )
        }

        WeatherState(
            cityName = city,
            regionName = region,
            currentTemp = curTemp,
            feelsLike = curFeels,
            currentCode = curCode,
            isDay = curIsDay,
            currentWind = curWind,
            currentPrecipProb = curProb,
            currentHumidity = curHum,
            hourly = hourlyList,
            daily = dailyList,
            isLoading = false
        )
    } catch (e: Exception) {
        WeatherState(cityName = city, regionName = region, isLoading = false, error = e.localizedMessage ?: "Помилка мережі")
    }
}