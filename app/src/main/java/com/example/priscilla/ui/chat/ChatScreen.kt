package com.example.priscilla.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.example.priscilla.ChatViewModel
import com.example.priscilla.data.UserIntent
import com.example.priscilla.ui.theme.FrameStyles
import com.example.priscilla.ui.theme.drawCustomFrame
import com.example.priscilla.MainViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.priscilla.R
import com.example.priscilla.data.auth.BorderStyle

@Composable
fun ChatScreen(viewModel: ChatViewModel, navController: NavController) {
    val mainViewModel: MainViewModel = viewModel(factory = MainViewModel.Factory)
    val preferences by mainViewModel.visualPreferences.collectAsState()
    val currentBorderStyle = preferences.borderStyle

    val prompt by viewModel.prompt
    val isLoading by viewModel.isLoading.collectAsState()
    val isInitialized by viewModel.isInitialized.collectAsState()
    val chatHistory by viewModel.uiChatHistory.collectAsState()
    val responseText by viewModel.responseText.collectAsState()
    val detectedIntent by viewModel.detectedIntent.collectAsState()
    val isTtsSpeaking by viewModel.isTtsSpeaking.collectAsState()

    val listState = rememberLazyListState()
    val context = LocalContext.current


    var selectedImageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showAttachmentOptions by remember { mutableStateOf(false) }

    val navBackStackEntry = navController.currentBackStackEntry
    val imageBitmap by navBackStackEntry?.savedStateHandle?.getStateFlow<Bitmap?>("captured_image_bitmap", null)
        ?.collectAsState() ?: remember { mutableStateOf(null) }

    LaunchedEffect(imageBitmap) {
        imageBitmap?.let {
            selectedImageBitmap = it
            navBackStackEntry?.savedStateHandle?.set("captured_image_bitmap", null)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            uri?.let {
                selectedImageBitmap = if (Build.VERSION.SDK_INT < 28) {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, it)
                } else {
                    val source = ImageDecoder.createSource(context.contentResolver, it)
                    ImageDecoder.decodeBitmap(source)
                }
            }
        }
    )

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                // Navigate to our new CameraScreen
                navController.navigate(com.example.priscilla.Screen.Camera.route)
            } else {
                Toast.makeText(context, "Camera permission is required.", Toast.LENGTH_SHORT).show()
            }
        }
    )

    LaunchedEffect(chatHistory.lastOrNull()?.assistant) {
        if (isLoading && chatHistory.isNotEmpty()) {
            listState.animateScrollToItem(chatHistory.lastIndex)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Priscilla AI", style = MaterialTheme.typography.headlineMedium)
            Button(
                onClick = {
                    viewModel.startNewChat()
                    selectedImageBitmap = null
                },
                enabled = !isLoading && isInitialized
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Chat")
                Spacer(modifier = Modifier.width(4.dp))
                Text("New Chat")
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                if (chatHistory.isEmpty()) {
                    item {
                        Text(
                            text = responseText,
                            modifier = Modifier
                                .fillParentMaxSize()
                                .padding(16.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
                items(chatHistory, key = { it.user + it.assistant }) { turn ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(text = "You:", fontWeight = FontWeight.Bold)
                        if (turn.imagePath != null) {
                            AsyncImage(
                                model = turn.imagePath,
                                contentDescription = "User attached image",
                                modifier = Modifier
                                    .padding(top = 8.dp, bottom = 4.dp)
                                    .fillMaxWidth()
                                    .heightIn(max = 240.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop,
                                placeholder = painterResource(id = R.drawable.icon_splash_final)
                            )
                        } else if (turn.localBitmap != null) {
                            Image(
                                bitmap = turn.localBitmap.asImageBitmap(),
                                contentDescription = "User attached image",
                                modifier = Modifier
                                    .padding(top = 8.dp, bottom = 4.dp)
                                    .fillMaxWidth()
                                    .heightIn(max = 240.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                        if (turn.user.isNotBlank()) {
                            Text(text = turn.user)
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Priscilla:",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        // Conditionally show the icon
                        val isLastTurn = turn === chatHistory.lastOrNull()
                        if (isTtsSpeaking && isLastTurn) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = "Speaking",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Text(text = turn.assistant)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                }
            }
            if (isLoading && chatHistory.isNotEmpty()) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {}
            }
        }

        Column {
            if (selectedImageBitmap != null) {
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Image(
                        bitmap = selectedImageBitmap!!.asImageBitmap(),
                        contentDescription = "Selected image",
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    IconButton(
                        onClick = { selectedImageBitmap = null },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 8.dp, y = (-8).dp)
                            .background(MaterialTheme.colorScheme.surface, CircleShape)
                            .size(24.dp)
                    ) {
                        Icon(Icons.Default.Cancel, contentDescription = "Clear Image", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    SpeedDialButton(
                        isVisible = showAttachmentOptions,
                        icon = Icons.Default.PhotoCamera,
                        offsetY = (-60).dp,
                        onClick = {
                            showAttachmentOptions = false
                            val permissionCheckResult = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                            if (permissionCheckResult == PackageManager.PERMISSION_GRANTED) {
                                navController.navigate(com.example.priscilla.Screen.Camera.route)
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        }
                    )
                    SpeedDialButton(
                        isVisible = showAttachmentOptions,
                        icon = Icons.Default.PhotoLibrary,
                        offsetY = (-115).dp,
                        onClick = {
                            showAttachmentOptions = false
                            galleryLauncher.launch("image/*")
                        }
                    )
                    val rotation: Float by animateFloatAsState(if (showAttachmentOptions) 45f else 0f, label = "rotation")
                    FloatingActionButton(
                        onClick = { showAttachmentOptions = !showAttachmentOptions },
                        modifier = Modifier.rotate(rotation),
                        elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Attach")
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    AnimatedVisibility(
                        visible = detectedIntent != null,
                        modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            detectedIntent?.let { IntentIcon(intent = it.intent) }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Priscilla understands...",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // We check the border style to decide which composable to show.
                    if (currentBorderStyle == BorderStyle.DEFAULT) {
                        // If style is DEFAULT, show the standard OutlinedTextField.
                        OutlinedTextField(
                            value = prompt, onValueChange = { viewModel.onPromptChanged(it) },
                            label = { Text("Address me...") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 8.dp),
                            enabled = !isLoading && isInitialized
                        )
                    } else {
                        // If style is CUSTOM, show our Box-wrapped transparent TextField.
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            // The `when` statement is still useful here for future styles.
                            val frameStyle = when (currentBorderStyle) {
                                BorderStyle.RED_JEWEL -> FrameStyles.RED_JEWEL_TEXT_INPUT_FRAME
                                BorderStyle.SHARP_GREEN -> FrameStyles.SHARP_GREEN_TEXT_INPUT_FRAME
                                BorderStyle.BLUE_DROPLET -> FrameStyles.BLUE_DROPLET_TEXT_INPUT_FRAME
                                else -> null // For DEFAULT or unimplemented styles, do nothing
                            }

                            // If a style was selected, draw the frame
                            if (frameStyle != null) {
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .heightIn(min = 56.dp)
                                        .drawCustomFrame(style = frameStyle) // <-- NOW DYNAMIC
                                )
                            }

                            // The transparent TextField for our custom frames
                            TextField(
                                value = prompt,
                                onValueChange = { viewModel.onPromptChanged(it) },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isLoading && isInitialized,
                                placeholder = { Text("Address me...") },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    disabledIndicatorColor = Color.Transparent,
                                ),
                                textStyle = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        viewModel.sendMessage(
                            userMessage = viewModel.prompt.value,
                            image = selectedImageBitmap
                        )
                        selectedImageBitmap = null
                    },
                    enabled = !isLoading && isInitialized && (prompt.isNotBlank() || selectedImageBitmap != null)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                }
            }
        }
    }
}

@Composable
fun SpeedDialButton(
    isVisible: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    offsetY: Dp,
    onClick: () -> Unit
) {
    val animatedOffsetY by animateDpAsState(
        targetValue = if (isVisible) offsetY else 0.dp,
        label = "offsetY"
    )

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        FloatingActionButton(
            onClick = onClick,
            modifier = Modifier.offset {
                IntOffset(x = 0, y = animatedOffsetY.roundToPx())
            },
            containerColor = MaterialTheme.colorScheme.secondary,
            elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondary)
        }
    }
}

@Composable
private fun IntentIcon(intent: UserIntent) {
    val icon = when (intent) {
        UserIntent.GET_WEATHER -> Icons.Default.WbSunny
        UserIntent.GET_TIME -> Icons.Default.Schedule
        UserIntent.GET_NEWS -> Icons.Default.Newspaper
        UserIntent.GET_LOCATION -> Icons.Default.LocationOn
        UserIntent.GET_MATH_RESULT -> Icons.Default.Functions
        UserIntent.GET_TRANSLATION -> Icons.Default.Translate
        UserIntent.CREATE_REMINDER -> Icons.Default.Alarm
    }
    Icon(
        imageVector = icon,
        contentDescription = intent.name,
        modifier = Modifier.size(20.dp),
        tint = MaterialTheme.colorScheme.primary
    )
}