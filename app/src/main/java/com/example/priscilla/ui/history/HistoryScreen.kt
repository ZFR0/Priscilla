package com.example.priscilla.ui.history

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.priscilla.HistoryViewModel
import com.example.priscilla.Screen
import androidx.compose.ui.Alignment
import com.example.priscilla.UiState
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import coil.compose.AsyncImage
import com.example.priscilla.R

@Composable
fun HistoryScreen(viewModel: HistoryViewModel, navController: NavController) {
    val historyItems by viewModel.historyItems.collectAsState()
    val searchQuery by viewModel.searchQueryInput.collectAsState()
    val isHistoryEmpty by viewModel.isHistoryEmpty.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("Conversation History", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))

            // Only show the search bar if the underlying history is NOT empty.
            if (!isHistoryEmpty) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.onSearchQueryChanged(it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search history...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search Icon") },
                    trailingIcon = {
                        // Show a clear button only when there is text
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear Search")
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        keyboardController?.hide() // Hide keyboard on search action
                    })
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
        if (historyItems.isEmpty()) {
            item {
                Text(
                    if (searchQuery.isNotBlank()) "No conversations found for your search."
                    else "No past conversations found."
                )
            }
        } else {
            items(historyItems, key = { it.conversation.id }) { item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    onClick = {
                        navController.navigate(Screen.ChatDetail.createRoute(item.conversation.id))
                    }
                ) {
                    Column(
                        modifier = Modifier.padding(
                            start = 16.dp,
                            end = 16.dp,
                            top = 16.dp,
                            bottom = if (item.searchSnippet != null) 8.dp else 16.dp
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item.conversation.firstMessagePreview,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                                maxLines = 2
                            )
                            if (item.conversation.isFavorite) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = "Favorite",
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.padding(start = 16.dp)
                                )
                            }
                        }

                        AnimatedVisibility(
                            visible = item.searchSnippet != null,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            item.searchSnippet?.let { snippet ->
                                Text(
                                    text = snippet,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(top = 8.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    historyViewModel: HistoryViewModel,
    conversationId: String,
    navController: NavController
) {
    LaunchedEffect(conversationId) {
        historyViewModel.loadConversationDetails(conversationId)
    }
    val conversationState by historyViewModel.conversationDetailsState.collectAsState()
    val isLoading by historyViewModel.isLoading.collectAsState()

    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Conversation") },
            text = { Text("Are you sure you want to permanently delete this conversation? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        historyViewModel.deleteConversation(conversationId)
                        showDeleteDialog = false
                        // Navigate back after deletion
                        navController.popBackStack()
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chat Details") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Show actions only when the conversation has loaded successfully
                    if (conversationState is UiState.Success) {
                        val isFavorite = (conversationState as UiState.Success<com.example.priscilla.ConversationDetail>).data.conversation?.isFavorite ?: false

                        // Favorite Button
                        IconButton(onClick = { historyViewModel.toggleFavorite(conversationId) }) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "Toggle Favorite"
                            )
                        }

                        // Delete Button
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Conversation"
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (isLoading) return@ExtendedFloatingActionButton

                    historyViewModel.loadConversation(conversationId)

                    navController.navigate(Screen.Chat.route) {
                        popUpTo(navController.graph.startDestinationId) {}
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Edit, "Continue Chat")
                    }
                },
                text = {
                    if (isLoading) {
                        Text("Loading...")
                    } else {
                        Text("Continue this chat")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = conversationState) {
                is UiState.Loading -> {
                    // Show a loading indicator in the center of the screen
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is UiState.Success -> {
                    // Once the data is loaded, show the LazyColumn with the chat turns
                    val chatTurns = state.data.turns
                    if (chatTurns.isEmpty()) {
                        // Optional: Handle case where a conversation has no messages
                        Text(
                            "This conversation is empty.",
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp)
                        ) {
                            items(chatTurns) { turn ->
                                // The content of the item is the same as before
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(text = "You:", fontWeight = FontWeight.Bold)
                                    if (turn.imagePath != null) {
                                        AsyncImage(
                                            model = turn.imagePath,
                                            contentDescription = "User attached image from history",
                                            modifier = Modifier
                                                .padding(top = 8.dp, bottom = 4.dp)
                                                .fillMaxWidth()
                                                .heightIn(max = 240.dp)
                                                .clip(RoundedCornerShape(12.dp)),
                                            contentScale = ContentScale.Crop,
                                            placeholder = painterResource(id = R.drawable.icon_splash_final)
                                        )
                                    }
                                    if (turn.user.isNotBlank()) {
                                        Text(text = turn.user)
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Priscilla:",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Text(text = turn.assistant)
                                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                            }
                        }
                    }
                }
                is UiState.Error -> {
                    // Optional: Handle the error case
                    Text(
                        "Failed to load conversation: ${state.message}",
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }
}