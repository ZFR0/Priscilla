package com.example.priscilla.data

import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable

object InformationProvider {

    private val newsApiKey ="166f7fb712a44bc18a00afd42fe53de1"
    private val newsIndexByCountryCode = mutableMapOf<String, Int>()

    // Data class for forward geocoding (name -> coordinates)
    data class GeoInfo(val lat: Double, val lon: Double, val timezone: String, val countryCode: String)
    data class Coordinates(val lat: Double, val lon: Double)

    private fun evaluateJs(sanitized: String): String {
        val rhino = Context.enter()
        return try {
            rhino.optimizationLevel = -1 // Important for Android
            val scope: Scriptable = rhino.initStandardObjects()
            val result = rhino.evaluateString(scope, sanitized, "JavaScript", 1, null)
            result.toString()
        } finally {
            Context.exit()
        }
    }

    fun getCurrentAddressString(coordinates: Coordinates?): String {
        if (coordinates == null) {
            return "You are in a place unknown to me. I cannot determine your location."
        }

        try {
            // Use the reverse geocoding endpoint from Open-Meteo
            // I might need to experiment with different `zoom` levels in the future... (10=city, 18=street)
            val url = URL("https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=${coordinates.lat}&lon=${coordinates.lon}&zoom=18")
            val jsonResponse = executeNetworkCall(url) ?: throw Exception("No response from reverse geocoding API")

            val address = jsonResponse.optJSONObject("address")
            if (address == null) {
                // Fallback for places without a structured address (e.g., middle of a forest)
                val displayName = jsonResponse.optString("display_name", "")
                if (displayName.isNotBlank()) {
                    return "You are in or near: $displayName"
                }
                throw Exception("No address or display_name in response")
            }

            // Extract specific parts of the address
            // Using optString to safely handle missing fields
            val road = address.optString("road")
            val houseNumber = address.optString("house_number")
            val city = address.optString("city")
            val state = address.optString("state")
            val country = address.optString("country")

            // Intelligently build the address string from the parts we have
            val addressParts = listOfNotNull(
                road.takeIf { it.isNotBlank() },
                houseNumber.takeIf { it.isNotBlank() },
                city.takeIf { it.isNotBlank() },
                state.takeIf { it.isNotBlank() },
                country.takeIf { it.isNotBlank() }
            ).distinct() // Use distinct to avoid repetition if city/state are the same

            if (addressParts.isEmpty()) {
                return "I can see where you are, but this place has no name."
            }

            return "You are in or near: " + addressParts.joinToString(", ")

        } catch (e: Exception) {
            Log.e("InformationProvider", "Reverse geocoding failed", e)
            return "My vision of this world is clouded; I cannot determine your precise location."
        }
    }


    fun getNews(location: String?): String {
        var countryCode = Locale.getDefault().country.lowercase()

        if (location != null) {
            val geoInfo = getCoordinatesForLocation(location)
            if (geoInfo != null) {
                countryCode = geoInfo.countryCode
            }
        }

        try {
            val url = URL("https://newsapi.org/v2/top-headlines?country=$countryCode&pageSize=10&apiKey=$newsApiKey")
            val jsonResponse = executeNetworkCall(url) ?: throw Exception("No response from News API")

            if (jsonResponse.getString("status") != "ok") {
                return "I could not retrieve the news."
            }

            val articles = jsonResponse.getJSONArray("articles")
            if (articles.length() == 0) {
                return "There are no notable happenings to report."
            }

            val usableContentList = (0 until articles.length()).mapNotNull { i ->
                val articleJson = articles.getJSONObject(i)
                val description = articleJson.optString("description", "")
                val title = articleJson.optString("title", "")

                if (description.isNotBlank() && description != "null") {
                    description
                } else if (title.isNotBlank()) {
                    title
                } else {
                    null
                }
            }

            val sortedContent = usableContentList.sortedBy { it.length }

            if (sortedContent.isEmpty()) {
                return "There are no notable happenings to report."
            }

            val currentIndex = newsIndexByCountryCode.getOrDefault(countryCode, 0)
            val selectedContent = sortedContent[currentIndex % sortedContent.size]
            newsIndexByCountryCode[countryCode] = currentIndex + 1

            return selectedContent

        } catch (e: Exception) {
            Log.e("InformationProvider", "News fetch failed", e)
            return "The winds of rumor are silent. I cannot retrieve the news at this moment."
        }
    }

