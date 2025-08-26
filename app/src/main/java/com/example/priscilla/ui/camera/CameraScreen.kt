package com.example.priscilla.ui.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import java.util.concurrent.Executor

@Composable
fun CameraScreen(
    onImageCaptured: (Bitmap) -> Unit,
    onError: (ImageCaptureException) -> Unit
) {
    val context = LocalContext.current
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    val imageCapture = remember { ImageCapture.Builder().build() }

    Box(modifier = Modifier.fillMaxSize()) {
        CameraPreview(
            modifier = Modifier.fillMaxSize(),
            imageCapture = imageCapture,
            lensFacing = lensFacing
        )

        // UI Controls (IconButton, etc.) remain the same
        IconButton(
            onClick = {
                captureImage(
                    imageCapture = imageCapture,
                    context = context,
                    onImageCaptured = onImageCaptured,
                    onError = onError
                )
            },
            modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp).size(80.dp),
            colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Default.Camera, contentDescription = "Take Picture", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(48.dp))
        }

        IconButton(
            onClick = {
                lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                    CameraSelector.LENS_FACING_FRONT
                } else {
                    CameraSelector.LENS_FACING_BACK
                }
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(32.dp)
        ) {
            Icon(Icons.Default.Cameraswitch, contentDescription = "Switch Camera", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
        }
    }
}

@Composable
private fun CameraPreview(
    modifier: Modifier = Modifier,
    imageCapture: ImageCapture,
    lensFacing: Int
) {
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val context = LocalContext.current
    val previewView = remember { PreviewView(context) }
    val coroutineScope = rememberCoroutineScope()

    // We use LaunchedEffect to safely handle the asynchronous camera setup
    // This will re-run whenever lensFacing changes.
    LaunchedEffect(lensFacing) {
        coroutineScope.launch {
            try {
                // Use .await() to get the CameraProvider without callbacks
                val cameraProvider = ProcessCameraProvider.getInstance(context).await()

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(lensFacing)
                    .build()

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )
            } catch (e: Exception) {
                Log.e("CameraScreen", "Use case binding failed", e)
            }
        }
    }

    // AndroidView now simply displays the previewView we created.
    AndroidView({ previewView }, modifier = modifier)
}


private fun captureImage(
    imageCapture: ImageCapture,
    context: Context,
    onImageCaptured: (Bitmap) -> Unit,
    onError: (ImageCaptureException) -> Unit
) {
    val mainExecutor: Executor = ContextCompat.getMainExecutor(context)
    imageCapture.takePicture(mainExecutor, object : ImageCapture.OnImageCapturedCallback() {
        override fun onCaptureSuccess(image: ImageProxy) {
            val bitmap = image.toBitmap()
            val matrix = Matrix().apply { postRotate(image.imageInfo.rotationDegrees.toFloat()) }
            val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            onImageCaptured(rotatedBitmap)
            image.close()
        }

        override fun onError(exception: ImageCaptureException) {
            Log.e("CameraScreen", "Image capture error: ${exception.message}", exception)
            onError(exception)
        }
    })
}