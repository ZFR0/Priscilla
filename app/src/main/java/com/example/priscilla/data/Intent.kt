package com.example.priscilla.data

import android.util.Log
import java.util.Locale

// --- Data structures to hold rich intent information ---

enum class Tense {
    PAST, PRESENT, FUTURE
}

enum class TimeUnit {
    SECOND, MINUTE, HOUR, DAY, WEEK, MONTH, YEAR
}

data class TimeContext(
    val value: Int,
    val unit: TimeUnit,
    val tense: Tense,
    val originalNumberText: String
)

/**
 * A data class to represent a specific, absolute point in time.
 */
data class AbsoluteTime(
    val year: Int,
    val month: Int,        // 1-12
    val dayOfMonth: Int,
    val hourOfDay: Int,    // 0-23
    val minute: Int
)

/**
 * A sealed class to represent the two ways time can be parsed:
 * - Relatively (e.g., "in 5 minutes")
 * - Absolutely (e.g., "tonight at 8 PM")
 */
sealed class ParsedTime {
    data class Relative(val context: TimeContext) : ParsedTime()
    data class Absolute(val time: AbsoluteTime) : ParsedTime()
}

data class ExtractedIntent(
    val intent: UserIntent,
    val location: String? = null,
    val timeContext: TimeContext? = null,
    val translationQuery: TranslationQuery? = null,
    val reminderInfo: ReminderInfo? = null
)

data class TranslationQuery(
    val phrase: String,
    val targetLanguage: String
)

data class ReminderInfo(
    val task: String,
    val parsedTime: ParsedTime
)

enum class UserIntent {
    GET_TIME,
    GET_WEATHER,
    GET_NEWS,
    GET_LOCATION,
    GET_MATH_RESULT,
    GET_TRANSLATION,
    CREATE_REMINDER
}


class IntentParser {

    // --- Private properties for our rules ---

    private val intentKeywords = mapOf(
        UserIntent.GET_TIME to listOf(
            // Core, Unambiguous Terms
            "time", "hour", "clock",

            // Common Phrasings
            "time is it", // Catches "what time is it" even if "what" is missed by the question parser
            "the hour", // e.g., "Tell me the hour."
            "current time",

            // More Colloquial or Vague Queries
            "what's the ticker", // Slang for time/clock
            "how late is it",
            "is it noon yet",
            "is it midnight"
        ),
        UserIntent.GET_WEATHER to listOf(
            // Core Terms
            "weather", "forecast", "temperature",

            // Conditions & Adjectives
            "sunny", "sun", "sunshine", "clear",
            "rainy", "rain", "drizzle", "showers",
            "cloudy", "clouds", "overcast",
            "stormy", "storm", "thunderstorm",
            "windy", "wind", "breeze",
            "snowy", "snow", "frost",
            "foggy", "fog",
            "humid", "humidity",

            // Temperature & Feeling
            "hot", "warm", "heat", "heatwave",
            "cold", "chilly", "freezing",

            // General & Environmental
            "outside", "out",
            "sky", "air", "atmosphere",
            "sunset", "sunrise"
        ),
        UserIntent.GET_NEWS to listOf(
            // Core, Unambiguous Terms
            "news", "headlines", "the news",

            // Common News-related Phrases (more specific than single words)
            "current events",
            "what's happening in", // The "in" is key here
            "what is happening in",
            "latest events",
            "recent events",
            "top stories",
            "breaking news",

            // Action-oriented phrases
            "tell me about the news",
            "give me the news",
            "can i have the news",
            "update me on", // e.g., "update me on the situation in..."

            // Common but slightly less specific (use with caution, but good to have)
            "latest happenings",
            "recent happenings"
        ),
        UserIntent.GET_LOCATION to listOf(
            // Direct "Where am I?" queries
            "where am i",
            "where are we",
            "what is my current location",
            "what's my current location",
            "my location",

            // Queries about the place itself
            "where is this",
            "what is this place",
            "what's this place",
            "name of this place",
            "where are we now",

            // More formal or command-style queries
            "tell me my location",
            "identify my location",
            "current position",
            "provide coordinates", // Could be for a future GPS coordinate feature
            "what city am i in",
            "what city are we in",
            "which city is this",
            "what country am i in",

            // Common colloquialisms or indirect queries
            "i'm lost",
            "i am lost",
            "am i near" // The parser will extract the location if they say e.g., "Am I near Paris?"
        ),
        UserIntent.GET_MATH_RESULT to listOf(
            // --- Anchor Keywords (High Confidence) ---
            // Arithmetic Operations as words
            "plus",
            "minus",
            "times",
            "divided by",
            "multiplied by",

            // Arithmetic Operations as symbols
            "+",
            "-",
            "*",
            "x", // Common symbol for multiplication
            "/",

            // --- Action Keywords (Medium Confidence) ---
            // These are less likely to cause false positives when combined with numbers
            "calculate",
            "sum of",
            "difference between",

            // --- Specific Question Patterns ---
            "how much is", // e.g. "how much is 5 times 5"
            "how many is"  // e.g. "how many is 10 plus 2"
        ),
        UserIntent.GET_TRANSLATION to listOf(
            // Core Verbs
            "translate",
            "say in",
            "what is", // Ambiguous, but necessary. We will use other clues to confirm.
            "what's",
            "how do you say",
            "how to say",

            // Language Keywords (will also be used for extraction)
            "in french",
            "in spanish",
            "in german",
            "in japanese",
            "in italian",
            "in chinese",
            "to french",
            "to spanish",
            "to german",
            "to japanese",
            "to italian",
            "to chinese"
        ),
        UserIntent.CREATE_REMINDER to listOf(
            "set a reminder",
            "add a reminder",
            "remind me" // The core, simple keyword
        )
    )