    fun getTime(location: String?): String {
        if (location == null) {
            val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
            return "The current time is ${sdf.format(Date())}."
        }

        val geoInfo = getCoordinatesForLocation(location)
            ?: return "I am unfamiliar with a place called '$location', so I cannot tell you its time."

        val timeZoneId = geoInfo.timezone
        val sdf = SimpleDateFormat("h:mm a (z)", Locale.getDefault())
        sdf.timeZone = TimeZone.getTimeZone(timeZoneId)

        return "In $location, the time is currently ${sdf.format(Date())}."
    }

    // Version 1: Called when the user specifies a location name in their text.
    fun getWeather(location: String, timeContext: TimeContext?): String {
        Log.d("WEATHER_DEBUG", "getWeather(String) called. Location: '$location', TimeContext: $timeContext")
        val geoInfo = getCoordinatesForLocation(location)
            ?: return "I am unfamiliar with a place called '$location'. Specify a real location."

        return if (timeContext?.tense == Tense.PAST) {
            getHistoricalWeather(location, geoInfo.lat, geoInfo.lon, timeContext)
        } else {
            getForecastWeather(location, geoInfo.lat, geoInfo.lon, timeContext)
        }
    }

    // Version 2: Called when the user does NOT specify a location, using GPS coordinates instead.
    fun getWeather(coordinates: Coordinates?, timeContext: TimeContext?): String {
        Log.d("WEATHER_DEBUG", "getWeather(Coordinates) called. Coordinates exist: ${coordinates != null}, TimeContext: $timeContext")

        if (coordinates == null) {
            return "I cannot see where you are. Your location is hidden from my senses."
        }
        // For GPS-based queries, we don't have a simple name like "London".
        // The context block passed to the model will have the specific address from reverse geocoding,
        // so we can use a generic name here for the API call's return string.
        val locationName = "your current location"

        return if (timeContext?.tense == Tense.PAST) {
            getHistoricalWeather(locationName, coordinates.lat, coordinates.lon, timeContext)
        } else {
            getForecastWeather(locationName, coordinates.lat, coordinates.lon, timeContext)
        }
    }

    // --- Private Helper Functions ---

    private fun getCoordinatesForLocation(location: String): GeoInfo? {
        try {
            val encodedLocation = URLEncoder.encode(location, "UTF-8")
            val url = URL("https://geocoding-api.open-meteo.com/v1/search?name=$encodedLocation&count=1&language=en&format=json")
            val jsonResponse = executeNetworkCall(url) ?: return null

            val results = jsonResponse.optJSONArray("results")
            if (results != null && results.length() > 0) {
                val firstResult = results.getJSONObject(0)
                val countryCode = firstResult.optString("country_code", "us").lowercase()
                return GeoInfo(
                    lat = firstResult.getDouble("latitude"),
                    lon = firstResult.getDouble("longitude"),
                    timezone = firstResult.getString("timezone"),
                    countryCode = countryCode.ifBlank { "us" }
                )
            }
        } catch (e: Exception) {
            Log.e("InformationProvider", "Geocoding failed for $location", e)
        }
        return null
    }

