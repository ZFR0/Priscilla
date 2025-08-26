package com.example.priscilla.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.priscilla.AuthRepository
import com.example.priscilla.ChatRepository
import com.example.priscilla.KnowledgeDataSource
import com.example.priscilla.LlamaDataSource
import com.example.priscilla.LocalDataSource
import com.example.priscilla.LocationProvider
import com.example.priscilla.SyncResult
import com.example.priscilla.data.ChatTurn
import com.example.priscilla.data.ChatTurnEntity
import com.example.priscilla.data.Conversation
import com.example.priscilla.data.ImageUploader
import com.example.priscilla.data.LlamaModel
import com.example.priscilla.data.SettingsRepository
import com.example.priscilla.data.TtsManager
import com.example.priscilla.data.source.ChatCloudDataSource
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

class ChatRepositoryImpl(
    private val context: Context,
    private val llamaDataSource: LlamaDataSource,
    private val localDataSource: LocalDataSource,
    private val knowledgeDataSource: KnowledgeDataSource,
    private val settingsRepository: SettingsRepository,
    private val chatCloudDataSource: ChatCloudDataSource,
    private val authRepository: AuthRepository,
    private val ttsManager: TtsManager,
    private val imageUploader: ImageUploader
) : ChatRepository {

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson = Gson()

    // --- State Management ---
    private val _uiChatHistory = MutableStateFlow<List<ChatTurn>>(emptyList())
    override val uiChatHistory: StateFlow<List<ChatTurn>> = _uiChatHistory.asStateFlow()

    private val _isModelInitialized = MutableStateFlow(false)
    override val isModelInitialized: StateFlow<Boolean> = _isModelInitialized.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    override val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _modelResponseText = MutableStateFlow("Initializing...")
    override val modelResponseText: StateFlow<String> = _modelResponseText.asStateFlow()

    private val _activeModel = MutableStateFlow<LlamaModel>(LlamaModel.TinyLlama1B)
    override val activeModel: StateFlow<LlamaModel> = _activeModel.asStateFlow()
    override val isTtsSpeaking: StateFlow<Boolean> = ttsManager.isSpeaking

    private var currentConversationId: String? = null
    private val locationProvider = LocationProvider(context)
    private var cloudSyncJob: Job? = null

    init {
        repositoryScope.launch {
            loadModel()
        }

        repositoryScope.launch {
            authRepository.currentUser.collectLatest { user ->
                cloudSyncJob?.cancel()
                if (user != null && !user.isGuest) {
                    startCloudSync(user.uid)
                } else {
                    Log.i("ChatRepository", "User is guest or null. No cloud sync active.")
                }
            }
        }
    }

    private fun startCloudSync(userId: String) {
        Log.i("ChatRepository", "Starting real-time cloud sync for user $userId")
        cloudSyncJob = repositoryScope.launch {
            chatCloudDataSource.getConversationsFlow(userId).collect { cloudConversations ->
                Log.d("ChatRepository", "Received ${cloudConversations.size} conversations from cloud.")
                cloudConversations.forEach { cloudConvo ->
                    val localConvo = localDataSource.getConversationById(cloudConvo.id)
                    if (localConvo == null) {
                        Log.d("ChatRepository", "Sync: Inserting new conversation #${cloudConvo.id}")
                        localDataSource.insertConversation(cloudConvo)
                        val turns = chatCloudDataSource.getTurnsForConversation(userId, cloudConvo.id)
                        if (turns.isNotEmpty()) {
                            localDataSource.insertTurns(turns)
                        }
                    } else {
                        if (cloudConvo.lastModified > localConvo.lastModified) {
                            Log.d("ChatRepository", "Sync: Updating existing conversation #${cloudConvo.id}")
                            localDataSource.insertConversation(cloudConvo)
                            localDataSource.deleteTurnsForConversation(cloudConvo.id)
                            val turns = chatCloudDataSource.getTurnsForConversation(userId, cloudConvo.id)
                            if (turns.isNotEmpty()) {
                                localDataSource.insertTurns(turns)
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun loadModel() {
        _isLoading.value = true
        _isModelInitialized.value = false
        _modelResponseText.value = "Priscilla is waking up, this may take a while..."

        try {
            val selectedModel = settingsRepository.getSelectedModel()
            _activeModel.value = selectedModel
            val params = settingsRepository.modelParametersFlow.first()
            val loaded = llamaDataSource.initialize(selectedModel, params)
            if (loaded) {
                _modelResponseText.value = "Priscilla is ready. I await your trivial questions, commoner."
                _isModelInitialized.value = true
            } else {
                _modelResponseText.value = "Error: Failed to load model ${selectedModel.fileName}."
            }
        } catch (e: Exception) {
            _modelResponseText.value = "Error: ${e.message}"
            Log.e("ChatRepository", "Error loading model", e)
        } finally {
            _isLoading.value = false
        }
    }

    override suspend fun sendMessage(userMessage: String, image: Bitmap?) {
        if ((userMessage.isBlank() && image == null) || _isLoading.value) return

        ttsManager.stopAndClearQueue()

        _isLoading.value = true
        var cloudImageUrl: String? = null

        val displayedUserMessage = if (image != null && userMessage.isBlank()) {
            "Behold this image and speak your mind, Priscilla."
        } else {
            userMessage
        }

        if (image != null) {
            cloudImageUrl = imageUploader.uploadImage(image)
            // Optional: Handle upload failure
            if (cloudImageUrl == null) {
                // Here you could decide how to handle an upload failure.
                // For now, we'll just log it and proceed without an image.
                Log.e("ChatRepository", "Image upload failed. Proceeding without image URL.")
            }
        }

        // --- TTS OPTIMIZATION: Fetch settings once at the start ---
        val voiceSettings = settingsRepository.voiceSettingsFlow.first()
        val isTtsEnabled = voiceSettings.selectedVoiceId != "NONE"
        if (isTtsEnabled) {
            ttsManager.setSpeechParameters(
                voiceSettings.selectedVoiceId,
                voiceSettings.pitch,
                voiceSettings.speed
            )
        }

        val contextString =
            knowledgeDataSource.getContextString(displayedUserMessage, image, locationProvider)

        if (currentConversationId == null) {
            val newConversation = Conversation(firstMessagePreview = displayedUserMessage)
            localDataSource.insertConversation(newConversation)
            currentConversationId = newConversation.id
        }

        val currentTurn = ChatTurn(
            user = displayedUserMessage,
            assistant = "",
            imagePath = null,
            localBitmap = image
        )
        _uiChatHistory.update { it + currentTurn }

        val userTurnContent = if (contextString != null) {
            "<|context|>\n$contextString\n</context|>\n$displayedUserMessage"
        } else {
            displayedUserMessage
        }

        val promptForModel = when (_activeModel.value) {
            is LlamaModel.TinyLlama1B -> "\n<|user|>\n$userTurnContent\n<|assistant|>\n"
            is LlamaModel.Qwen4B -> "\n<|im_start|>user\n$userTurnContent<|im_end|>\n<|im_start|>assistant\n"
        }

        Log.d("LlamaPrompt", "Prompt sent to model: ${userTurnContent}.")
        val fullResponse = StringBuilder()

        val ttsSentenceBuffer = StringBuilder()
        llamaDataSource.generate(promptForModel)
            .catch { e ->
                _uiChatHistory.value.lastOrNull()?.let {
                    val errorTurn = it.copy(assistant = "Error: ${e.message}")
                    _uiChatHistory.update { history -> history.dropLast(1) + errorTurn }
                }
            }
            .onCompletion { cause ->
                _uiChatHistory.value.lastOrNull()?.let {
                    val finalResponseStr =
                        fullResponse.toString().substringBefore("<|user|>").trimEnd()
                    val completedTurn = it.copy(assistant = finalResponseStr)
                    _uiChatHistory.update { history -> history.dropLast(1) + completedTurn }

                    val conversationId = currentConversationId
                    if (conversationId != null) {
                        val newTurn = ChatTurnEntity(
                            conversationId = conversationId,
                            user = completedTurn.user,
                            assistant = completedTurn.assistant,
                            imagePath = cloudImageUrl
                        )
                        localDataSource.insertTurns(listOf(newTurn))
                        localDataSource.updateConversationTimestamp(
                            conversationId = conversationId,
                            timestamp = System.currentTimeMillis()
                        )
                    }
                }

                if (isTtsEnabled && ttsSentenceBuffer.isNotBlank()) {
                    var lastWords = ttsSentenceBuffer.toString().trim()
                    lastWords = lastWords.substringBefore("<|user|>")
                    if (lastWords.isNotBlank()) {
                        ttsManager.speakStream(lastWords)
                    }
                    ttsSentenceBuffer.clear()
                }

                if (cause == null) llamaDataSource.finalizeTurn()
                _isLoading.value = false
            }
            .collect { nextPiece ->
                fullResponse.append(nextPiece)
                _uiChatHistory.value.lastOrNull()?.let {
                    val currentAssistantResponse = it.assistant
                    val updatedTurn = it.copy(assistant = currentAssistantResponse + nextPiece)
                    _uiChatHistory.update { history -> history.dropLast(1) + updatedTurn }
                }

                if (isTtsEnabled) {
                    ttsSentenceBuffer.append(nextPiece)
                    val sentenceEnders = listOf(".", "!", "?", ",", ";", ":", "\n")
                    if (sentenceEnders.any { ttsSentenceBuffer.endsWith(it) }) {
                        var sentenceToSpeak = ttsSentenceBuffer.toString().trim()
                        sentenceToSpeak = sentenceToSpeak.substringBefore("<|user|>")

                        if (sentenceToSpeak.isNotBlank()) {
                            ttsManager.speakStream(sentenceToSpeak)
                        }
                        ttsSentenceBuffer.clear()
                    }
                }

                if (fullResponse.contains("<|user|>")) {
                    throw CancellationException("Runaway generation detected")
                }
            }
    }

    private suspend fun saveCurrentConversationState() {
        val id = currentConversationId
        val model = _activeModel.value
        if (id != null && _uiChatHistory.value.isNotEmpty()) {
            val cachePath = llamaDataSource.saveKVCache(id, model.fileName)
            if (cachePath != null) {
                val conversation = localDataSource.getConversationById(id)
                if (conversation != null) {
                    val updatedPaths = conversation.kvCachePaths.toMutableMap()
                    updatedPaths[model.fileName] = cachePath
                    val pathsAsJson = gson.toJson(updatedPaths)
                    localDataSource.updateKvCachePaths(id, pathsAsJson)
                }
            }
        }
    }

    override suspend fun startNewChat() {
        saveCurrentConversationState()
        llamaDataSource.resetContext()
        ttsManager.stopAndClearQueue()
        _uiChatHistory.value = emptyList()
        currentConversationId = null
        _modelResponseText.value = "I await your next trivial question."
    }

    override suspend fun loadConversation(conversationId: String) {
        if (_isLoading.value) return
        saveCurrentConversationState()
        ttsManager.stopAndClearQueue()
        _isLoading.value = true
        _modelResponseText.value = "Loading past conversation..."
        _uiChatHistory.value = emptyList()

        try {
            val conversation = localDataSource.getConversationById(conversationId)
                ?: throw Exception("Conversation not found")
            val turnsFromDb = localDataSource.getTurnsForConversation(conversationId)

            val cachePathForCurrentModel = conversation.kvCachePaths[_activeModel.value.fileName]
            var cacheLoaded = false

            if (cachePathForCurrentModel != null && File(cachePathForCurrentModel).exists()) {
                cacheLoaded = llamaDataSource.loadKVCache(cachePathForCurrentModel)
            }

            if (!cacheLoaded) {
                Log.w("ChatRepository", "SLOW PATH: Replaying text to generate cache.")
                llamaDataSource.resetContext()
                for (turnEntity in turnsFromDb) {
                    val promptForReplay = when (_activeModel.value) {
                        is LlamaModel.TinyLlama1B -> "\n<|user|>\n${turnEntity.user}\n<|assistant|>\n${turnEntity.assistant}"
                        is LlamaModel.Qwen4B -> "\n<|im_start|>user\n${turnEntity.user}<|im_end|>\n<|im_start|>assistant\n${turnEntity.assistant}<|im_end|>"
                    }
                    llamaDataSource.generate(promptForReplay).collect {}
                    llamaDataSource.finalizeTurn()
                }
                saveCurrentConversationState()
            }

            val loadedChatTurns = turnsFromDb.map { turnEntity ->
                // Now we pass the imagePath, so the UI can load it.
                ChatTurn(
                    user = turnEntity.user,
                    assistant = turnEntity.assistant,
                    imagePath = turnEntity.imagePath,
                    localBitmap = null // No local bitmap when loading from history
                )
            }
            _uiChatHistory.value = loadedChatTurns
            currentConversationId = conversationId
            _modelResponseText.value = "Conversation loaded. Continue where you left off."
        } catch (e: Exception) {
            Log.e("ChatRepository", "Failed to load conversation", e)
            startNewChat()
            _modelResponseText.value = "Error loading conversation. Started a new chat."
        } finally {
            _isLoading.value = false
        }
    }

    override suspend fun triggerModelReload() {
        startNewChat()
        loadModel()
    }

    override suspend fun hasLocalData(): Boolean {
        return localDataSource.getAllConversations().first().isNotEmpty()
    }

    override suspend fun migrateLocalToCloud(userId: String): Boolean {
        // This function is now superseded by the more specific `uploadUnsyncedData`.
        // I will keep it for now.
        // For now, let's make it call the new function.
        return uploadUnsyncedData(userId, 0L).success
    }

    override suspend fun migrateCloudToLocal(userId: String) {
        Log.i("ChatRepository", "Starting FULL Cloud -> Local sync.")
        // Clear all existing local data.
        clearLocalChatData()

        // Manually perform a ONE-TIME fetch of all data from the cloud.
        // We do this because the real-time listener won't re-fire if the cloud data hasn't changed.
        try {
            Log.d("ChatRepository", "Fetching all conversations from cloud for migration...")
            val cloudConversations = chatCloudDataSource.getConversationsFlow(userId).first()
            if (cloudConversations.isNotEmpty()) {
                // Insert the fetched conversations into our now-empty local DB.
                localDataSource.insertConversations(cloudConversations)

                // For each conversation, fetch its turns and insert them.
                cloudConversations.forEach { conversation ->
                    val turns = chatCloudDataSource.getTurnsForConversation(userId, conversation.id)
                    if (turns.isNotEmpty()) {
                        localDataSource.insertTurns(turns)
                    }
                }
                Log.i("ChatRepository", "Successfully migrated ${cloudConversations.size} conversations from cloud to local.")
            }
        } catch (e: Exception) {
            Log.e("ChatRepository", "Failed during manual cloud to local migration.", e)
            // If the migration fails, the local data will be empty, which is a safe state.
        }
        // Reset the active chat state to prevent inconsistencies and crashes.
        startNewChat()
    }

    override suspend fun clearLocalChatData() {
        localDataSource.clearAllChatData()
    }

    override suspend fun hasCloudData(userId: String): Boolean {
        return chatCloudDataSource.hasCloudData(userId)
    }

    override suspend fun hasUnsyncedData(lastSyncTimestamp: Long): Boolean {
        val localConversations = localDataSource.getAllConversations().first()
        return localConversations.any { it.lastModified > lastSyncTimestamp }
    }

    override suspend fun uploadUnsyncedData(userId: String, lastSyncTimestamp: Long): SyncResult {
        _isLoading.value = true
        return try {
            Log.i("ChatRepository", "Starting upload of unsynced data for user $userId")

            val allLocalConversations = localDataSource.getAllConversations().first()
            val unsyncedConversations = allLocalConversations.filter {
                it.lastModified > lastSyncTimestamp
            }

            if (unsyncedConversations.isEmpty()) {
                Log.i("ChatRepository", "No unsynced conversations to upload.")
                SyncResult(uploaded = 0, success = true)
            } else {
                Log.i("ChatRepository", "Found ${unsyncedConversations.size} unsynced conversations to upload.")
                val turnsMap = mutableMapOf<String, List<ChatTurnEntity>>()
                unsyncedConversations.forEach { conversation ->
                    turnsMap[conversation.id] = localDataSource.getTurnsForConversation(conversation.id)
                }
                chatCloudDataSource.uploadConversations(userId, unsyncedConversations, turnsMap)
                Log.i("ChatRepository", "Upload of unsynced data finished successfully.")
                SyncResult(uploaded = unsyncedConversations.size, success = true)
            }
        } catch (e: Exception) {
            Log.e("ChatRepository", "Upload of unsynced data FAILED", e)
            SyncResult(success = false)
        } finally {
            _isLoading.value = false
        }
    }

    override suspend fun toggleFavorite(conversationId: String) {
        val conversation = localDataSource.getConversationById(conversationId) ?: return
        val newFavoriteState = !conversation.isFavorite
        val timestamp = System.currentTimeMillis()

        localDataSource.setFavoriteStatus(conversationId, newFavoriteState, timestamp)

        authRepository.currentUser.value?.let { user ->
            if (!user.isGuest) {
                // Use the robust upload function instead of the raw cloud source
                uploadUnsyncedData(user.uid, timestamp - 1)
            }
        }
    }

    override suspend fun deleteConversation(conversationId: String) {
        val timestamp = System.currentTimeMillis()
        localDataSource.softDeleteConversation(conversationId, timestamp)
        Log.i("ChatRepository", "Soft-deleted conversation $conversationId locally.")

        if (currentConversationId == conversationId) {
            Log.i("ChatRepository", "Deleted conversation was the active one. Starting a new chat.")
            startNewChat()
        }

        authRepository.currentUser.value?.let { user ->
            if (!user.isGuest) {
                Log.i("ChatRepository", "Propagating delete to cloud for conversation $conversationId.")
                // We can just call the upload function directly. It will find the
                // soft-deleted conversation because its timestamp was just updated.
                uploadUnsyncedData(user.uid, timestamp - 1)
            }
        }
    }

    override fun onSignOut() {
        Log.i("ChatRepository", "Sign-out detected. Cancelling cloud sync job.")
        cloudSyncJob?.cancel()
        cloudSyncJob = null
    }

    fun release() {
        repositoryScope.launch {
            saveCurrentConversationState()
        }
        llamaDataSource.release()
    }

}