    private val numberWords = mapOf(
        "a" to 1, "an" to 1, "one" to 1, "two" to 2, "three" to 3, "four" to 4,
        "five" to 5, "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10
    )

    private val locationPrepositions = setOf("in", "at", "for", "on", "from")

    private val locationConnectors = setOf("de", "del", "la", "las", "es", "of", "the")


    // --- Main public function ---
    fun parse(text: String): List<ExtractedIntent> {
        val lowercasedText = text.lowercase(Locale.ROOT)

        // More specific intents (like translation, math) should come first.
        val intentPrecedence = listOf(
            UserIntent.CREATE_REMINDER,
            UserIntent.GET_TRANSLATION,
            UserIntent.GET_MATH_RESULT,
            UserIntent.GET_LOCATION,
            UserIntent.GET_NEWS,
            UserIntent.GET_TIME,
            UserIntent.GET_WEATHER
        )

        for (intent in intentPrecedence) {
            val keywords = intentKeywords[intent] ?: continue

            if (keywords.any { keyword -> "\\b${Regex.escape(keyword)}\\b".toRegex().containsMatchIn(lowercasedText) }) {
                // A keyword was found. Now, try to extract the specific data.
                // If the extraction is successful, we consider it a confident match and stop.
                val extractedData = when (intent) {
                    UserIntent.GET_LOCATION, UserIntent.GET_MATH_RESULT -> {
                        ExtractedIntent(intent = intent)
                    }
                    UserIntent.GET_TRANSLATION -> {
                        val query = extractTranslationQuery(text)
                        query?.let { ExtractedIntent(intent = intent, translationQuery = it) }
                    }
                    UserIntent.CREATE_REMINDER -> {
                        val reminderInfo = extractReminderInfo(text)
                        reminderInfo?.let { ExtractedIntent(intent = intent, reminderInfo = it) }
                    }
                    else -> { // Weather, News, Time
                        val location = extractLocation(text)
                        val timeContext = extractTimeContext(lowercasedText)
                        ExtractedIntent(intent = intent, location = location, timeContext = timeContext)
                    }
                }

                if (extractedData != null) {
                    // We found a confident match. Return it as the only result.
                    println("DEBUG42:$extractedData")
                    return listOf(extractedData)
                }
                // If extractedData is null (e.g., "what is" was found but no language),
                // we continue the loop to check the next intent.
            }
        }

        // If no confident intents were found after checking all of them, return an empty list.
        return emptyList()
    }

