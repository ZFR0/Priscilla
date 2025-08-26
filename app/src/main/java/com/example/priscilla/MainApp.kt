package com.example.priscilla

import android.graphics.drawable.AnimatedImageDrawable
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import com.example.priscilla.ui.camera.CameraScreen
import com.example.priscilla.ui.chat.ChatScreen
import com.example.priscilla.ui.history.ChatDetailScreen
import com.example.priscilla.ui.history.HistoryScreen
import com.example.priscilla.ui.profile.CloudSyncScreen
import com.example.priscilla.ui.profile.ProfileScreen
import com.example.priscilla.ui.profile.ThemeScreen
import com.example.priscilla.ui.reminders.RemindersScreen
import com.example.priscilla.ui.settings.SettingsScreen
import kotlinx.coroutines.delay
import com.example.priscilla.ui.profile.VoiceOptionsScreen
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.detectReorderAfterLongPress
import org.burnoutcrew.reorderable.rememberReorderableLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.sp
import org.burnoutcrew.reorderable.reorderable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Chat : Screen("chat", "Chat", Icons.Default.Face)
    data object History : Screen("history", "History", Icons.Default.Book)
    data object Reminders : Screen("reminders", "Reminders", Icons.Default.Alarm)
    data object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    data object ChatDetail : Screen("chat_detail/{conversationId}", "Chat Detail", Icons.AutoMirrored.Filled.ArrowBack ) {
        fun createRoute(conversationId: String) = "chat_detail/$conversationId"
    }
    data object Camera : Screen("camera", "Camera", Icons.Default.Camera)
    data object Profile : Screen("profile", "Profile", Icons.Default.Person)
    data object ThemeCustomization : Screen("theme", "Theme", Icons.Default.Palette)
    data object CloudSync : Screen("cloud_sync", "Cloud Sync", Icons.Default.CloudUpload)
    data object VoiceOptions : Screen("voice_options", "Voice Options", Icons.Default.RecordVoiceOver)
}

@RequiresApi(Build.VERSION_CODES.P)
@Composable
fun PriscillaApp() {
    val navController = rememberNavController()
    val mainViewModel: MainViewModel = viewModel(factory = MainViewModel.Factory)
    var showGif by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        delay(2500L) // Let the GIF play for 2.5 seconds
        showGif = false
    }

    val context = LocalContext.current
    val imageLoader = (context as MainActivity).imageLoader

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                val navItems by mainViewModel.navItems.collectAsState()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                val reorderState = rememberReorderableLazyListState(
                    onMove = { from, to ->
                        mainViewModel.onNavItemMoved(from.index, to.index)
                    }
                )

                // Use NavigationBar for the correct background color and elevation.
                NavigationBar {
                    LazyRow(
                        state = reorderState.listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .reorderable(reorderState),
                        // Use Arrangement.SpaceAround to distribute items evenly
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(navItems, key = { it.route }) { screen ->
                            ReorderableItem(
                                reorderableState = reorderState,
                                key = screen.route
                            ) { isDragging ->
                                val isSelected = currentDestination?.hierarchy?.any { it.route == screen.route } == true

                                // Animate the scale: 1.1f when dragging, 1.0f otherwise.
                                val scale by animateFloatAsState(if (isDragging) 1.1f else 1.0f, label = "scale")

                                // Animate the elevation shadow: 8.dp when dragging, 0.dp otherwise.
                                val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "elevation")

                                // Set the background color: a darker/different variant when dragging, transparent otherwise.
                                // `surfaceVariant` is a great choice from the Material theme for this.
                                val backgroundColor = if (isDragging) {
                                    MaterialTheme.colorScheme.surfaceVariant
                                } else {
                                    Color.Transparent
                                }

                                ReorderableNavigationBarItem(
                                    modifier = Modifier
                                        .detectReorderAfterLongPress(reorderState)
                                        // 1. Apply the animated shadow for a "lifted" effect.
                                        .shadow(elevation, shape = MaterialTheme.shapes.medium)
                                        // 2. Apply the animated scale using a graphicsLayer for performance.
                                        .graphicsLayer {
                                            scaleX = scale
                                            scaleY = scale
                                        }
                                        // 3. Apply the background color and a rounded shape to create the "chip".
                                        .background(
                                            color = backgroundColor,
                                            shape = MaterialTheme.shapes.medium
                                        ),
                                    selected = isSelected,
                                    onClick = {
                                        if (!isSelected) {
                                            navController.navigate(screen.route) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    },
                                    icon = { Icon(screen.icon, contentDescription = screen.title) },
                                    label = { Text(screen.title, fontSize = 11.sp, maxLines = 1) }
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            AppNavHost(navController = navController, modifier = Modifier.padding(innerPadding))
        }

        AnimatedVisibility(
            visible = showGif,
            exit = fadeOut(tween(500))
        ) {
            AnimatedIntroScreen(imageLoader = imageLoader)
        }
    }
}

@Composable
private fun ReorderableNavigationBarItem(
    modifier: Modifier = Modifier,
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    label: @Composable () -> Unit
) {
    // We use a simple Box with a clickable modifier. This is the container.
    Box(
        modifier = modifier
            .clickable(
                onClick = onClick,
                // Remove ripple effect for a cleaner drag experience
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            )
            .padding(horizontal = 8.dp, vertical = 8.dp) // Padding for touch target
            .fillMaxHeight(),
        contentAlignment = Alignment.Center
    ) {
        // A Column to stack the icon and label vertically, just like the original.
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Determine content color based on selection state
            val contentColor = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }

            // Provide the calculated color to the icon and label
            CompositionLocalProvider(LocalContentColor provides contentColor) {
                icon()
                Spacer(modifier = Modifier.height(4.dp))
                label()
            }
        }
    }
}

