package com.example.priscilla.data

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.tasks.await
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream

class ImageAnalyzer(
    private val context: Context,
    // For Image Labeling, a higher confidence is often better to start with.
    // Let's use 0.7f to get more relevant labels.
    private val confidenceThreshold: Float = 0.7f
) {
    // Use options for the Image Labeler instead of the Object Detector.
    // We can set the confidence threshold directly here.
    private val options = ImageLabelerOptions.Builder()
        .setConfidenceThreshold(confidenceThreshold)
        .build()

    // Get an ImageLabeler client.
    private val labeler = ImageLabeling.getClient(options)

    /**
     * Takes a Bitmap and analyzes the image for descriptive labels.
     */
    suspend fun analyze(bitmap: Bitmap): List<String> {
        val image = InputImage.fromBitmap(bitmap, 0)

        // The try/finally block for closing the client is no longer needed with the
        // recommended singleton approach for the Labeler. The resources are managed differently.

        return try {
            // Process the image with the labeler. This returns a list of ImageLabel.
            val labels = labeler.process(image).await()

            Log.d("ImageAnalyzer", "ML Kit found ${labels.size} labels before filtering (if any).")
            labels.forEach {
                Log.d("ImageAnalyzer", "-> Label: '${it.text}', Confidence: ${it.confidence}")
            }

            if (labels.isEmpty()) {
                Log.d("ImageAnalyzer", "No labels detected by ML Kit.")
                return emptyList()
            }

            // The filtering is now much simpler. We just map the text of each label.
            // The confidence was already filtered by the options we set.
            val labelTexts = labels.map { it.text }

            Log.d("ImageAnalyzer", "Analysis successful. Final Labels: $labelTexts")
            labelTexts

        } catch (e: Exception) {
            Log.e("ImageAnalyzer", "Error during ML Kit image labeling.", e)
            // It's better to return an empty list on failure than to crash the ViewModel.
            // The ViewModel can then handle the empty list case.
            emptyList()
        }
    }

    /**
     * Loads a Bitmap from a given absolute file path.
     * Returns null if the file doesn't exist or cannot be decoded.
     */
    fun loadBitmapFromPath(path: String): Bitmap? {
        return try {
            val file = File(path)
            if (file.exists()) {
                BitmapFactory.decodeFile(file.absolutePath)
            } else {
                Log.w("ImageAnalyzer", "Bitmap file not found at path: $path")
                null
            }
        } catch (e: Exception) {
            Log.e("ImageAnalyzer", "Error loading bitmap from path: $path", e)
            null
        }
    }

    /**
     * Saves a bitmap to the app's internal storage in a dedicated 'images' directory.
     *
     * @param bitmap The bitmap to save.
     * @return The absolute path to the saved image file, or null on failure.
     */
    fun saveBitmapToInternalStorage(bitmap: Bitmap): String? {
        // Get the directory for your app's private pictures.
        // Using a subdirectory is good practice.
        val imagesDir = File(context.filesDir, "images")
        if (!imagesDir.exists()) {
            imagesDir.mkdirs()
        }

        // Create a unique file name.
        val fileName = "IMG_${System.currentTimeMillis()}.jpg"
        val imageFile = File(imagesDir, fileName)

        return try {
            FileOutputStream(imageFile).use { fos ->
                // Compress the bitmap to the file stream.
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
            }
            Log.i("ImageAnalyzer", "Successfully saved bitmap to: ${imageFile.absolutePath}")
            // Return the full path to the saved file.
            imageFile.absolutePath
        } catch (e: Exception) {
            Log.e("ImageAnalyzer", "Error saving bitmap to internal storage", e)
            null
        }
    }
}