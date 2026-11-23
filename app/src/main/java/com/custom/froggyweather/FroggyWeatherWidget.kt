package com.custom.froggyweather

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.URL
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

class FroggyWeatherWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
        refreshFromNetwork(context)
    }

    companion object {
        private const val PREFS_NAME = "froggy_widget_prefs"

        fun saveWeatherSnapshot(context: Context, city: String, temp: Int, weatherCode: Int, lat: Double, lon: Double) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putString("city", city)
                .putInt("temp", temp)
                .putInt("code", weatherCode)
                .putFloat("lat", lat.toFloat())
                .putFloat("lon", lon.toFloat())
                .apply()
            updateAllWidgets(context)
        }

        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, FroggyWeatherWidget::class.java))
            for (id in ids) {
                updateAppWidget(context, manager, id)
            }
        }

        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val city = prefs.getString("city", "Хмельницький") ?: "Хмельницький"
            val temp = prefs.getInt("temp", 1)
            val code = prefs.getInt("code", 61)

            val ukLocale = Locale.forLanguageTag("uk")
            val dateText = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", ukLocale))

            val iconRes = when (code) {
                0 -> R.drawable.ic_widget_sun
                1, 2, 3, 45, 48 -> R.drawable.ic_widget_cloud
                in 71..77, in 85..86 -> R.drawable.ic_widget_snow
                else -> R.drawable.ic_widget_rain
            }

            val views = RemoteViews(context.packageName, R.layout.widget_froggy_weather)
            views.setTextViewText(R.id.widget_date, dateText)
            views.setTextViewText(R.id.widget_city, city)
            views.setTextViewText(R.id.widget_temp, "${temp}C")
            views.setImageViewResource(R.id.widget_weather_icon, iconRes)

            val launchIntent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        fun refreshFromNetwork(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val lat = prefs.getFloat("lat", 49.4229f).toDouble()
            val lon = prefs.getFloat("lon", 26.9871f).toDouble()
            val city = prefs.getString("city", "Хмельницький") ?: "Хмельницький"

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code&timezone=auto"
                    val json = JSONObject(URL(url).readText())
                    val current = json.getJSONObject("current")
                    val temp = current.getDouble("temperature_2m").roundToInt()
                    val code = current.getInt("weather_code")
                    saveWeatherSnapshot(context, city, temp, code, lat, lon)
                } catch (_: Exception) {
                }
            }
        }
    }
}