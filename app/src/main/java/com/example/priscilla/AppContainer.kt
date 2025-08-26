package com.example.priscilla

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import com.example.priscilla.data.ChatDatabase
import com.example.priscilla.data.ChatTurn
import com.example.priscilla.data.ImageUploader
import com.example.priscilla.data.LlamaModel
import com.example.priscilla.data.PermissionManager
import com.example.priscilla.data.ReminderRepository
import com.example.priscilla.data.SettingsRepository
import com.example.priscilla.data.auth.PriscillaUser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import com.example.priscilla.data.source.LlamaDataSourceImpl
import com.example.priscilla.data.source.LocalDataSourceImpl
import com.example.priscilla.data.source.KnowledgeDataSourceImpl
import com.example.priscilla.data.repository.ChatRepositoryImpl
import com.example.priscilla.data.repository.AuthRepositoryImpl
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.example.priscilla.data.auth.VisualPreferences
import com.example.priscilla.data.repository.ProfileRepositoryImpl
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableStateFlow
import com.example.priscilla.data.source.ChatCloudDataSource
import com.example.priscilla.data.source.ChatFirestoreDataSourceImpl
import com.example.priscilla.data.SyncStateRepository
import com.example.priscilla.data.SyncStateRepositoryImpl
import com.example.priscilla.data.source.SettingsCloudDataSource
import com.example.priscilla.data.source.SettingsCloudDataSourceImpl
import com.example.priscilla.data.source.RemindersCloudDataSource
import com.example.priscilla.data.source.RemindersCloudDataSourceImpl
import com.example.priscilla.data.TtsManager
import com.example.priscilla.data.auth.UserProfile

//================================================================================
// 1. INTERFACES (The "Contracts")
//================================================================================


/**
 * A data class to hold the detailed results of a sync operation.
 * @param uploaded The number of items successfully uploaded.
 * @param downloaded The number of items successfully downloaded.
 * @param success True if the entire operation succeeded, false otherwise.
 */
data class SyncResult(
    val uploaded: Int = 0,
    val downloaded: Int = 0,
    val success: Boolean = true
)

/**
 * An interface for repositories that hold user-specific resources (like real-time listeners)
 * that must be cleaned up BEFORE a user is signed out.
 */
interface UserScopeCleanup {
    fun onSignOut()
}

/**
 * The single source of truth for all chat-related operations.
 * The ViewModel will interact with this, and this repository will orchestrate
 * the data sources to perform the work.
 */
interface ChatRepository : UserScopeCleanup {
    // State exposed to the ViewModel
    val uiChatHistory: StateFlow<List<ChatTurn>>
    val isModelInitialized: StateFlow<Boolean>
    val isLoading: StateFlow<Boolean>
    val modelResponseText: StateFlow<String>
    val activeModel: StateFlow<LlamaModel>
    val isTtsSpeaking: StateFlow<Boolean>

    // Events from the ViewModel
    suspend fun sendMessage(userMessage: String, image: Bitmap?)
    suspend fun startNewChat()
    suspend fun loadConversation(conversationId: String)
    suspend fun triggerModelReload()
    suspend fun hasLocalData(): Boolean
    suspend fun hasCloudData(userId: String): Boolean
    suspend fun migrateLocalToCloud(userId: String): Boolean
    suspend fun migrateCloudToLocal(userId: String)
    suspend fun clearLocalChatData()
    suspend fun hasUnsyncedData(lastSyncTimestamp: Long): Boolean
    suspend fun uploadUnsyncedData(userId: String, lastSyncTimestamp: Long): SyncResult
    suspend fun toggleFavorite(conversationId: String)
    suspend fun deleteConversation(conversationId: String)
}

/**
 * Handles all interactions with the native llama.cpp library.
 */
interface LlamaDataSource {
    suspend fun initialize(model: LlamaModel, params: com.example.priscilla.data.ModelParameters): Boolean
    fun generate(prompt: String): Flow<String>
    fun resetContext()
    fun finalizeTurn()
    suspend fun saveKVCache(conversationId: String, modelFileName: String): String?
    suspend fun loadKVCache(path: String): Boolean
    fun release()
}

/**
 * Handles fetching context-aware information from the internet or system.
 */
interface KnowledgeDataSource {
    suspend fun getContextString(prompt: String, image: Bitmap?, locationProvider: LocationProvider): String?
    suspend fun analyzeImage(image: Bitmap): List<String>
    suspend fun loadBitmapFromPath(path: String): Bitmap?
}

/**
 * Handles all interactions with the local Room database.
 */
interface LocalDataSource {
    // History functions
    fun getAllConversations(): Flow<List<com.example.priscilla.data.Conversation>>
    fun getConversationWithTurns(conversationId: String): Flow<com.example.priscilla.data.ConversationWithTurns?>
    suspend fun getTurnsForConversation(conversationId: String): List<com.example.priscilla.data.ChatTurnEntity>
    suspend fun getConversationById(conversationId: String): com.example.priscilla.data.Conversation?
    suspend fun getConversationByIdIncludingDeleted(conversationId: String): com.example.priscilla.data.Conversation?
    suspend fun setFavoriteStatus(conversationId: String, isFavorite: Boolean, timestamp: Long)
    suspend fun softDeleteConversation(conversationId: String, timestamp: Long)
    suspend fun insertConversation(conversation: com.example.priscilla.data.Conversation)
    suspend fun insertConversations(conversations: List<com.example.priscilla.data.Conversation>)
    suspend fun insertTurns(turns: List<com.example.priscilla.data.ChatTurnEntity>)
    suspend fun updateKvCachePaths(conversationId: String, pathsAsJson: String)
    suspend fun updateConversationTimestamp(conversationId: String, timestamp: Long)
    suspend fun clearAllChatData()
    suspend fun deleteTurnsForConversation(conversationId: String)
}

