package com.example.priscilla.data.source

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.priscilla.KnowledgeDataSource
import com.example.priscilla.LocationProvider
import com.example.priscilla.data.ActionExecutor
import com.example.priscilla.data.ImageAnalyzer
import com.example.priscilla.data.InformationProvider
import com.example.priscilla.data.IntentParser
import com.example.priscilla.data.ReminderRepository
import com.example.priscilla.data.UserIntent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class KnowledgeDataSourceImpl(
    private val context: Context,
    private val reminderRepository: ReminderRepository
) : KnowledgeDataSource {

    private val intentParser = IntentParser()
    private val imageAnalyzer = ImageAnalyzer(context)
    private val actionExecutor = ActionExecutor(context, reminderRepository)

    override suspend fun getContextString(prompt: String, image: Bitmap?, locationProvider: LocationProvider): String? {
        return withContext(Dispatchers.IO) {
            if (image != null) {
                // Image handling logic
                val labels = imageAnalyzer.analyze(image)
                return@withContext if (labels.isNotEmpty()) {
                    "The objects I perceive in this image are: ${labels.joinToString(", ")}."
                } else {
                    "The image is incomprehensible or contains nothing of interest."
                }
            }

            // Text handling logic
            val intentData = intentParser.parse(prompt).firstOrNull() ?: return@withContext null
            Log.d("WEATHER_DEBUG", "Parser Result: intent=${intentData.intent}, location=${intentData.location}, timeContext=${intentData.timeContext}")

            // An intent was detected. Execute the corresponding action to get the context string.
            when (intentData.intent) {
                UserIntent.GET_WEATHER -> {
                    if (intentData.location != null) {
                        InformationProvider.getWeather(intentData.location, intentData.timeContext)
                    } else {
                        val coordinates = locationProvider.getUserLocation()
                        InformationProvider.getWeather(coordinates, intentData.timeContext)
                    }
                }
                UserIntent.GET_TIME -> InformationProvider.getTime(intentData.location)
                UserIntent.GET_NEWS -> InformationProvider.getNews(intentData.location)
                UserIntent.GET_LOCATION -> {
                    val coordinates = locationProvider.getUserLocation()
                    InformationProvider.getCurrentAddressString(coordinates)
                }
                UserIntent.GET_TRANSLATION -> InformationProvider.getTranslation(intentData.translationQuery)
                UserIntent.CREATE_REMINDER -> actionExecutor.setReminder(intentData.reminderInfo).confirmationMessage
                UserIntent.GET_MATH_RESULT -> InformationProvider.performCalculation(prompt)
            }
        }
    }

    override suspend fun analyzeImage(image: Bitmap): List<String> {
        return imageAnalyzer.analyze(image)
    }

    override suspend fun loadBitmapFromPath(path: String): Bitmap? {
        return imageAnalyzer.loadBitmapFromPath(path)
    }
}