    //  Upgraded extractLocation function ---
    private fun extractLocation(originalText: String): String? {
        val words = originalText.split(" ")
        val articlesToSkip = setOf("the", "a", "an")

        for (i in words.indices) {
            // Find a preposition
            if (locationPrepositions.contains(words[i].lowercase(Locale.ROOT))) {
                var potentialLocationIndex = i + 1

                // Check if the word after the preposition is an article we should skip
                if (potentialLocationIndex < words.size && articlesToSkip.contains(words[potentialLocationIndex].lowercase(Locale.ROOT))) {
                    // If it is, move our index to the next word
                    potentialLocationIndex++
                }

                if (potentialLocationIndex < words.size) {
                    val firstWord = words[potentialLocationIndex]
                    // The first word of the location must be capitalized
                    if (firstWord.isNotBlank() && firstWord.first().isUpperCase()) {
                        val locationParts = mutableListOf(firstWord)
                        // Loop to find multi-word locations
                        var nextWordIndex = potentialLocationIndex + 1 // Start looking from the word after our 'firstWord'
                        while (nextWordIndex < words.size) {
                            val nextWord = words[nextWordIndex]
                            // Add the next word if it's also capitalized or a common connector
                            if (nextWord.isNotBlank() && (nextWord.first().isUpperCase() || locationConnectors.contains(nextWord.lowercase(Locale.ROOT)))) {
                                locationParts.add(nextWord)
                                nextWordIndex++
                            } else {
                                // Stop when we hit a non-capitalized word that isn't a connector
                                break
                            }
                        }
                        // Join the parts and clean the final result of punctuation
                        return locationParts.joinToString(" ").trimEnd('.', ',', '?', '!')
                    }
                }
            }
        }
        return null
    }

    private fun extractTimeContext(lowercasedText: String): TimeContext? {
        val parsedTime = parseTime(lowercasedText) ?: return null

        return when (parsedTime) {
            is ParsedTime.Relative -> {
                // If the master parser found a relative time, just return it directly.
                parsedTime.context
            }
            is ParsedTime.Absolute -> {
                // If the master parser found an absolute time (e.g., "evening"),
                // we need to convert it into a relative TimeContext for the weather function.
                val calendar = java.util.Calendar.getInstance()
                val nowMillis = calendar.timeInMillis

                val absolute = parsedTime.time
                calendar.set(java.util.Calendar.YEAR, absolute.year)
                calendar.set(java.util.Calendar.MONTH, absolute.month - 1)
                calendar.set(java.util.Calendar.DAY_OF_MONTH, absolute.dayOfMonth)
                calendar.set(java.util.Calendar.HOUR_OF_DAY, absolute.hourOfDay)
                calendar.set(java.util.Calendar.MINUTE, absolute.minute)

                val targetMillis = calendar.timeInMillis

                // Calculate the difference in hours from now.
                val diffHours = ((targetMillis - nowMillis) / (1000 * 60 * 60)).toInt()

                TimeContext(
                    value = diffHours.coerceAtLeast(0),
                    unit = TimeUnit.HOUR,
                    tense = Tense.FUTURE,
                    originalNumberText = "" // This field isn't critical for this converted context
                )
            }
        }
    }

