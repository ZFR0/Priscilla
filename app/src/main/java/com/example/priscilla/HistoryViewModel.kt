package com.example.priscilla

import android.app.Application
import android.graphics.BitmapFactory
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.priscilla.data.ChatDatabase
import com.example.priscilla.data.Conversation
import com.example.priscilla.data.ConversationWithTurns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.text.AnnotatedString
import com.example.priscilla.data.ChatTurn
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce

data class ConversationDetail(
    val conversation: Conversation?,
    val turns: List<ChatTurn>
)

data class HistoryUiItem(
    val conversation: Conversation,
    val searchSnippet: AnnotatedString? = null
)

@OptIn(FlowPreview::class)
class HistoryViewModel(
    application: Application,
    private val chatRepository: ChatRepository
) : ViewModel() {
    private val chatDao = ChatDatabase.getDatabase(application).chatDao()

    val isLoading: StateFlow<Boolean> = chatRepository.isLoading


    // This holds the raw text from the search bar, updated on every key press.
    private val _searchQueryInput = MutableStateFlow("")
    val searchQueryInput: StateFlow<String> = _searchQueryInput.asStateFlow()

    // This will hold the query that the search logic actually uses.
    // It's updated only after the user stops typing for 1 second.
    private val _debouncedSearchQuery = MutableStateFlow("")

    // We store the full list from the database here.
    private val _allConversations = chatDao.getAllConversationsWithTurns()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // This flow specifically tracks if the underlying database list is empty.
    val isHistoryEmpty: StateFlow<Boolean> = _allConversations
        .map { it.isEmpty() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    init {
        viewModelScope.launch {
            _searchQueryInput
                .debounce(1000L) // Wait for 1 second of inactivity
                .collect { query ->
                    _debouncedSearchQuery.value = query
                }
        }
    }

    val historyItems: StateFlow<List<HistoryUiItem>> = _allConversations
        .combine(_debouncedSearchQuery) { conversations, query ->
            filterAndCreateSnippets(conversations, query)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _conversationDetailsState = MutableStateFlow<UiState<ConversationDetail>>(UiState.Loading)
    val conversationDetailsState: StateFlow<UiState<ConversationDetail>> = _conversationDetailsState.asStateFlow()

    fun onSearchQueryChanged(query: String) {
        _searchQueryInput.value = query
    }

    fun loadConversationDetails(conversationId: String) {
        viewModelScope.launch {
            chatDao.getConversationWithTurns(conversationId)
                .map<ConversationWithTurns?, UiState<ConversationDetail>> { conversationWithTurns ->
                    // LOG 1: Mapping started
                    Log.d("FlickerDebug", "Mapping database results to UI model...")
                    val turns = conversationWithTurns?.turns?.map { entity ->
                        ChatTurn(
                            user = entity.user,
                            assistant = entity.assistant,
                            imagePath = entity.imagePath,
                            localBitmap = null
                        )
                    } ?: emptyList()

                    UiState.Success(ConversationDetail(conversationWithTurns?.conversation, turns))
                }
                .onStart { emit(UiState.Loading) }
                .catch { e -> emit(UiState.Error(e.message ?: "An unknown error occurred")) }
                .collect { state ->
                    _conversationDetailsState.value = state
                }
        }
    }

    fun loadConversation(conversationId: String) {
        viewModelScope.launch {
            chatRepository.loadConversation(conversationId)
        }
    }

    fun toggleFavorite(conversationId: String) {
        viewModelScope.launch {
            chatRepository.toggleFavorite(conversationId)
        }
    }

    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            chatRepository.deleteConversation(conversationId)
        }
    }

    private fun filterAndCreateSnippets(
        conversations: List<ConversationWithTurns>,
        query: String
    ): List<HistoryUiItem> {
        if (query.isBlank()) {
            // If there's no query, just return all conversations with no snippet.
            return conversations.map { HistoryUiItem(it.conversation) }
        }

        val results = mutableListOf<HistoryUiItem>()
        val queryLower = query.lowercase()

        for (convoWithTurns in conversations) {
            // Concatenate all user and assistant messages into one searchable block.
            val fullText = convoWithTurns.turns.joinToString(separator = " ") {
                "${it.user} ${it.assistant}"
            }
            val fullTextLower = fullText.lowercase()

            // We only need to check if the query exists. The snippet generator will find it again.
            if (fullTextLower.contains(queryLower)) {
                // A match was found! Call the new snippet generator.
                val snippet = createSnippet(fullText, query)
                results.add(
                    HistoryUiItem(
                        conversation = convoWithTurns.conversation,
                        searchSnippet = snippet
                    )
                )
            }
        }
        return results
    }

    private fun createSnippet(fullText: String, query: String): AnnotatedString {
        val windowWords = 7 // Words before and after the query
        val words = fullText.split(Regex("\\s+")) // Split by any whitespace
        val queryLower = query.lowercase()

        // Find the index of the first word that starts our query match
        var characterCount = 0
        val matchStartIndex = fullText.lowercase().indexOf(queryLower)
        var wordIndexOfMatch = -1

        for ((index, word) in words.withIndex()) {
            if (characterCount >= matchStartIndex) {
                wordIndexOfMatch = index
                break
            }
            characterCount += word.length + 1 // +1 for the space
        }

        if (wordIndexOfMatch == -1) {
            // Fallback if something went wrong, should not happen if called correctly
            return AnnotatedString(fullText.take(100) + "...")
        }

        // Calculate the window, adjusting for edges
        var start = (wordIndexOfMatch - windowWords).coerceAtLeast(0)
        var end = (wordIndexOfMatch + windowWords + 1).coerceAtMost(words.size)

        // Adjust window if it's hitting an edge, to maintain total length
        val startDeficit = windowWords - (wordIndexOfMatch - start)
        if (startDeficit > 0) {
            end = (end + startDeficit).coerceAtMost(words.size)
        }

        val endDeficit = windowWords - (end - wordIndexOfMatch - 1)
        if (endDeficit > 0) {
            start = (start - endDeficit).coerceAtLeast(0)
        }

        // Get the sublist of words for our snippet
        val snippetWords = words.subList(start, end)
        val snippetText = snippetWords.joinToString(" ")

        // Add ellipses if the snippet doesn't start/end at the original text's boundaries
        val finalSnippetText = buildString {
            if (start > 0) append("... ")
            append(snippetText)
            if (end < words.size) append(" ...")
        }

        // Build the AnnotatedString with highlighting
        return androidx.compose.ui.text.buildAnnotatedString {
            append(finalSnippetText)
            val highlightStartIndex = finalSnippetText.lowercase().indexOf(queryLower)
            if (highlightStartIndex != -1) {
                addStyle(
                    style = androidx.compose.ui.text.SpanStyle(
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        background = androidx.compose.ui.graphics.Color.Yellow.copy(alpha = 0.5f)
                    ),
                    start = highlightStartIndex,
                    end = highlightStartIndex + query.length
                )
            }
        }
    }

    // Added ViewModel Factory
    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                modelClass: Class<T>,
                extras: CreationExtras
            ): T {
                val application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) as PriscillaApplication
                val chatRepository = application.appContainer.chatRepository
                return HistoryViewModel(application, chatRepository) as T
            }
        }
    }
}