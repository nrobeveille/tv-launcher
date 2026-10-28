package fr.nrobeveille.tvlauncher.data

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

/** Type de temps, pour choisir l'icône. */
enum class WeatherKind { CLEAR, PARTLY, CLOUDY, FOG, RAIN, SNOW, STORM }

@Immutable
data class Weather(
    /** Température en °C, arrondie. */
    val temperature: Int,
    /** Code météo WMO renvoyé par Open-Meteo. */
    val code: Int,
    val isDay: Boolean,
) {
    val kind: WeatherKind
        get() = when (code) {
            0 -> WeatherKind.CLEAR
            1, 2 -> WeatherKind.PARTLY
            3 -> WeatherKind.CLOUDY
            45, 48 -> WeatherKind.FOG
            in 51..67, in 80..82 -> WeatherKind.RAIN
            in 71..77, 85, 86 -> WeatherKind.SNOW
            in 95..99 -> WeatherKind.STORM
            else -> WeatherKind.CLOUDY
        }

    val description: String
        get() = when (kind) {
            WeatherKind.CLEAR -> if (isDay) "Ensoleillé" else "Ciel dégagé"
            WeatherKind.PARTLY -> "Éclaircies"
            WeatherKind.CLOUDY -> "Nuageux"
            WeatherKind.FOG -> "Brouillard"
            WeatherKind.RAIN -> "Pluie"
            WeatherKind.SNOW -> "Neige"
            WeatherKind.STORM -> "Orage"
        }
}

/** Météo actuelle via Open-Meteo (gratuit, sans clé d'API). */
class WeatherRepository {

    suspend fun fetch(): Weather = withContext(Dispatchers.IO) {
        val url = URL(
            "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$LATITUDE&longitude=$LONGITUDE" +
                "&current=temperature_2m,weather_code,is_day"
        )
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("User-Agent", "tv-launcher")
        }
        try {
            val status = connection.responseCode
            if (status !in 200..299) throw IOException("Open-Meteo : HTTP $status")
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val current = JSONObject(body).getJSONObject("current")
            Weather(
                temperature = current.getDouble("temperature_2m").roundToInt(),
                code = current.getInt("weather_code"),
                isDay = current.optInt("is_day", 1) == 1,
            )
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val CITY = "Angers"
        private const val LATITUDE = 47.4784
        private const val LONGITUDE = -0.5632
    }
}
