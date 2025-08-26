package com.example.priscilla

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.animation.AnticipateInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.animation.doOnEnd
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import com.example.priscilla.ui.theme.PriscillaTheme
import coil.request.ImageRequest

class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels { MainViewModel.Factory }

    val imageLoader: ImageLoader by lazy {
        ImageLoader.Builder(this)
            .components {
                // Register the correct decoder for the Android version.
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
    }

    @SuppressLint("NewApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        preloadGif()

        splashScreen.setKeepOnScreenCondition {
            mainViewModel.isThemeLoading.value
        }

        // Instead of removing the view, we now animate it out.
        splashScreen.setOnExitAnimationListener { splashScreenView ->
            try {
                val fadeOut = ObjectAnimator.ofFloat(
                    splashScreenView.iconView,
                    View.ALPHA,
                    1f,
                    0f
                )
                fadeOut.interpolator = AnticipateInterpolator()
                fadeOut.duration = 500L
                fadeOut.doOnEnd {
                    splashScreenView.remove()
                }
                fadeOut.start()
            } catch (e: Exception) {
                // If anything goes wrong with the animation, just remove the splash screen
                splashScreenView.remove()
            }
        }

        // Since we are no longer managing a second splash screen in Compose,
        // the logic becomes much cleaner.
        setContent {
            // No need for LaunchedEffect or showArtificialSplashScreen state anymore.

            PriscillaTheme(mainViewModel = mainViewModel) {
                // We just show the main app.
                // It will be drawn underneath the fading system splash screen.
                PriscillaApp()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // This ensures that when the main activity is destroyed (e.g., app is fully closed),
        // we release the Text-to-Speech engine's resources to prevent memory leaks.
        // We get the singleton instance of the TtsManager from our AppContainer.
        (application as PriscillaApplication).appContainer.ttsManager.shutdown()
    }

    // This function now correctly uses the Activity's `imageLoader` property.
    private fun preloadGif() {
        val request = ImageRequest.Builder(this)
            .data(R.drawable.priscilla_splash)
            .build()
        // Use the custom loader to enqueue the request.
        this.imageLoader.enqueue(request)
    }
}