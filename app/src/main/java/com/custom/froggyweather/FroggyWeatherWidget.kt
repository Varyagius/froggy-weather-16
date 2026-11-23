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
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

class FroggyWeatherWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) updateAppWidget(context, appWidgetManager, id)
        refreshFromNetwork(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            Intent.ACTION_TIME_TICK,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            AppWidgetManager.ACTION_APPWIDGET_UPDATE -> updateAllWidgets(context)
        }
    }

    companion object {
        private const val PREFS = "froggy_widget_prefs"

        fun saveWeatherSnapshot(context: Context, city: String, temp: Int, weatherCode: Int, lat: Double, lon: Double) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("city", city)
                .putInt("temp", temp)
                .putInt("code", weatherCode)
                .putFloat("lat", lat.toFloat())
                .putFloat("lon", lon.toFloat())
                .apply()
            updateAllWidgets(context)
        }

        fun updateAllWidgets(context: Context) {
            val m = AppWidgetManager.getInstance(context)
            val ids = m.getAppWidgetIds(ComponentName(context, FroggyWeatherWidget::class.java))
            for (id in ids) updateAppWidget(context, m, id)
        }

        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val city = p.getString("city", "Хмельницький") ?: "Хмельницький"
            val temp = p.getInt("temp", 1)
            val ukLocale = Locale.forLanguageTag("uk")
            val timeText = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm", ukLocale))
            val dateText = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", ukLocale))

            val views = RemoteViews(context.packageName, R.layout.widget_froggy_weather)
            views.setTextViewText(R.id.widget_clock, timeText)
            views.setTextViewText(R.id.widget_date, dateText)
            views.setTextViewText(R.id.widget_city, city)
            views.setTextViewText(R.id.widget_temp, "${temp}C")
            views.setImageViewResource(R.id.widget_weather_icon, R.mipmap.ic_launcher)

            val pi = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pi)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        fun refreshFromNetwork(context: Context) {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val lat = p.getFloat("lat", 49.4229f).toDouble()
            val lon = p.getFloat("lon", 26.9871f).toDouble()
            val city = p.getString("city", "Хмельницький") ?: "Хмельницький"
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val cur = JSONObject(URL("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code&timezone=auto").readText()).getJSONObject("current")
                    saveWeatherSnapshot(context, city, cur.getDouble("temperature_2m").roundToInt(), cur.getInt("weather_code"), lat, lon)
                } catch (_: Exception) {}
            }
        }
    }
}