// File: /data/ModelDownloader.kt
package com.example.priscilla.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

// This state class will be used to update the UI with progress.
// It's a sealed class for better state management in the UI.
sealed class DownloadState {
    object Idle : DownloadState()
    // Includes which model is downloading
    data class Downloading(val modelFileName: String, val progress: Int) : DownloadState()
    object Finished : DownloadState()
    // Includes which model had an error
    data class Error(val modelFileName: String, val message: String) : DownloadState()
}

class ModelDownloader(private val context: Context) {
    // We create a single client for efficiency.
    private val client = OkHttpClient()

    fun downloadModel(model: LlamaModel): Flow<DownloadState> = flow {
        // 1. Check if a download is even needed
        val url = model.downloadUrl ?: run {
            // This is the bundled model, no download needed.
            emit(DownloadState.Finished)
            return@flow
        }

        val destinationFile = File(context.filesDir, model.fileName)
        if (destinationFile.exists()) {
            // For simplicity, we assume if the file exists, it's correct.
            // A more robust implementation might check file integrity (e.g., with a checksum).
            Log.i("ModelDownloader", "Model ${model.fileName} already exists. No download needed.")
            emit(DownloadState.Finished)
            return@flow
        }

        val tempFile = File(context.filesDir, "${model.fileName}.tmp")
        if (tempFile.exists()) {
            tempFile.delete()
        }
        // 2. Start the download process
        emit(DownloadState.Downloading(model.fileName, 0))
        Log.i("ModelDownloader", "Starting download for ${model.fileName} to temporary file.")

        try {
            val request = Request.Builder()
                .url(url)
                .build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                throw IOException("Download failed: ${response.code} ${response.message}")
            }

            val body = response.body ?: throw IOException("Response body is null")
            val totalBytes = body.contentLength()
            var bytesCopied = 0L

            // 3. Read the file stream and report progress
            tempFile.outputStream().use { fileOut ->
                body.source().use { source ->
                    val buffer = okio.Buffer()
                    var read = source.read(buffer, 8192L)
                    while (read != -1L) {
                        fileOut.write(buffer.readByteArray())
                        bytesCopied += read
                        if (totalBytes > 0) {
                            val progress = ((bytesCopied * 100) / totalBytes).toInt()
                            if (progress != ( ( (bytesCopied - read) * 100) / totalBytes).toInt() ) {
                                emit(DownloadState.Downloading(model.fileName, progress))
                            }
                        }
                        read = source.read(buffer, 8192L)
                    }
                }
            }
            // If the download completes, rename the temp file to the final destination file.
            // This is an atomic operation on most file systems, making the process robust.
            if (tempFile.renameTo(destinationFile)) {
                Log.i("ModelDownloader", "Model download complete. Renamed to final destination.")
                emit(DownloadState.Finished)
            } else {
                throw IOException("Failed to rename temporary file to final destination.")
            }

        } catch (e: Exception) {
            Log.e("ModelDownloader", "Download error", e)
            emit(DownloadState.Error(model.fileName, e.localizedMessage ?: "An unknown error occurred"))

            // Clean up the temporary file on failure
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }.flowOn(Dispatchers.IO) // Ensure all of this runs on a background thread.
}