    private fun parseTime(lowercasedText: String): ParsedTime? {
        // --- Strategy 1: Look for absolute time keywords first ---
        val timeOfDayMap = mapOf(
            "morning" to 9, "noon" to 12, "afternoon" to 15,
            "evening" to 20, "tonight" to 21, "midnight" to 0
        )
        val foundAbsKeyword = timeOfDayMap.keys.find { "\\b$it\\b".toRegex().containsMatchIn(lowercasedText) }

        if (foundAbsKeyword != null) {
            val targetHour = timeOfDayMap[foundAbsKeyword]!!
            val calendar = java.util.Calendar.getInstance()
            val now = calendar.timeInMillis

            calendar.set(java.util.Calendar.HOUR_OF_DAY, targetHour)
            calendar.set(java.util.Calendar.MINUTE, 0)
            calendar.set(java.util.Calendar.SECOND, 0)
            calendar.set(java.util.Calendar.MILLISECOND, 0)

            if (calendar.timeInMillis <= now) {
                calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
            }

            val absoluteTime = AbsoluteTime(
                year = calendar.get(java.util.Calendar.YEAR),
                month = calendar.get(java.util.Calendar.MONTH) + 1,
                dayOfMonth = calendar.get(java.util.Calendar.DAY_OF_MONTH),
                hourOfDay = calendar.get(java.util.Calendar.HOUR_OF_DAY),
                minute = calendar.get(java.util.Calendar.MINUTE)
            )
            return ParsedTime.Absolute(absoluteTime)
        }

        // --- Strategy 2: Look for standalone relative words ---
        if ("\\btomorrow\\b".toRegex().containsMatchIn(lowercasedText)) {
            return ParsedTime.Relative(TimeContext(1, TimeUnit.DAY, Tense.FUTURE, "tomorrow"))
        }
        if ("\\btoday\\b".toRegex().containsMatchIn(lowercasedText)) {
            return ParsedTime.Relative(TimeContext(0, TimeUnit.DAY, Tense.PRESENT, "today"))
        }
        if ("\\byesterday\\b".toRegex().containsMatchIn(lowercasedText)) {
            return ParsedTime.Relative(TimeContext(1, TimeUnit.DAY, Tense.PAST, "yesterday"))
        }

        // --- Strategy 3: Fallback to (number/modifier, unit) pairs ---
        val words = lowercasedText.split(" ")
        var number: Int? = null
        var numberIndex = -1
        var timeUnit: TimeUnit? = null
        var tense: Tense = Tense.PRESENT
        var originalNumberText = ""
        val timeUnitKeywords = setOf("second", "minute", "hour", "day", "week", "month", "year", "pm", "am")

        for (i in 0 until words.size - 1) {
            val currentWord = words[i]
            val nextWord = words[i + 1]
            val nextWordClean = nextWord.trimEnd('.', ',', '?', '!', ':', ';').removeSuffix("s")

            if (timeUnitKeywords.contains(nextWordClean)) {
                val numFromDigit = currentWord.toIntOrNull()
                val numFromWord = numberWords[currentWord]

                if (numFromDigit != null) {
                    number = numFromDigit
                    numberIndex = i
                    originalNumberText = currentWord
                } else if (numFromWord != null) {
                    number = numFromWord
                    numberIndex = i
                    originalNumberText = currentWord
                } else if (currentWord == "next") {
                    number = 1
                    numberIndex = i
                    originalNumberText = "next"
                    tense = Tense.FUTURE
                } else if (currentWord == "last") {
                    number = 1
                    numberIndex = i
                    originalNumberText = "last"
                    tense = Tense.PAST
                }

                if (number != null && numberIndex != -1) {
                    timeUnit = when (nextWordClean) {
                        "second" -> TimeUnit.SECOND
                        "minute" -> TimeUnit.MINUTE
                        "hour", "pm", "am" -> TimeUnit.HOUR
                        "day" -> TimeUnit.DAY
                        "week" -> TimeUnit.WEEK
                        "month" -> TimeUnit.MONTH
                        "year" -> TimeUnit.YEAR
                        else -> null
                    }
                    if (timeUnit != null) {
                        break // Found a match, exit the loop
                    }
                }
            }
        }

        if (number == null || timeUnit == null || originalNumberText.isBlank()) {
            return null // No time context found by any strategy
        }

        // Determine tense for (number, unit) pairs if not already set by "next/last"
        if (originalNumberText != "next" && originalNumberText != "last") {
            if (numberIndex > 0 && words[numberIndex - 1] == "in") {
                tense = Tense.FUTURE
            } else {
                val unitIndex = numberIndex + 1
                if (unitIndex + 1 < words.size && words[unitIndex + 1].trimEnd('?', '.', '!') == "ago") {
                    tense = Tense.PAST
                } else if (unitIndex + 2 < words.size &&
                    words[unitIndex + 1] == "from" &&
                    words[unitIndex + 2].trimEnd('?', '.', '!') == "now"
                ) {
                    tense = Tense.FUTURE
                }
            }
        }

        val finalTimeContext = TimeContext(number, timeUnit, tense, originalNumberText)
        return ParsedTime.Relative(finalTimeContext)
    }

