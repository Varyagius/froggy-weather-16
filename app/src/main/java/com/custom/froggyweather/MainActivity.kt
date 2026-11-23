package com.custom.froggyweather

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val weatherCode: Int
)

data class WeatherState(
    val cityName: String = "Хмельницький",
    val currentTemp: Int = 0,
    val currentCode: Int = 0,
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
    var state by remember { mutableStateOf(WeatherState()) }
    var selectedDayIndex by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var showSearchDialog by remember { mutableStateOf(false) }
    var coords by remember { mutableStateOf(49.4229 to 26.9871) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(coords, state.cityName) {
        state = state.copy(isLoading = true, error = null)
        state = fetchWeather(state.cityName, coords.first, coords.second)
    }

    val bgDark = Color(0xFF1F2023)
    val cardDark = Color(0xFF2B2D31)
    val accentBlue = Color(0xFF8AB4F8)

    Scaffold(
        containerColor = bgDark,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { showSearchDialog = true }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.LocationOn, contentDescription = null, tint = accentBlue, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(state.cityName, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                    Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = Color.LightGray)
                }
                IconButton(onClick = {
                    scope.launch {
                        state = state.copy(isLoading = true)
                        state = fetchWeather(state.cityName, coords.first, coords.second)
                    }
                }) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Оновити", tint = Color.LightGray)
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
            val dayHours = state.hourly.drop(selectedDayIndex * 24).take(7)
            val currentHour = dayHours.firstOrNull()

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    FroggyBannerCard(
                        temp = if (selectedDayIndex == 0) state.currentTemp else state.daily.getOrNull(selectedDayIndex)?.maxTemp ?: 0,
                        code = if (selectedDayIndex == 0) state.currentCode else state.daily.getOrNull(selectedDayIndex)?.weatherCode ?: 0
                    )
                }

                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(state.daily) { index, day ->
                            val isSelected = index == selectedDayIndex
                            Column(
                                modifier = Modifier
                                    .width(68.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (isSelected) Color(0xFF33363B) else cardDark)
                                    .border(
                                        width = if (isSelected) 1.5.dp else 0.dp,
                                        color = if (isSelected) Color(0xFF9AA0A6) else Color.Transparent,
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    .clickable { selectedDayIndex = index }
                                    .padding(vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(day.dayName, color = Color.LightGray, fontSize = 13.sp)
                                Spacer(Modifier.height(6.dp))
                                WeatherCodeIcon(day.weatherCode, size = 24)
                                Spacer(Modifier.height(6.dp))
                                Text("${day.maxTemp}°/${day.minTemp}°", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }

                item {
                    val totalMm = dayHours.sumOf { it.precipMm }
                    SectionCard(
                        icon = Icons.Rounded.WaterDrop,
                        title = "Опади",
                        headerValue = "${currentHour?.precipProb ?: 0}%",
                        subtitle = "Кількість опадів у вікні ${String.format(Locale.US, "%.1f", totalMm)} мм",
                        cardColor = cardDark
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            dayHours.forEach { h ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                    Text(
                                        if (h.precipMm == 0.0) "0,0" else if (h.precipMm < 0.25) "<0,25" else String.format(Locale.US, "%.1f", h.precipMm),
                                        color = Color.LightGray,
                                        fontSize = 11.sp
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    Box(
                                        modifier = Modifier
                                            .width(26.dp)
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(if (h.precipProb > 0) accentBlue else Color(0xFF5F6368))
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text("${h.precipProb}%", color = accentBlue, fontSize = 11.sp)
                                    Text(h.timeLabel, color = Color.Gray, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                item {
                    val maxWind = dayHours.maxOfOrNull { it.windSpeed } ?: 0
                    SectionCard(
                        icon = Icons.Rounded.Air,
                        title = "Вітер",
                        headerValue = "${currentHour?.windSpeed ?: 0} км/год",
                        subtitle = "Найвищий показник у вікні $maxWind км/год",
                        cardColor = cardDark
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            dayHours.forEach { h ->
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
                                    Text(h.timeLabel, color = Color.Gray, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                item {
                    val avgHum = if (dayHours.isNotEmpty()) dayHours.map { it.humidity }.average().roundToInt() else 0
                    SectionCard(
                        icon = Icons.Rounded.Opacity,
                        title = "Вологість",
                        headerValue = "${currentHour?.humidity ?: 0}%",
                        subtitle = "У середньому $avgHum%",
                        cardColor = cardDark
                    ) {
                        HumidityChart(dayHours, accentBlue)
                    }
                }

                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    if (showSearchDialog) {
        AlertDialog(
            onDismissRequest = { showSearchDialog = false },
            title = { Text("Вибір міста") },
            text = {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Наприклад: Київ, Львів, Одеса") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val q = searchQuery.trim()
                    if (q.isNotEmpty()) {
                        showSearchDialog = false
                        scope.launch {
                            val found = geocodeCity(q)
                            if (found != null) {
                                coords = found.second to found.third
                                state = state.copy(cityName = found.first)
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
fun FroggyBannerCard(temp: Int, code: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF3E515B), Color(0xFF567465), Color(0xFF3D4E59))
                )
            )
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("$temp°", color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.Light)
                Text(weatherDescription(code), color = Color(0xFFE8EAED), fontSize = 15.sp)
            }
            Text("🐸", fontSize = 54.sp)
        }
    }
}

@Composable
fun SectionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    headerValue: String,
    subtitle: String,
    cardColor: Color,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(cardColor)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF383B40)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Color(0xFF8AB4F8), modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text("$title  •  $headerValue", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(12.dp))
        Text(subtitle, color = Color(0xFFBDC1C6), fontSize = 13.sp)
        Spacer(Modifier.height(14.dp))
        content()
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
                    Text(h.timeLabel, color = Color.Gray, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
fun WeatherCodeIcon(code: Int, size: Int) {
    val icon = when (code) {
        0 -> Icons.Rounded.WbSunny
        1, 2 -> Icons.Rounded.WbCloudy
        3, 45, 48 -> Icons.Rounded.Cloud
        in 51..67, in 80..82 -> Icons.Rounded.Grain
        in 71..77, in 85..86 -> Icons.Rounded.AcUnit
        else -> Icons.Rounded.Thunderstorm
    }
    val tint = when (code) {
        0, 1, 2 -> Color(0xFFFDD663)
        3, 45, 48 -> Color(0xFFBDC1C6)
        in 51..67, in 80..82 -> Color(0xFF8AB4F8)
        in 71..77, in 85..86 -> Color(0xFFD2E3FC)
        else -> Color(0xFFF28B82)
    }
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size.dp))
}

suspend fun geocodeCity(query: String): Triple<String, Double, Double>? = withContext(Dispatchers.IO) {
    try {
        val url = "https://geocoding-api.open-meteo.com/v1/search?name=${java.net.URLEncoder.encode(query, "UTF-8")}&count=1&language=uk&format=json"
        val json = JSONObject(URL(url).readText())
        val results = json.optJSONArray("results") ?: return@withContext null
        if (results.length() == 0) return@withContext null
        val first = results.getJSONObject(0)
        Triple(first.getString("name"), first.getDouble("latitude"), first.getDouble("longitude"))
    } catch (_: Exception) {
        null
    }
}

suspend fun fetchWeather(city: String, lat: Double, lon: Double): WeatherState = withContext(Dispatchers.IO) {
    try {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,weather_code" +
                "&hourly=temperature_2m,precipitation_probability,precipitation,wind_speed_10m,wind_direction_10m,relative_humidity_2m" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min" +
                "&timezone=auto&forecast_days=6"
        val json = JSONObject(URL(url).readText())

        val current = json.getJSONObject("current")
        val curTemp = current.getDouble("temperature_2m").roundToInt()
        val curCode = current.getInt("weather_code")

        val hourlyObj = json.getJSONObject("hourly")
        val times = hourlyObj.getJSONArray("time")
        val temps = hourlyObj.getJSONArray("temperature_2m")
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
                    weatherCode = dCodes.getInt(i)
                )
            )
        }

        WeatherState(
            cityName = city,
            currentTemp = curTemp,
            currentCode = curCode,
            hourly = hourlyList,
            daily = dailyList,
            isLoading = false
        )
    } catch (e: Exception) {
        WeatherState(cityName = city, isLoading = false, error = e.localizedMessage ?: "Помилка мережі")
    }
}