/**
 * A repository to handle all authentication-related operations.
 */
interface AuthRepository {
    // Exposes a Flow of the current user's state. The UI will observe this.
    // It will emit a new value whenever the auth state changes (e.g., login, logout).
    val currentUser: StateFlow<PriscillaUser?>

    // Triggers the Google Sign-In flow.
    suspend fun signInWithGoogle(tokenId: String): Result<Unit>

    // Signs the user out.
    fun signOut()

    fun registerUserScopeCleanup(cleanup: UserScopeCleanup)
}

/**
 * Handles saving and loading user-specific preferences.
 */
interface ProfileRepository : UserScopeCleanup {
    fun getUserProfileFlow(userId: String?): Flow<UserProfile>
    suspend fun updateUserProfile(userId: String, profile: UserProfile)
    suspend fun updateUserPhotoUrl(userId: String, url: String)
}

interface SyncStateRepository {
    fun getLocalLastSyncTimestampFlow(): Flow<Long>
    suspend fun getCloudLastSyncTimestamp(userId: String): Long
    suspend fun updateLastSyncTimestamp(userId: String, timestamp: Long)
}

//================================================================================
// 2. DEPENDENCY INJECTION CONTAINER
//================================================================================

/**
 * A container to hold singleton instances that need to be shared across the app.
 * This is our manual Dependency Injection setup.
 */
val draftVisualPreferences: MutableStateFlow<VisualPreferences?> = MutableStateFlow(null)

interface AppContainer {
    val chatRepository: ChatRepository
    val reminderRepository: ReminderRepository
    val authRepository: AuthRepository
    val profileRepository: ProfileRepository
    val syncStateRepository: SyncStateRepository
    val settingsRepository: SettingsRepository
    val draftVisualPreferences: MutableStateFlow<VisualPreferences?>
    val ttsManager: TtsManager
    val imageUploader: ImageUploader
}

/**
 * The Application class, which will be the owner of the AppContainer.
 */
class PriscillaApplication : Application() {
    lateinit var appContainer: AppContainer

    override fun onCreate() {
        super.onCreate()
        appContainer = DefaultAppContainer(this)
    }
}

/**
 * The default implementation of the AppContainer. This is where we will
 * construct all our major singleton objects.
 */
class DefaultAppContainer(private val context: Context) : AppContainer {

    // --- Data Source Implementations ---
    private val llamaDataSource: LlamaDataSource by lazy {
        LlamaDataSourceImpl(context)
    }

    private val localDataSource: LocalDataSource by lazy {
        val dao = ChatDatabase.getDatabase(context).chatDao()
        LocalDataSourceImpl(dao)
    }

    private val knowledgeDataSource: KnowledgeDataSource by lazy {
        KnowledgeDataSourceImpl(context, reminderRepository)
    }

    private val chatCloudDataSource: ChatCloudDataSource by lazy {
        ChatFirestoreDataSourceImpl(Firebase.firestore)
    }

    private val settingsCloudDataSource: SettingsCloudDataSource by lazy {
        SettingsCloudDataSourceImpl(Firebase.firestore)
    }

    private val remindersCloudDataSource: RemindersCloudDataSource by lazy {
        RemindersCloudDataSourceImpl(Firebase.firestore)
    }

    override val imageUploader: ImageUploader by lazy {
        ImageUploader()
    }

    override val ttsManager: TtsManager by lazy {
        TtsManager(context)
    }

    override val authRepository: AuthRepository by lazy {
        AuthRepositoryImpl(Firebase.auth)
    }

    private val permissionManager: PermissionManager by lazy {
        PermissionManager(context)
    }

    override val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(
            context = context,
            authRepository = authRepository,
            settingsCloudDataSource = settingsCloudDataSource,
            permissionManager = permissionManager
        )
    }

    // --- Repository Implementations ---

    override val chatRepository: ChatRepository by lazy {
        ChatRepositoryImpl(
            context = context,
            llamaDataSource = llamaDataSource,
            localDataSource = localDataSource,
            knowledgeDataSource = knowledgeDataSource,
            settingsRepository = settingsRepository,
            chatCloudDataSource = chatCloudDataSource,
            authRepository = authRepository,
            ttsManager = ttsManager,
            imageUploader = imageUploader
        )
    }

    override val reminderRepository: ReminderRepository by lazy {
        ReminderRepository(
            context = context,
            chatDao = ChatDatabase.getDatabase(context).chatDao(),
            authRepository = authRepository,
            remindersCloudDataSource = remindersCloudDataSource
        )
    }

    override val profileRepository: ProfileRepository by lazy {
        ProfileRepositoryImpl(Firebase.firestore)
    }

    override val syncStateRepository: SyncStateRepository by lazy {
        SyncStateRepositoryImpl(context, Firebase.firestore)
    }

    override val draftVisualPreferences: MutableStateFlow<VisualPreferences?> = MutableStateFlow(null)

    init {
        authRepository.registerUserScopeCleanup(chatRepository)
        authRepository.registerUserScopeCleanup(settingsRepository)
        authRepository.registerUserScopeCleanup(reminderRepository)
        authRepository.registerUserScopeCleanup(profileRepository)
    }
}