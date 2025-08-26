package com.example.priscilla

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class LlamaBridge {
    init {
        System.loadLibrary("llama_jni")
    }

    // --- Public Functions ---
    fun initialize(modelPath: String, temp: Float, topK: Int, topP: Float, repeatPenalty: Float): Boolean {
        return loadModel(modelPath, temp, topK, topP, repeatPenalty)
    }

    fun release() {
        unloadModel()
    }

    fun reset() {
        resetContext()
    }

    fun generate(prompt: String): Flow<String> = flow {
        if (!startInference(prompt)) {
            throw IllegalStateException("Failed to start inference")
        }
        while (true) {
            val nextPiece = continueInference() ?: break
            emit(nextPiece)
        }
    }.flowOn(Dispatchers.IO)

    external fun finalizeTurn()

    // --- JNI Declarations ---
    private external fun loadModel(
        modelPath: String, temp: Float, topK: Int, topP: Float, repeatPenalty: Float
    ): Boolean
    private external fun unloadModel()
    private external fun startInference(promptText: String): Boolean
    private external fun continueInference(): String?

    private external fun resetContext()

    external fun saveKVCache(filePath: String): Boolean
    external fun loadKVCache(filePath: String): Boolean

}