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

data class HourlyItem(
    val timeLabel: String,
    val temp: Int,
    val apparentTemp: Int,
    val weatherCode: Int,
    val isDay: Boolean,
    val precipProb: Int,
    val precipMm: Double,
    val windSpeed: Int,
    val windDir: Float,
    val humidity: Int
)

data class DailyItem(
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

const val APP_VERSION_LABEL = "v4 - 1.3.0 (code 4)"

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
    var searchQuery by remember { mutableStateOf("") }
    var showSearchDialog by remember { mutableStateOf(false) }
    var coords by remember { mutableStateOf(49.4229 to 26.9871) }
    var manualOverride by remember { mutableStateOf(false) }
    var autoLocationStatus by remember { mutableStateOf("Автогеолокація") }

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
            state = fetchWeather(place.first, place.second, loc.latitude, loc.longitude)
        } else {
            state = fetchWeather(state.cityName, state.regionName, coords.first, coords.second)
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
                state = fetchWeather(state.cityName, state.regionName, coords.first, coords.second)
            }
        }
    }

    LaunchedEffect(Unit) {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (hasFine || hasCoarse) {
            detectAndLoadCurrentLocation()
        } else {
            state = fetchWeather(state.cityName, state.regionName, coords.first, coords.second)
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    val bgDark = Color(0xFF1A1B1E)
    val cardDark = Color(0xFF26282C)
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
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (state.regionName.isNotEmpty()) "${state.cityName}, ${state.regionName}" else state.cityName,
                        color = Color(0xFFE3E3E3),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Icon(
                        Icons.Rounded.ArrowDropDown,
                        contentDescription = null,
                        tint = Color(0xFFC4C7C5),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Кнопка повернення до автоматичного місцеположення
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
                        state = fetchWeather(state.cityName, state.regionName, coords.first, coords.second)
                    }
                }) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Оновити", tint = Color(0xFFC4C7C5))
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
                Text("Помилка: ${state.error}", color = Color(0xFFF28B82))
            }
        } else {
            val dayHours = state.hourly.drop(selectedDayIndex * 24).take(8)
            val activeDay = state.daily.getOrNull(selectedDayIndex)
            val displayTemp = if (selectedDayIndex == 0) state.currentTemp else activeDay?.maxTemp ?: 0
            val displayFeels = if (selectedDayIndex == 0) state.feelsLike else (activeDay?.maxTemp ?: 0) - 2
            val displayCode = if (selectedDayIndex == 0) state.currentCode else activeDay?.weatherCode ?: 0
            val displayIsDay = if (selectedDayIndex == 0) state.isDay else true
            val maxWindToday = if (selectedDayIndex == 0) maxOf(state.currentWind, dayHours.maxOfOrNull { it.windSpeed } ?: 0) else activeDay?.maxWind ?: 0

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
                                text = if (selectedDayIndex == 0) "Зараз" else activeDay?.dayName?.uppercase() ?: "Прогноз",
                                color = Color(0xFFC4C7C5),
                                fontSize = 15.sp
                            )
                            Text(
                                text = if (displayIsDay) "☀️ День" else "🌙 Ніч",
                                color = Color(0xFF9AA0A6),
                                fontSize = 12.sp
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "$displayTemp°",
                                    color = Color.White,
                                    fontSize = 46.sp,
                                    fontWeight = FontWeight.Normal
                                )
                                Spacer(Modifier.width(8.dp))
                                WeatherCodeIcon(displayCode, isDay = displayIsDay, size = 34)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = weatherDescription(displayCode),
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Відчувається як $displayFeels°",
                                    color = Color(0xFFC4C7C5),
                                    fontSize = 13.sp
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
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Помірний ризик вітру · до $maxWindToday км/год",
                                    color = Color(0xFFF3EAE8),
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            itemsIndexed(dayHours) { _, h ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.width(46.dp)
                                ) {
                                    Text("${h.temp}°", color = Color(0xFFE3E3E3), fontSize = 14.sp)
                                    Spacer(Modifier.height(6.dp))
                                    WeatherCodeIcon(h.weatherCode, isDay = h.isDay, size = 24)
                                    if (h.precipProb > 0) {
                                        Text("${h.precipProb}%", color = accentBlue, fontSize = 11.sp)
                                    } else {
                                        Spacer(Modifier.height(15.dp))
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(h.timeLabel, color = Color(0xFFC4C7C5), fontSize = 12.sp, maxLines = 1)
                                }
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        FrogDynamicScene(weatherCode = displayCode, isDay = displayIsDay)
                    }
                }

                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(state.daily) { index, day ->
                            val isSelected = index == selectedDayIndex
                            Column(
                                modifier = Modifier
                                    .width(66.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (isSelected) Color(0xFF32353B) else cardDark)
                                    .border(
                                        width = if (isSelected) 1.5.dp else 0.dp,
                                        color = if (isSelected) Color(0xFFA8C7FA) else Color.Transparent,
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .clickable { selectedDayIndex = index }
                                    .padding(vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(day.dayName, color = Color(0xFFE3E3E3), fontSize = 13.sp)
                                Spacer(Modifier.height(8.dp))
                                WeatherCodeIcon(day.weatherCode, isDay = true, size = 24)
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "${day.maxTemp}°/${day.minTemp}°",
                                    color = Color(0xFFC4C7C5),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                item {
                    val probVal = if (selectedDayIndex == 0) state.currentPrecipProb else (dayHours.maxOfOrNull { it.precipProb } ?: 0)
                    val totalMm = dayHours.sumOf { it.precipMm }
                    ExpandableWeatherCard(
                        icon = Icons.Rounded.WaterDrop,
                        title = "Опади",
                        summary = "$probVal%",
                        cardColor = cardDark,
                        initiallyExpanded = true
                    ) {
                        Text(
                            text = "Кількість опадів у вікні ${String.format(Locale.US, "%.1f", totalMm)} мм",
                            color = Color(0xFFC4C7C5),
                            fontSize = 13.sp
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            dayHours.take(7).forEach { h ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (h.precipMm == 0.0) "0,0" else String.format(Locale.US, "%.1f", h.precipMm),
                                        color = Color(0xFFE3E3E3),
                                        fontSize = 11.sp
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .width(24.dp)
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(if (h.precipProb > 0) accentBlue else Color(0xFF5F6368))
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text("${h.precipProb}%", color = accentBlue, fontSize = 11.sp)
                                    Text(h.timeLabel, color = Color(0xFF9AA0A6), fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                item {
                    val windVal = if (selectedDayIndex == 0) state.currentWind else maxWindToday
                    ExpandableWeatherCard(
                        icon = Icons.Rounded.Air,
                        title = "Вітер",
                        summary = "$windVal км/год",
                        cardColor = cardDark,
                        initiallyExpanded = false
                    ) {
                        Text(
                            text = "Найвищий показник у вікні $maxWindToday км/год",
                            color = Color(0xFFC4C7C5),
                            fontSize = 13.sp
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            dayHours.take(7).forEach { h ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                    Text("${h.windSpeed}", color = Color.White, fontSize = 13.sp)
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
                                    Text(h.timeLabel, color = Color(0xFF9AA0A6), fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                item {
                    val humVal = if (selectedDayIndex == 0) state.currentHumidity else (if (dayHours.isNotEmpty()) dayHours.map { it.humidity }.average().roundToInt() else 0)
                    val avgHum = if (dayHours.isNotEmpty()) dayHours.map { it.humidity }.average().roundToInt() else humVal
                    ExpandableWeatherCard(
                        icon = Icons.Rounded.Opacity,
                        title = "Вологість",
                        summary = "$humVal%",
                        cardColor = cardDark,
                        initiallyExpanded = false
                    ) {
                        Text("У середньому $avgHum%", color = Color(0xFFC4C7C5), fontSize = 13.sp)
                        Spacer(Modifier.height(12.dp))
                        HumidityChart(dayHours.take(7), accentBlue)
                    }
                }

                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    if (showSearchDialog) {
        AlertDialog(
            onDismissRequest = { showSearchDialog = false },
            title = { Text("Місто або автогеолокація") },
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
                        Text("Визначити автоматично (GPS)")
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
                                state = fetchWeather(found.first, found.fourth, found.second, found.third)
                            }
                        }
                    }
                }) { Text("Знайти") }
            },
            dismissButton = {
                TextButton(onClick = { showSearchDialog = false }) { Text("Скасувати") }
            }
        )
    }
}

@Composable
fun FrogDynamicScene(weatherCode: Int, isDay: Boolean) {
    val isRainOrDrizzle = weatherCode in listOf(51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82, 95, 96, 99)
    val isSnow = weatherCode in listOf(71, 73, 75, 77, 85, 86)
    val isCloudy = weatherCode in listOf(1, 2, 3, 45, 48) || isRainOrDrizzle || isSnow

    val skyColors = when {
        !isDay && isRainOrDrizzle -> listOf(Color(0xFF0F172A), Color(0xFF1E293B), Color(0xFF17202A))
        !isDay -> listOf(Color(0xFF0D1B2A), Color(0xFF1B263B), Color(0xFF243447))
        isRainOrDrizzle -> listOf(Color(0xFF334155), Color(0xFF475569), Color(0xFF3B4954))
        isSnow -> listOf(Color(0xFF475569), Color(0xFF64748B), Color(0xFF4B5C6B))
        isCloudy -> listOf(Color(0xFF3A506B), Color(0xFF5C7AEA).copy(alpha = 0.45f), Color(0xFF3D5A5C))
        else -> listOf(Color(0xFF2B6CB0), Color(0xFF4A90B8), Color(0xFF4E8D7C))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(152.dp)
            .clip(RoundedCornerShape(18.dp))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            drawRect(brush = Brush.verticalGradient(skyColors))

            if (!isDay) {
                // Місяць і зірочки вночі
                drawCircle(color = Color(0xFFFFF3B0), radius = 18f, center = Offset(w * 0.82f, h * 0.24f))
                drawCircle(color = Color(0xFF0D1B2A), radius = 15f, center = Offset(w * 0.85f, h * 0.21f))
                listOf(
                    Offset(w * 0.14f, h * 0.20f),
                    Offset(w * 0.28f, h * 0.14f),
                    Offset(w * 0.42f, h * 0.25f),
                    Offset(w * 0.65f, h * 0.17f)
                ).forEach { star ->
                    drawCircle(color = Color(0xFFFFF8D6), radius = 2.4f, center = star)
                }
            } else if (!isRainOrDrizzle && !isSnow) {
                // Сонце вдень
                drawCircle(
                    color = Color(0xFFFFD166).copy(alpha = 0.28f),
                    radius = 34f,
                    center = Offset(w * 0.83f, h * 0.23f)
                )
                drawCircle(
                    color = Color(0xFFFFCA3A),
                    radius = 20f,
                    center = Offset(w * 0.83f, h * 0.23f)
                )
            }

            if (isCloudy) {
                val cloudColor = if (isDay) Color(0xFFE2E8F0).copy(alpha = 0.85f) else Color(0xFF64748B).copy(alpha = 0.8f)
                drawCircle(color = cloudColor, radius = 24f, center = Offset(w * 0.22f, h * 0.26f))
                drawCircle(color = cloudColor, radius = 28f, center = Offset(w * 0.28f, h * 0.23f))
                drawCircle(color = cloudColor, radius = 20f, center = Offset(w * 0.34f, h * 0.28f))
            }

            // Пагорби
            drawCircle(
                color = if (isDay) Color(0xFF3A6B5C) else Color(0xFF1B2E35),
                radius = w * 0.44f,
                center = Offset(w * 0.18f, h * 0.96f)
            )
            drawCircle(
                color = if (isDay) Color(0xFF2F5B4E) else Color(0xFF15242A),
                radius = w * 0.50f,
                center = Offset(w * 0.80f, h * 0.98f)
            )

            // Земля або сніговий покрив
            drawRect(
                color = if (isSnow) Color(0xFFCBD5E1) else if (isDay) Color(0xFF23473C) else Color(0xFF111C21),
                topLeft = Offset(0f, h * 0.78f),
                size = Size(w, h * 0.22f)
            )

            // Грибочок-будиночок
            val houseCenterX = w * 0.56f
            val stemWidth = w * 0.20f
            val stemHeight = h * 0.32f
            val stemTop = h * 0.47f

            drawRoundRect(
                color = Color(0xFFEDE4D3),
                topLeft = Offset(houseCenterX - stemWidth / 2f, stemTop),
                size = Size(stemWidth, stemHeight),
                cornerRadius = CornerRadius(18f, 18f)
            )

            // Віконце світиться яскравіше вночі або в негоду
            val windowColor = if (!isDay || isRainOrDrizzle || isSnow) Color(0xFFFFD166) else Color(0xFFFFF3B0)
            drawRoundRect(
                color = windowColor,
                topLeft = Offset(houseCenterX - stemWidth * 0.26f, stemTop + stemHeight * 0.22f),
                size = Size(stemWidth * 0.52f, stemHeight * 0.48f),
                cornerRadius = CornerRadius(12f, 12f)
            )

            drawArc(
                color = Color(0xFFE06D53),
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = true,
                topLeft = Offset(houseCenterX - stemWidth * 0.85f, stemTop - stemWidth * 0.65f),
                size = Size(stemWidth * 1.7f, stemWidth * 1.25f)
            )
            drawCircle(color = Color(0xFFFFF6E5), radius = 7f, center = Offset(houseCenterX - 20f, stemTop - 24f))
            drawCircle(color = Color(0xFFFFF6E5), radius = 9f, center = Offset(houseCenterX + 4f, stemTop - 35f))
            drawCircle(color = Color(0xFFFFF6E5), radius = 6f, center = Offset(houseCenterX + 24f, stemTop - 20f))

            // Жабеня Фроггі адаптується до погоди: парасолька в дощ, шарфик у сніг
            val frogX = w * 0.36f
            val frogY = h * 0.72f
            drawOval(
                color = Color(0xFF74C67A),
                topLeft = Offset(frogX - 22f, frogY - 15f),
                size = Size(44f, 30f)
            )
            // Щічки
            drawCircle(color = Color(0xFFF4978E).copy(alpha = 0.8f), radius = 4f, center = Offset(frogX - 11f, frogY - 1f))
            drawCircle(color = Color(0xFFF4978E).copy(alpha = 0.8f), radius = 4f, center = Offset(frogX + 11f, frogY - 1f))

            // Очі
            drawCircle(color = Color(0xFF74C67A), radius = 8f, center = Offset(frogX - 11f, frogY - 18f))
            drawCircle(color = Color(0xFF74C67A), radius = 8f, center = Offset(frogX + 11f, frogY - 18f))
            drawCircle(color = Color.White, radius = 5f, center = Offset(frogX - 11f, frogY - 18f))
            drawCircle(color = Color.White, radius = 5f, center = Offset(frogX + 11f, frogY - 18f))
            drawCircle(color = Color(0xFF14181A), radius = 2.6f, center = Offset(frogX - 10.5f, frogY - 18f))
            drawCircle(color = Color(0xFF14181A), radius = 2.6f, center = Offset(frogX + 11.5f, frogY - 18f))

            if (isSnow) {
                // Теплий шарфик у сніг
                drawRoundRect(
                    color = Color(0xFFE63946),
                    topLeft = Offset(frogX - 18f, frogY + 6f),
                    size = Size(36f, 7f),
                    cornerRadius = CornerRadius(6f, 6f)
                )
            }

            if (isRainOrDrizzle) {
                // Парасолька над жабеням у дощ
                drawArc(
                    color = Color(0xFFF4A261),
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = true,
                    topLeft = Offset(frogX - 32f, frogY - 46f),
                    size = Size(64f, 34f)
                )
                drawLine(
                    color = Color(0xFFE2E8F0),
                    start = Offset(frogX, frogY - 29f),
                    end = Offset(frogX, frogY - 12f),
                    strokeWidth = 3f
                )
                // Краплинки дощу
                listOf(
                    Offset(w * 0.15f, h * 0.42f),
                    Offset(w * 0.25f, h * 0.58f),
                    Offset(w * 0.72f, h * 0.36f),
                    Offset(w * 0.85f, h * 0.52f)
                ).forEach { drop ->
                    drawLine(
                        color = Color(0xFF8AB4F8),
                        start = drop,
                        end = Offset(drop.x - 4f, drop.y + 12f),
                        strokeWidth = 2.5f
                    )
                }
            }

            if (isSnow) {
                // Сніжинки
                listOf(
                    Offset(w * 0.16f, h * 0.32f),
                    Offset(w * 0.30f, h * 0.45f),
                    Offset(w * 0.68f, h * 0.28f),
                    Offset(w * 0.84f, h * 0.46f)
                ).forEach { flake ->
                    drawCircle(color = Color.White, radius = 4f, center = flake)
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
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF34373D)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = Color(0xFFA8C7FA), modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(10.dp))
                Text(summary, color = Color(0xFFC4C7C5), fontSize = 15.sp)
            }
            Icon(
                imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = Color(0xFFC4C7C5)
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
                    Text("${h.humidity}%", color = Color.White, fontSize = 12.sp)
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
                    Text(h.timeLabel, color = Color(0xFF9AA0A6), fontSize = 11.sp)
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
        val url = "https://geocoding-api.open-meteo.com/v1/search?name=&count=1&language=uk&format=json"
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

        val nowHour = LocalDateTime.now().hour
        val hourlyList = mutableListOf<HourlyItem>()
        for (i in nowHour until times.length()) {
            val rawTime = times.getString(i)
            val hourStr = rawTime.substringAfter("T")
            val label = if (i == nowHour) "Зараз" else hourStr
            hourlyList.add(
                HourlyItem(
                    timeLabel = label,
                    temp = temps.getDouble(i).roundToInt(),
                    apparentTemp = feelsArr.getDouble(i).roundToInt(),
                    weatherCode = codesArr.getInt(i),
                    isDay = (isDayArr?.optInt(i, 1) ?: 1) == 1,
                    precipProb = probs.optInt(i, 0),
                    precipMm = mms.optDouble(i, 0.0),
                    windSpeed = winds.getDouble(i).roundToInt(),
                    windDir = dirs.getDouble(i).toFloat(),
                    humidity = hums.getInt(i)
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
            val date = LocalDate.parse(dTimes.getString(i))
            val dayShort = date.dayOfWeek.getDisplayName(TextStyle.SHORT, ukLocale).lowercase()
            dailyList.add(
                DailyItem(
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
