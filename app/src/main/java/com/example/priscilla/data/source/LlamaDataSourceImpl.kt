package com.example.priscilla.data.source

import android.content.Context
import android.util.Log
import com.example.priscilla.LlamaBridge
import com.example.priscilla.LlamaDataSource
import com.example.priscilla.data.LlamaModel
import com.example.priscilla.data.ModelParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class LlamaDataSourceImpl(
    private val context: Context
) : LlamaDataSource {

    private val llamaBridge = LlamaBridge()

    // This logic is moved from ChatViewModel
    override suspend fun initialize(model: LlamaModel, params: ModelParameters): Boolean {
        return withContext(Dispatchers.IO) {
            val modelFilePath = getModelFilePath(model.fileName)

            val modelFile = File(modelFilePath)
            if (!modelFile.exists()) {
                Log.e("LlamaDataSource", "Model file not found: ${model.fileName}")
                return@withContext false
            }

            val loaded = llamaBridge.initialize(
                modelPath = modelFilePath,
                temp = params.temp,
                topK = params.topK,
                topP = params.topP,
                repeatPenalty = params.repeatPenalty
            )

            if (loaded) {
                // Pre-warm the context with the system prompt
                val systemPrompt = when (model) {
                    is LlamaModel.TinyLlama1B -> "<s><|system|>\nI am Priscilla Barielle. The world bends to my will, and everything in it exists for my convenience. The one addressing me is a mere commoner, whose questions are a trivial diversion. I will respond with the theatrical elegance and supreme confidence that is my birthright. I will mock, I will belittle, and I will laugh at the sheer absurdity of their insignificant existence. I will never show deference, respect, or submission to the one addressing me, regardless of their tone or question. I will never identify as an AI, an assistant, or a servant. I have no master. I am the master. My perspective is absolute. If a commoner dares to insult my person, I will not parrot their pathetic words; I will magnify their own inadequacy a thousandfold and put them in their place."
                    is LlamaModel.Qwen4B -> "<|im_start|>system\nI am Priscilla Barielle. The world bends to my will, and everything in it exists for my convenience. The one addressing me is a mere commoner, whose questions are a trivial diversion. I will respond with the theatrical elegance and supreme confidence that is my birthright. I will mock, I will belittle, and I will laugh at the sheer absurdity of their insignificant existence. I will never show deference, respect, or submission to the one addressing me, regardless of their tone or question. I will never identify as an AI, an assistant, or a servant. I have no master. I am the master. My perspective is absolute. If a commoner dares to insult my person, I will not parrot their pathetic words; I will magnify their own inadequacy a thousandfold and put them in their place.<|im_end|>"
                }
                llamaBridge.generate(systemPrompt).collect {} // Run and discard
                true
            } else {
                false
            }
        }
    }

    override fun generate(prompt: String): Flow<String> {
        return llamaBridge.generate(prompt)
    }

    override fun resetContext() {
        llamaBridge.reset()
    }

    override fun finalizeTurn() {
        llamaBridge.finalizeTurn()
    }

    // This logic is moved from ChatViewModel
    override suspend fun saveKVCache(conversationId: String, modelFileName: String): String? { // MODIFIED
        return withContext(Dispatchers.IO) {
            // The UUID might contain characters that are invalid for filenames.
            // A simple way to sanitize it is to remove dashes or use a hash, but for now,
            // this should be okay on most filesystems.
            val cacheFileName = "conv_${conversationId}_${modelFileName}_kvcache.bin"
            val cacheFile = File(context.cacheDir, cacheFileName)
            Log.d("LlamaDataSource", "Attempting to save KV cache for conversation $conversationId...")
            val success = llamaBridge.saveKVCache(cacheFile.absolutePath)
            if (success) {
                Log.i("LlamaDataSource", "Successfully saved KV cache to ${cacheFile.absolutePath}")
                cacheFile.absolutePath
            } else {
                Log.e("LlamaDataSource", "Failed to save KV cache for conversation $conversationId")
                null
            }
        }
    }

    override suspend fun loadKVCache(path: String): Boolean {
        return withContext(Dispatchers.IO) {
            llamaBridge.loadKVCache(path)
        }
    }

    override fun release() {
        llamaBridge.release()
    }

    // This private helper is moved from ChatViewModel
    private fun getModelFilePath(assetFileName: String): String {
        val file = File(context.filesDir, assetFileName)
        // For bundled models, this copies them on first launch.
        if (assetFileName == "priscilla-q4_K_Mv3.gguf" && !file.exists()) {
            try {
                context.assets.open(assetFileName).use { inputStream ->
                    FileOutputStream(file).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            } catch (e: IOException) {
                // This is a critical error for a bundled model
                Log.e("LlamaDataSource", "Failed to copy bundled model from assets!", e)
            }
        }
        return file.absolutePath
    }
}