    private fun extractAbsoluteTime(lowercasedText: String): ParsedTime.Absolute? {
        return parseTime(lowercasedText) as? ParsedTime.Absolute
    }

    private fun extractTranslationQuery(originalText: String): TranslationQuery? {
        val lowercasedText = originalText.lowercase(Locale.ROOT)
        // A map of languages we support and their keywords. The key is the ISO 639-1 code.
        val supportedLanguages = mapOf(
            "fr" to listOf("french"),
            "es" to listOf("spanish"),
            "de" to listOf("german"),
            "ja" to listOf("japanese"),
            "it" to listOf("italian"),
            "zh" to listOf("chinese")
        )

        var targetLanguage: String? = null
        var languageKeyword: String? = null

        // Find the target language in the text
        for ((_, keywords) in supportedLanguages) {
            val foundKeyword = keywords.find { lowercasedText.contains("in $it") || lowercasedText.contains("to $it") }
            if (foundKeyword != null) {
                targetLanguage = keywords.first() // Use the primary name (e.g., "french")
                languageKeyword = foundKeyword
                break
            }
        }

        if (targetLanguage == null || languageKeyword == null) return null

        // Now, extract the phrase. It's usually enclosed in quotes or between keywords.
        var phrase: String? = null

        // Pattern 1: "translate 'PHRASE' to language"
        val quoteRegex = """['"](.*?)['"]""".toRegex()
        val quoteMatch = quoteRegex.find(originalText)
        if (quoteMatch != null) {
            phrase = quoteMatch.groupValues[1]
        }

        // Pattern 2: "how do you say PHRASE in language"
        if (phrase == null) {
            val sayRegex = """how do you say (.*?) in $languageKeyword""".toRegex(RegexOption.IGNORE_CASE)
            val sayMatch = sayRegex.find(originalText)
            if (sayMatch != null) {
                phrase = sayMatch.groupValues[1]
            }
        }

        // Pattern 3: "what is 'PHRASE' in language"
        if (phrase == null) {
            val whatIsRegex = """what is ['"](.*?)['"] in $languageKeyword""".toRegex(RegexOption.IGNORE_CASE)
            val whatIsMatch = whatIsRegex.find(originalText)
            if(whatIsMatch != null) {
                phrase = whatIsMatch.groupValues[1]
            }
        }

        // Final check and return
        if (phrase != null && phrase.isNotBlank()) {
            return TranslationQuery(phrase.trimEnd('?', '.', '!'), targetLanguage)
        }

        return null
    }