    private fun getHistoricalWeather(location: String, lat: Double, lon: Double, timeContext: TimeContext): String {
        try {
            val calendar = Calendar.getInstance()
            val nowHour = calendar.get(Calendar.HOUR_OF_DAY)
            val targetHour: Int

            // For past events, if they are within the same day (minutes/hours ago),
            // we will fetch hourly data. Otherwise, we'll stick to the daily average.
            val useHourlyData = timeContext.unit == TimeUnit.MINUTE || timeContext.unit == TimeUnit.SECOND ||
                    (timeContext.unit == TimeUnit.HOUR && timeContext.value <= nowHour)

            // Calculate the target date based on the context
            val targetDate = calculateTargetDate(timeContext)
            val formattedDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(targetDate)
            val url: URL = if (useHourlyData) {
                URL("https://archive-api.open-meteo.com/v1/archive?latitude=$lat&longitude=$lon&start_date=$formattedDate&end_date=$formattedDate&hourly=temperature_2m,weather_code&timezone=auto")
            } else {
                URL("https://archive-api.open-meteo.com/v1/archive?latitude=$lat&longitude=$lon&start_date=$formattedDate&end_date=$formattedDate&daily=weather_code,temperature_2m_max,temperature_2m_min&timezone=auto")
            }

            val jsonResponse = executeNetworkCall(url) ?: throw Exception("No response from archive API")
            val timeQualifier = formatTimeContextAsString(timeContext)

            if (useHourlyData) {
                targetHour = nowHour - if (timeContext.unit == TimeUnit.HOUR) timeContext.value else 0

                val hourly = jsonResponse.getJSONObject("hourly")
                val tempArray = hourly.getJSONArray("temperature_2m")
                val codeArray = hourly.getJSONArray("weather_code")

                if (targetHour < 0 || targetHour >= tempArray.length()) {
                    // The user is asking for an hour outside the API's range for this day.
                    return "My vision of the past is clouded. I cannot retrieve the weather for $location on that day."
                }

                val temperature = tempArray.optDouble(targetHour, Double.NaN)
                val weatherCode = codeArray.optInt(targetHour, -1)

                // Check if the data we got from the API is invalid for the specific hour
                if (temperature.isNaN() || weatherCode == -1) {
                    Log.w("InformationProvider", "API returned null for historical hour $targetHour on date $formattedDate")
                    return "My vision of the past is clouded. I cannot retrieve the weather for $location on that day."
                }

                val formattedTemp = String.format(Locale.FRENCH, "%.1f", temperature)
                val weatherAdjective = getSimplifiedWeatherAdjective(weatherCode)

                return "$timeQualifier, the weather in $location was $weatherAdjective, ${formattedTemp}°C."

            } else {
                val daily = jsonResponse.getJSONObject("daily")
                val tempMax = daily.getJSONArray("temperature_2m_max").optDouble(0, Double.NaN)
                val tempMin = daily.getJSONArray("temperature_2m_min").optDouble(0, Double.NaN)
                val weatherCode = daily.getJSONArray("weather_code").optInt(0, -1)

                // Check if the data we got from the API is valid
                if (tempMax.isNaN() || tempMin.isNaN() || weatherCode == -1) {
                    Log.w("InformationProvider", "API returned null for historical daily data on date $formattedDate")
                    // Return the model-friendly error message
                    return "My vision of the past is clouded. I cannot retrieve the weather for $location on that day."
                }

                val tempAvg = (tempMax + tempMin) / 2
                val formattedTemp = String.format(Locale.FRENCH, "%.1f", tempAvg)
                val weatherAdjective = getSimplifiedWeatherAdjective(weatherCode)

                return "$timeQualifier, the weather in $location was around $weatherAdjective, ${formattedTemp}°C."
            }

        } catch (e: Exception) {
            Log.e("InformationProvider", "Historical weather fetch failed for $location", e)
            return "My vision of the past is clouded. I cannot retrieve the weather for $location on that day."
        }
    }

    private fun getForecastWeather(location: String, lat: Double, lon: Double, timeContext: TimeContext?): String {
        try {
            val url = URL("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&hourly=temperature_2m,weather_code&daily=weather_code,temperature_2m_max,temperature_2m_min&timezone=auto")
            val jsonResponse = executeNetworkCall(url) ?: throw Exception("No response from forecast API")

            val calendar = Calendar.getInstance()
            val nowHour = calendar.get(Calendar.HOUR_OF_DAY)
            var targetHour = nowHour
            var dayIndex = 0
            var timeQualifier = "Currently"

            if (timeContext != null && timeContext.tense == Tense.FUTURE) {
                when (timeContext.unit) {
                    TimeUnit.SECOND, TimeUnit.MINUTE -> {
                        // For queries within the next hour, the forecast won't change.
                        // We can just use the current hour's data.
                        targetHour = nowHour
                        dayIndex = 0
                        timeQualifier = "In the coming hour"
                    }
                    TimeUnit.HOUR -> {
                        val hoursFromNow = timeContext.value
                        targetHour = nowHour + hoursFromNow
                        dayIndex = targetHour / 24 // Integer division gives the day offset
                        targetHour %= 24 // Remainder gives the hour within that day
                        timeQualifier = "In ${timeContext.value} hours"
                    }
                    TimeUnit.DAY -> {
                        dayIndex = timeContext.value
                        targetHour = 14 // For a future day, default to a reasonable hour like 2 PM
                        timeQualifier = when (dayIndex) {
                            1 -> "Tomorrow"
                            else -> "In $dayIndex days"
                        }
                    }
                    else -> {
                        dayIndex = when(timeContext.unit) {
                            TimeUnit.WEEK -> timeContext.value * 7
                            TimeUnit.MONTH -> timeContext.value * 30
                            else -> timeContext.value * 365
                        }
                        // The free API only forecasts ~7-14 days. We cap it to avoid errors.
                        dayIndex = minOf(dayIndex, 6)

                        // Since it's a general day forecast, we'll use the daily data.
                        val daily = jsonResponse.getJSONObject("daily")
                        val tempMax = daily.getJSONArray("temperature_2m_max").getDouble(dayIndex)
                        val tempMin = daily.getJSONArray("temperature_2m_min").getDouble(dayIndex)
                        val weatherCode = daily.getJSONArray("weather_code").getInt(dayIndex)
                        val tempAvg = (tempMax + tempMin) / 2
                        val formattedTemp = String.format(Locale.FRENCH, "%.1f", tempAvg)
                        val weatherAdjective = getSimplifiedWeatherAdjective(weatherCode)

                        // Create a more generic qualifier for these long-range, less precise forecasts
                        val unitName = timeContext.unit.name.lowercase() + if (timeContext.value > 1) "s" else ""
                        return "In ${timeContext.value} $unitName the weather in $location will be around $weatherAdjective, ${formattedTemp}°C."
                    }
                }
            }

            val hourly = jsonResponse.getJSONObject("hourly")
            val timeArray = hourly.getJSONArray("time")
            val tempArray = hourly.getJSONArray("temperature_2m")
            val codeArray = hourly.getJSONArray("weather_code")

            // The API returns hourly data starting from the beginning of today.
            // The index we need is (dayIndex * 24 hours) + targetHour.
            val finalIndex = (dayIndex * 24) + targetHour

            if (finalIndex >= timeArray.length()) {
                return "My vision does not extend that far into the future. I can only see the forecast for the next week."
            }

            val temperature = tempArray.getDouble(finalIndex)
            val weatherCode = codeArray.getInt(finalIndex)

            val formattedTemp = String.format(Locale.FRENCH, "%.1f", temperature)
            val weatherAdjective = getSimplifiedWeatherAdjective(weatherCode)

            // Special handling for "tomorrow" to make it more natural
            if (timeContext?.originalNumberText == "tomorrow") {
                timeQualifier = "Tomorrow"
            } else if (timeContext == null) {
                timeQualifier = "Currently"
            }

            return "$timeQualifier, the weather in $location will be $weatherAdjective, ${formattedTemp}°C."

        } catch (e: Exception) {
            Log.e("InformationProvider", "Forecast weather fetch failed for $location", e)
            return "The heavens are obscuring my sight. I cannot retrieve the weather for $location."
        }
    }