@Composable
fun AppNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    // The ViewModels are now created here, in one central place, using their factories.
    val chatViewModel: ChatViewModel = viewModel(factory = ChatViewModel.Factory)
    val historyViewModel: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory)
    val settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
    val remindersViewModel: RemindersViewModel = viewModel() // This one had no new dependencies
    val profileViewModel: ProfileViewModel = viewModel(factory = ProfileViewModel.Factory)
    val themeViewModel: ThemeViewModel = viewModel(factory = ThemeViewModel.Factory)

    NavHost(
        navController = navController,
        startDestination = Screen.Chat.route,
        modifier = modifier
    ) {
        navigation(
            startDestination = "history_list", // A new, internal route name for the list screen
            route = Screen.History.route // The route for the entire graph is the tab's route
        ) {
            // Destination 1: The list of conversations
            composable("history_list") {
                HistoryScreen(
                    viewModel = historyViewModel,
                    navController = navController
                )
            }
            // Destination 2: The detail screen
            composable(
                route = Screen.ChatDetail.route,
                // The NavType is a StringType...
                arguments = listOf(navArgument("conversationId") { type = NavType.StringType })
            ) { backStackEntry ->
                // We get a String from the arguments
                val conversationId = backStackEntry.arguments?.getString("conversationId")
                if (conversationId != null) {
                    ChatDetailScreen(historyViewModel, conversationId, navController)
                }
            }
        }
        composable(Screen.Reminders.route) {
            RemindersScreen(remindersViewModel)
        }
        composable(Screen.Chat.route) {
            ChatScreen(chatViewModel, navController)
        }
        composable(Screen.ThemeCustomization.route) {
            ThemeScreen(navController, themeViewModel)
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                settingsViewModel = settingsViewModel,
                onReloadComplete = {
                    navController.navigate(Screen.Chat.route) {
                        popUpTo(navController.graph.startDestinationId)
                    }
                }
            )
        }
        composable(Screen.Camera.route) {
            CameraScreen(
                onImageCaptured = { bitmap ->
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("captured_image_bitmap", bitmap)
                    navController.popBackStack()
                },
                onError = { exception ->
                    // We now receive the full exception object
                    Log.e("AppNavHost", "Camera Error: ${exception.message}", exception)
                    navController.popBackStack()
                }
            )
        }
        composable(Screen.Profile.route) {
            ProfileScreen(profileViewModel, navController)
        }
        composable(Screen.CloudSync.route) {
            CloudSyncScreen(navController = navController)
        }
        composable(Screen.VoiceOptions.route) {
            VoiceOptionsScreen(navController = navController)
        }
    }
}

@RequiresApi(Build.VERSION_CODES.P)
@Composable
fun AnimatedIntroScreen(imageLoader: ImageLoader) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color = colorResource(id = R.color.splash_background_red)),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = R.drawable.priscilla_splash,
            imageLoader = imageLoader,
            contentDescription = "Priscilla Splash Animation",

            onState = { state ->
                // The `onState` callback gives us access to the painter's state.
                // We check if the state is `Success`.
                if (state is AsyncImagePainter.State.Success) {
                    // And then we can get the drawable from the result.
                    val drawable = state.result.drawable
                    if (drawable is AnimatedImageDrawable) {
                        drawable.start()
                    }
                }
            },
            contentScale = ContentScale.FillBounds,
            // We explicitly set the size of the Image composable to match
            // the size of the system splash screen's icon container.
            modifier = Modifier.size(192.dp).offset(y = (-15).dp).scale(0.95f)
        )
    }
}