    private fun extractReminderInfo(text: String): ReminderInfo? {
        val lowercasedText = text.lowercase(Locale.ROOT)

        // Step 1: Call the single, unified master parser.
        val parsedTime = parseTime(lowercasedText)

        // Step 2: If the master parser found nothing, we cannot create a reminder.
        if (parsedTime == null) {
            Log.d("ReminderDebug", "Step 1 FAILED: No valid time context found by master parser.")
            return null
        }
        Log.d("ReminderDebug", "Step 1: Found parsedTime: $parsedTime")

        // Step 3: Reconstruct the text of the "time block" to remove it from the sentence.
        val timeBlock: String = try {
            when (parsedTime) {
                is ParsedTime.Absolute -> {
                    val timeOfDayKeywords = setOf("morning", "noon", "afternoon", "evening", "tonight", "midnight")
                    val keyword = timeOfDayKeywords.find { "\\b$it\\b".toRegex().containsMatchIn(lowercasedText) } ?: ""

                    if ("\\bthis $keyword\\b".toRegex().containsMatchIn(lowercasedText)) {
                        "this $keyword"
                    } else if ("\\bat $keyword\\b".toRegex().containsMatchIn(lowercasedText)) {
                        "at $keyword"
                    } else {
                        keyword
                    }
                }
                is ParsedTime.Relative -> {
                    val timeContext = parsedTime.context
                    val numberAsText = timeContext.originalNumberText
                    val timeStartIndex = lowercasedText.lastIndexOf(numberAsText)
                    val words = lowercasedText.split(" ")
                    val numberWordIndex = words.lastIndexOf(numberAsText)
                    val originalUnitText = words.getOrNull(numberWordIndex + 1)?.trimEnd('.', ',', '?', '!', ':', ';')
                    val preposition = words.getOrNull(numberWordIndex - 1)
                    val timeBlockStartIndex = if (preposition != null && (preposition == "in" || preposition == "at" || preposition == "on")) {
                        lowercasedText.lastIndexOf("$preposition $numberAsText")
                    } else {
                        timeStartIndex
                    }
                    val unitMatch = "\\b${Regex.escape(originalUnitText!!)}\\b".toRegex().find(lowercasedText, startIndex = timeStartIndex)
                    val timeBlockEndIndex = unitMatch!!.range.last + 1
                    text.substring(timeBlockStartIndex, timeBlockEndIndex)
                }
            }
        } catch (e: Exception) {
            Log.e("ReminderDebug", "Failed to reconstruct time block, cannot create reminder.", e)
            return null
        }

        // Step 4: Find the trigger keyword (e.g., "remind me").
        val keywords = intentKeywords[UserIntent.CREATE_REMINDER] ?: return null
        val keywordMatch = keywords.firstNotNullOfOrNull { keyword ->
            "\\b${Regex.escape(keyword)}\\b".toRegex(RegexOption.IGNORE_CASE).find(text)
        }
        if (keywordMatch == null) {
            Log.d("ReminderDebug", "Step 2 FAILED: No reminder keyword found.")
            return null
        }
        Log.d("ReminderDebug", "Step 2: Found keywordMatch: '${keywordMatch.value}'")

        // Step 5: Isolate the task by removing the keyword and the time block.
        val keywordBlock = keywordMatch.value
        Log.d("ReminderDebug", "Identified timeBlock: '$timeBlock'")
        Log.d("ReminderDebug", "Identified keywordBlock: '$keywordBlock'")

        var task = text
            .replace(keywordBlock, "", ignoreCase = true)
            .replace(timeBlock, "", ignoreCase = true)
            .trim()
        Log.d("ReminderDebug", "Task after removing blocks: '$task'")

        // Step 6: Clean up common connecting words from the start of the task.
        task = task.trim()
            .removePrefix(",").removePrefix(":")
            .removePrefix("to")
            .removePrefix("for")
            .removePrefix("about")
            .removePrefix("that")
            .trim()
            .trimEnd('.', ',', '?', '!')
            .trim()

        Log.d("ReminderDebug", "Final task after cleaning: '$task'")

        if (task.isBlank()) {
            Log.d("ReminderDebug", "Final task is blank. Cannot create reminder.")
            return null
        }

        // Step 7: Capitalize and return the final ReminderInfo object.
        val finalTask = task.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        Log.d("ReminderDebug", "Final formatted task: '$finalTask'")

        return ReminderInfo(finalTask, parsedTime)
    }
}