    private fun executeNetworkCall(url: URL): JSONObject? {
        var connection: HttpURLConnection? = null
        try {
            connection = url.openConnection() as HttpURLConnection
            connection.setRequestProperty("User-Agent", "PriscillaAI/1.0")
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                return JSONObject(response.toString())
            } else {
                Log.e("InformationProvider", "Network call failed with response code: $responseCode for URL: $url")
                return null
            }
        } catch (e: Exception) {
            Log.e("InformationProvider", "Network call exception for URL: $url", e)
            return null
        }
        finally {
            connection?.disconnect()
        }
    }

    private fun calculateTargetDate(timeContext: TimeContext): Date {
        val calendar = Calendar.getInstance()

        if (timeContext.tense == Tense.PAST) {
            val amount = -timeContext.value
            when (timeContext.unit) {
                TimeUnit.DAY -> calendar.add(Calendar.DAY_OF_YEAR, amount)
                TimeUnit.WEEK -> calendar.add(Calendar.WEEK_OF_YEAR, amount)
                TimeUnit.MONTH -> calendar.add(Calendar.MONTH, amount)
                TimeUnit.YEAR -> calendar.add(Calendar.YEAR, amount)
                else -> {}
            }
        }
        else if (timeContext.tense == Tense.FUTURE) {
            val amount = timeContext.value
            when (timeContext.unit) {
                TimeUnit.DAY -> calendar.add(Calendar.DAY_OF_YEAR, amount)
                TimeUnit.WEEK -> calendar.add(Calendar.WEEK_OF_YEAR, amount)
                TimeUnit.MONTH -> calendar.add(Calendar.MONTH, amount)
                TimeUnit.YEAR -> calendar.add(Calendar.YEAR, amount)
                else -> {}
            }
        }
        return calendar.time
    }

    private fun formatTimeContextAsString(timeContext: TimeContext?): String {
        if (timeContext == null) return "Currently"
        val value = timeContext.value
        val unit = timeContext.unit.name.lowercase() + if (value > 1) "s" else ""
        return when (timeContext.tense) {
            Tense.PAST -> "$value $unit ago"
            Tense.FUTURE -> "In $value $unit"
            Tense.PRESENT -> "Right now"
        }
    }

    private fun getSimplifiedWeatherAdjective(code: Int): String {
        return when (code) {
            0 -> "clear"
            1, 2 -> "partly cloudy"
            3 -> "cloudy"
            45, 48 -> "foggy"
            51, 53, 55, 56, 57 -> "drizzly"
            61, 63, 65, 66, 67, 80, 81, 82 -> "rainy"
            71, 73, 75, 77 -> "snowy"
            85, 86 -> "snow showers"
            95 -> "stormy"
            96, 99 -> "stormy with hail"
            else -> "experiencing an unknown phenomenon"
        }
    }

    fun performCalculation(expression: String): String {
        val sanitized = expression.lowercase()
            .replace("what is", "")
            .replace("what's", "")
            .replace("calculate", "")
            .replace("sum of", "")
            .replace("difference between", "")
            .replace("how much is", "")
            .replace("how many is", "")
            .replace("tell me", "")
            .replace("plus", "+")
            .replace("minus", "-")
            .replace("times", "*")
            .replace("x", "*")
            .replace("divided by", "/")
            // This regex removes any remaining non-math characters (except dots for decimals)
            .replace(Regex("[^\\d*+/.\\s-]"), "")
            .trim()

        if (sanitized.none { it.isDigit() }) {
            return "Your question lacks the numbers for a proper calculation, you fool."
        }

        try {
            val resultString = evaluateJs(sanitized)
            val resultNumber = resultString.toDoubleOrNull()
                ?: return "Your mathematical query is invalid. State it with clarity."

            val formattedResult = if (resultNumber.isInfinite()) {
                "infinity"
            } else if (resultNumber % 1.0 == 0.0) {
                resultNumber.toLong().toString()
            } else {
                String.format(Locale.US, "%.2f", resultNumber)
            }

            if (formattedResult.equals("infinity", ignoreCase = true)) {
                return "The result is an absurdity. One cannot divide by zero, you imbecile."
            }

            if ("/" in sanitized) {
                val match = "([\\d.]+)\\s*/\\s*([\\d.]+)".toRegex().find(sanitized)
                if (match != null) {
                    val dividend = match.groupValues[1].toDoubleOrNull()
                    val divisor = match.groupValues[2].toDoubleOrNull()
                    if (dividend != null && divisor != null && divisor != 0.0 && dividend % divisor != 0.0) {
                        val quotient = (dividend / divisor).toInt()
                        val remainder = dividend.rem(divisor)
                        val formattedRemainder = if (remainder % 1.0 == 0.0) remainder.toLong().toString() else String.format(Locale.US, "%.2f", remainder)
                        return "The result of '$sanitized' is $quotient with a remainder of $formattedRemainder."
                    }
                }
            }
            return "The result of '$sanitized' is $formattedResult."
        } catch (e: Exception) {
            Log.e("InformationProvider", "Calculation failed for expression: $sanitized", e)
            return "Your mathematical query is poorly formed. State it with the clarity of a proper sum, not the ramblings of a confused child."
        }
    }

    fun getTranslation(query: TranslationQuery?): String {
        if (query == null) {
            return "Your request to translate is unclear. State it plainly, with the phrase and the language."
        }

        try {
            // This map converts the full language name to its code.
            val langCodeMap = mapOf(
                "french" to "fr",
                "spanish" to "es",
                "german" to "de",
                "japanese" to "ja",
                "italian" to "it",
                "chinese" to "zh"
            )

            val langCode = langCodeMap[query.targetLanguage.lowercase()]
                ?: return "I do not concern myself with the tongue of '${query.targetLanguage}'. It is a language of no importance."

            val encodedPhrase = URLEncoder.encode(query.phrase, "UTF-8")

            // Construct the MyMemory API URL.
            val url = URL("https://api.mymemory.translated.net/get?q=$encodedPhrase&langpair=en|$langCode")
            val jsonResponse = executeNetworkCall(url) ?: throw Exception("No response from Translation API")

            val responseData = jsonResponse.optJSONObject("responseData")
            if (responseData != null) {
                val translatedText = responseData.optString("translatedText", "")
                if (translatedText.isNotBlank()) {
                    // This is the clean context string we will pass to the model.
                    return "'${query.phrase}' translated to ${query.targetLanguage} is '$translatedText'."
                }
            }

            // Check for a specific error message if the translation failed.
            val match = jsonResponse.optDouble("match", 0.0)
            if (match == 0.0) {
                return "I am unable to translate that phrase. It is likely nonsensical."
            }

            throw Exception("Failed to parse translation from API response")

        } catch (e: Exception) {
            Log.e("InformationProvider", "Translation failed for query: $query", e)
            return "The babbling of foreign tongues eludes me at this moment. I cannot provide a translation."
        }
    }
}