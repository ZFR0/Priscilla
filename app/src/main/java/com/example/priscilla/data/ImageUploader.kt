package com.example.priscilla.data

import android.graphics.Bitmap
import com.google.gson.annotations.SerializedName
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import kotlin.math.sqrt


// Data classes to model the JSON response from the ImgBB API
data class ImgbbResponse(
    val data: ImgbbData?,
    val success: Boolean,
    val status: Int
)

data class ImgbbData(
    val id: String?,
    @SerializedName("display_url")
    val displayUrl: String?,
    val url: String?,
    val title: String?,
    @SerializedName("time")
    val time: String?,
    val image: ImgbbImageInfo?,
    val thumb: ImgbbImageInfo?,
    val medium: ImgbbImageInfo?,
    @SerializedName("delete_url")
    val deleteUrl: String?
)

data class ImgbbImageInfo(
    val filename: String?,
    val name: String?,
    val mime: String?,
    val extension: String?,
    val url: String?
)

class ImageUploader {

    private val client = OkHttpClient()
    private val gson = Gson()
    private val apiKey = "4fd8fade265c811443c686a5317c6cf0"

    /**
     * Uploads a bitmap to ImgBB and returns the direct URL to the image.
     * This function handles image compression and resizing.
     *
     * @param bitmap The bitmap to upload.
     * @return The direct URL of the uploaded image, or null if the upload failed.
     */
    suspend fun uploadImage(bitmap: Bitmap): String? = withContext(Dispatchers.IO) {
        try {
            Log.d("ImageUploader", "Starting image upload...")
            // Step 1: Compress and resize the image
            val compressedBitmap = resizeAndCompressBitmap(bitmap)

            // Step 2: Convert the compressed bitmap to a ByteArray
            val outputStream = ByteArrayOutputStream()
            compressedBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
            val imageByteArray = outputStream.toByteArray()

            // Step 3: Base64 encode the byte array (required by ImgBB API)
            val base64Image = Base64.encodeToString(imageByteArray, Base64.DEFAULT)

            // Step 4: Build the multipart request body
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("key", apiKey)
                .addFormDataPart("image", base64Image)
                .build()

            // Step 5: Build the HTTP request
            val request = Request.Builder()
                .url("https://api.imgbb.com/1/upload")
                .post(requestBody)
                .build()

            // Step 6: Execute the request and parse the response
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("ImageUploader", "Upload failed with code: ${response.code}, message: ${response.message}")
                    Log.e("ImageUploader", "Response body: ${response.body?.string()}")
                    return@withContext null
                }

                val responseBody = response.body?.string()
                if (responseBody == null) {
                    Log.e("ImageUploader", "Upload failed: Response body was null.")
                    return@withContext null
                }

                val imgbbResponse = gson.fromJson(responseBody, ImgbbResponse::class.java)

                if (imgbbResponse.success && imgbbResponse.data?.displayUrl != null) {
                    val imageUrl = imgbbResponse.data.displayUrl
                    Log.i("ImageUploader", "Image uploaded successfully! URL: $imageUrl")
                    return@withContext imageUrl
                } else {
                    Log.e("ImageUploader", "Upload failed: API returned success=false. Response: $responseBody")
                    return@withContext null
                }
            }
        } catch (e: Exception) {
            Log.e("ImageUploader", "Exception during image upload", e)
            return@withContext null
        }
    }

    /**
     * Resizes a bitmap to a target size (e.g., 1 megapixel) while maintaining aspect ratio,
     * then compresses it.
     */
    private fun resizeAndCompressBitmap(bitmap: Bitmap): Bitmap {
        val targetPixelCount = 1024 * 1024 // Target ~1 Megapixel
        val currentPixelCount = bitmap.width * bitmap.height

        if (currentPixelCount <= targetPixelCount) {
            return bitmap // No resizing needed
        }

        val scaleFactor = sqrt(targetPixelCount.toDouble() / currentPixelCount)
        val newWidth = (bitmap.width * scaleFactor).toInt()
        val newHeight = (bitmap.height * scaleFactor).toInt()

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
}