package com.example.priscilla.data

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import android.speech.tts.UtteranceProgressListener
import android.os.Bundle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.util.UUID

class TtsManager(
    private val context: Context
) : TextToSpeech.OnInitListener {

    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tts: TextToSpeech? = null

    // A deferred object to signal when TTS initialization is complete and successful.
    private val ttsInitialized = CompletableDeferred<Boolean>()

    // A StateFlow to report the current speaking status to the UI.
    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking = _isSpeaking.asStateFlow()

    init {
        // Start the TTS engine initialization on a background thread.
        managerScope.launch {
            try {
                Log.d("TtsManager", "Initializing TTS engine...")
                tts = TextToSpeech(context, this@TtsManager)
            } catch (e: Exception) {
                Log.e("TtsManager", "Exception during TTS initialization", e)
                ttsInitialized.complete(false)
            }
        }
    }

    /**
     * This is the callback function from TextToSpeech.OnInitListener.
     * It's called by the system when the TTS engine is ready.
     */
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            Log.i("TtsManager", "TTS Engine initialized successfully.")
            tts?.language = Locale.US

            // Set up the listener to track when speech starts and stops.
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    // Speech has started, update our state.
                    _isSpeaking.value = true
                }

                override fun onDone(utteranceId: String?) {
                    // The queue is finished, update our state.
                    _isSpeaking.value = false
                }

                @Deprecated("deprecated in API level 21")
                override fun onError(utteranceId: String?) {
                    // An error occurred, update our state.
                    _isSpeaking.value = false
                }
            })

            ttsInitialized.complete(true)
        } else {
            Log.e("TtsManager", "TTS Engine initialization failed with status: $status")
            ttsInitialized.complete(false)
        }
    }


    // --- Public Functions ---

    /**
     * Speaks a chunk of text, adding it to the queue.
     * This is the core function for our streaming playback.
     */
    suspend fun speakStream(text: String) {
        if (ttsInitialized.await()) {
            withContext(Dispatchers.Main) {
                //  We add a unique utteranceId to each speak call ---
                // This is required for the UtteranceProgressListener to work reliably.
                val params = Bundle()
                tts?.speak(text, TextToSpeech.QUEUE_ADD, params, UUID.randomUUID().toString())
            }
        }
    }

    /**
     * Stops any current speech and clears the playback queue.
     * Essential for interrupting Priscilla when a new response is starting.
     */
    suspend fun stopAndClearQueue() {
        if (ttsInitialized.await()) {
            withContext(Dispatchers.Main) {
                tts?.stop()
                _isSpeaking.value = false
            }
        }
    }

    /**
     * Gets a list of available TTS voices on the user's device.
     */
    suspend fun getAvailableVoices(): Set<Voice> {
        return if (ttsInitialized.await()) {
            tts?.voices ?: emptySet()
        } else {
            emptySet()
        }
    }

    /**
     * Sets the voice, pitch, and speech rate for playback.
     */
    suspend fun setSpeechParameters(voiceId: String?, pitch: Float, rate: Float) {
        if (ttsInitialized.await()) {
            withContext(Dispatchers.Main) {
                tts?.let { engine ->
                    // Find the voice object from the provided ID.
                    engine.voice = engine.voices.find { it.name == voiceId } ?: engine.defaultVoice
                    engine.setPitch(pitch)
                    engine.setSpeechRate(rate)
                }
            }
        }
    }

    /**
     * Shuts down the TTS engine to release resources.
     * Crucial to call this when the app is closing.
     */
    fun shutdown() {
        Log.d("TtsManager", "Shutting down TTS engine.")
        tts?.stop()
        tts?.shutdown()
    }
}