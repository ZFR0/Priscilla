#include <jni.h>
#include <string>
#include <vector>
#include <thread>
#include <fstream>
#include <android/log.h>
#include "llama.h"

#define TAG "llama_jni.cpp"

// --- Logging Control Switch ---
#define ENABLE_LLAMA_LOGGING 0

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)


#if ENABLE_LLAMA_LOGGING
static void log_batch_as_text(const llama_batch& batch, const llama_model* model, const char* log_prefix) {
    if (!model) return;
    const auto* vocab = llama_model_get_vocab(model);
    if (!vocab) return;

    std::string batch_text;
    char piece_buf[64];

    for (int i = 0; i < batch.n_tokens; ++i) {
        const int n_chars = llama_token_to_piece(vocab, batch.token[i], piece_buf, sizeof(piece_buf), 0, true);
        if (n_chars >= 0) {
            batch_text.append(piece_buf, n_chars);
        }
    }
    LOGI("%s\n---\n%s\n---", log_prefix, batch_text.c_str());
}
#endif


// --- Global state variables ---
static llama_model* model = nullptr;
static llama_context* ctx = nullptr;
static llama_sampler* smpl = nullptr;
static llama_batch batch = {0, nullptr, nullptr, nullptr, nullptr, nullptr, nullptr};
static int n_past = 0;
static llama_seq_id g_seq_id = 0;
static std::string cached_token_str;
static int g_n_keep = 0;

static bool is_backend_initialized = false;

// This function frees only the model, context, and batch resources.
// It leaves the backend intact, making it safe for model reloads.
static void free_model_resources() {
    if (model == nullptr) {
        return; // Nothing to free
    }
    LOGI("Freeing model-specific resources (model, context, batch).");

    if (smpl) { llama_sampler_free(smpl); smpl = nullptr; }

    // The hypothesis is that llama_free(ctx) is responsible for cleaning up the batch.
    // By commenting out our explicit call, I will test if this prevents the double-free crash.
    // if (batch.token) {
    //    llama_batch_free(batch);
    // }
    // After commenting out the above, now explicitly reset the global variable to prevent
    // any possible use of a dangling pointer (hopefully, I still need to check if this actually happens).
    batch = {0, nullptr, nullptr, nullptr, nullptr, nullptr, nullptr};

    if (ctx) { llama_free(ctx); ctx = nullptr; }
    if (model) { llama_model_free(model); model = nullptr; }

    // Reset conversation state
    n_past = 0;
    g_n_keep = 0;
    cached_token_str.clear();
}

// This function performs a full cleanup, including the backend.
// It should only be called when the app is exiting.
static void full_cleanup() {
    LOGI("Performing full cleanup including backend.");
    free_model_resources(); // Free model resources first
    if (is_backend_initialized) {
        llama_backend_free();
        is_backend_initialized = false;
    }
}


extern "C" JNIEXPORT void JNICALL
Java_com_example_priscilla_LlamaBridge_unloadModel(JNIEnv* env, jobject) {
    // This is called from onCleared(), so we do a full cleanup.
    full_cleanup();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_priscilla_LlamaBridge_loadModel(
        JNIEnv* env, jobject, jstring modelPath, jfloat temp, jint top_k,
        jfloat top_p, jfloat repeat_penalty) {

    // Correct lifecycle implementation ---

    // 1. Free any existing model, but not the backend.
    free_model_resources();

    // 2. Initialize the backend *only if* it hasn't been done before.
    if (!is_backend_initialized) {
        LOGI("Initializing Llama backend for the first time.");
        llama_backend_init();
        is_backend_initialized = true;
    }

    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    if (path == nullptr) return JNI_FALSE;
    LOGI("Loading model from: %s", path);

    llama_model_params model_params = llama_model_default_params();
    model_params.use_mmap = true;
    model = llama_model_load_from_file(path, model_params);
    env->ReleaseStringUTFChars(modelPath, path);
    if (model == nullptr) {
        LOGE("Failed to load model.");
        // No need to free backend here, it should persist.
        return JNI_FALSE;
    }

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = 2048;
    unsigned int n_threads = std::thread::hardware_concurrency();
    ctx_params.n_threads = n_threads;
    ctx_params.n_threads_batch = n_threads;

    ctx = llama_init_from_model(model, ctx_params);
    if (ctx == nullptr) {
        LOGE("Failed to create context.");
        free_model_resources(); // Clean up the model we just loaded
        return JNI_FALSE;
    }

    auto sparams = llama_sampler_chain_default_params();
    smpl = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(smpl, llama_sampler_init_penalties(ctx_params.n_ctx, repeat_penalty, 0.0f, 0.0f));
    if (top_k > 0) { llama_sampler_chain_add(smpl, llama_sampler_init_top_k(top_k)); }
    if (top_p < 1.0f) { llama_sampler_chain_add(smpl, llama_sampler_init_top_p(top_p, 1)); }
    if (temp > 0.0f) {
        llama_sampler_chain_add(smpl, llama_sampler_init_temp(temp));
        llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
    } else {
        llama_sampler_chain_add(smpl, llama_sampler_init_greedy());
    }

    batch = llama_batch_init(ctx_params.n_ctx, 0, 1);
    if (!batch.token) {
        LOGE("Failed to initialize llama_batch.");
        free_model_resources();
        return JNI_FALSE;
    }

    LOGI("Model loaded successfully.");
    return JNI_TRUE;
}

//
// NO CHANGES ARE NEEDED BELOW THIS LINE.
// The inference functions remain the same.
//

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_priscilla_LlamaBridge_startInference(JNIEnv* env, jobject, jstring promptText) {
    if (ctx == nullptr) { LOGE("startInference called but context not loaded."); return JNI_FALSE; }

    const char* prompt = env->GetStringUTFChars(promptText, nullptr);
    if (prompt == nullptr) return JNI_FALSE;
    std::string promptStr(prompt);
    env->ReleaseStringUTFChars(promptText, prompt);

    std::vector<llama_token> new_tokens;
    new_tokens.resize(promptStr.length() + 1);

    int n_tokens = llama_tokenize(llama_model_get_vocab(model), promptStr.c_str(), promptStr.length(), new_tokens.data(), new_tokens.size(), false, true);
    if (n_tokens < 0) { LOGE("Tokenization failed."); return JNI_FALSE; }
    new_tokens.resize(n_tokens);

    if (n_past == 0) {
        g_n_keep = n_tokens;
    }

    const int n_ctx = llama_n_ctx(ctx);
    if (n_past + n_tokens > n_ctx - 128) {
        LOGI("Context is getting full (%d tokens). Pruning...", n_past);
        const int n_left = n_past - g_n_keep;
        const int n_to_remove = n_left / 4;
        LOGI("Removing %d tokens from the start of the conversation.", n_to_remove);
        llama_memory_seq_rm(llama_get_memory(ctx), g_seq_id, g_n_keep, g_n_keep + n_to_remove);
        llama_memory_seq_add(llama_get_memory(ctx), g_seq_id, g_n_keep + n_to_remove, -1, -n_to_remove);
        n_past -= n_to_remove;
        LOGI("Pruning complete. New context size: %d tokens.", n_past);
    }

    if (n_past + (int)new_tokens.size() > llama_n_ctx(ctx) - 4) {
        LOGE("Prompt is too long and would overflow context even after pruning.");
        return JNI_FALSE;
    }

    batch.n_tokens = 0;
    for (int i = 0; i < new_tokens.size(); ++i) {
        const int i_batch = batch.n_tokens;
        batch.token[i_batch]    = new_tokens[i];
        batch.pos[i_batch]      = n_past + i;
        batch.n_seq_id[i_batch] = 1;
        batch.seq_id[i_batch]   = &g_seq_id;
        batch.logits[i_batch]   = 0;
        batch.n_tokens++;
    }
    batch.logits[batch.n_tokens - 1] = 1;

#if ENABLE_LLAMA_LOGGING
    log_batch_as_text(batch, model, "CONTEXT ADD (startInference):");
#endif

    if (llama_decode(ctx, batch) != 0) {
        LOGE("llama_decode failed on prompt");
        return JNI_FALSE;
    }
    n_past += batch.n_tokens;
    cached_token_str.clear();
    return JNI_TRUE;
}


extern "C" JNIEXPORT jstring JNICALL
Java_com_example_priscilla_LlamaBridge_continueInference(JNIEnv* env, jobject) {
    if (ctx == nullptr) { return nullptr; }
    if (n_past >= llama_n_ctx(ctx)) { return nullptr; }

    llama_token new_token_id = llama_sampler_sample(smpl, ctx, -1);
    llama_sampler_accept(smpl, new_token_id);

    const auto* vocab = llama_model_get_vocab(model);
    if (llama_vocab_is_eog(vocab, new_token_id)) {
        return nullptr;
    }

    batch.n_tokens = 0;
    batch.token[0]    = new_token_id;
    batch.pos[0]      = n_past;
    batch.n_seq_id[0] = 1;
    batch.seq_id[0]   = &g_seq_id;
    batch.logits[0]   = 1;
    batch.n_tokens++;

#if ENABLE_LLAMA_LOGGING
    log_batch_as_text(batch, model, "CONTEXT ADD (continueInference):");
#endif

    if (llama_decode(ctx, batch) != 0) {
        LOGE("llama_decode failed during generation");
        return nullptr;
    }
    n_past++;

    char piece_buf[64];
    const int n_chars = llama_token_to_piece(vocab, new_token_id, piece_buf, sizeof(piece_buf), 0, false);
    if (n_chars < 0) {
        LOGE("llama_token_to_piece failed");
        return nullptr;
    }
    std::string piece(piece_buf, n_chars);

    cached_token_str += piece;
    std::vector<char> utf8_char;
    size_t i = 0;
    while (i < cached_token_str.length()) {
        char ch = cached_token_str[i];
        int len = 1;
        if ((ch & 0x80) == 0x00) len = 1;
        else if ((ch & 0xE0) == 0xC0) len = 2;
        else if ((ch & 0xF0) == 0xE0) len = 3;
        else if ((ch & 0xF8) == 0xF0) len = 4;
        else { i++; continue; }
        if (i + len > cached_token_str.length()) { break; }
        utf8_char.insert(utf8_char.end(), cached_token_str.begin() + i, cached_token_str.begin() + i + len);
        i += len;
    }
    if (!utf8_char.empty()) {
        cached_token_str.erase(0, i);
        return env->NewStringUTF(std::string(utf8_char.begin(), utf8_char.end()).c_str());
    }
    return env->NewStringUTF("");
}


extern "C" JNIEXPORT void JNICALL
Java_com_example_priscilla_LlamaBridge_finalizeTurn(JNIEnv *env, jobject) {
    if (ctx == nullptr) {
        LOGE("finalizeTurn called but context not loaded.");
        return;
    }

    const auto* vocab = llama_model_get_vocab(model);
    llama_token eos_token = llama_vocab_eos(vocab);

    batch.n_tokens = 0;
    batch.token[0]    = eos_token;
    batch.pos[0]      = n_past;
    batch.n_seq_id[0] = 1;
    batch.seq_id[0]   = &g_seq_id;
    batch.logits[0]   = 0;
    batch.n_tokens++;

#if ENABLE_LLAMA_LOGGING
    log_batch_as_text(batch, model, "CONTEXT ADD (finalizeTurn):");
#endif

    if (llama_decode(ctx, batch) != 0) {
        LOGE("llama_decode failed in finalizeTurn");
    }
    n_past++;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_priscilla_LlamaBridge_saveKVCache(JNIEnv *env, jobject, jstring filePath) {
    if (ctx == nullptr) {
        LOGE("saveKVCache called but context is null.");
        return JNI_FALSE;
    }

    const char *path = env->GetStringUTFChars(filePath, nullptr);
    if (path == nullptr) {
        LOGE("saveKVCache received a null file path.");
        return JNI_FALSE;
    }
    LOGI("Saving KV Cache to %s", path);

    // 1. Determine the size of the state using the new function name
    const size_t state_size = llama_state_get_size(ctx);
    std::vector<uint8_t> state_mem(state_size);

    // 2. Copy the state into our buffer using the new function name
    llama_state_get_data(ctx, state_mem.data(), state_mem.size());

    // 3. Write the buffer to the file
    std::ofstream file(path, std::ios::binary);
    if (!file) {
        LOGE("Failed to open file for writing: %s", path);
        env->ReleaseStringUTFChars(filePath, path);
        return JNI_FALSE;
    }
    file.write(reinterpret_cast<const char*>(state_mem.data()), static_cast<std::streamsize>(state_mem.size()));
    file.close();

    env->ReleaseStringUTFChars(filePath, path);
    LOGI("KV Cache saved successfully.");
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_priscilla_LlamaBridge_loadKVCache(JNIEnv *env, jobject, jstring filePath) {
    if (ctx == nullptr) {
        LOGE("loadKVCache called but context is null.");
        return JNI_FALSE;
    }

    const int n_ctx_current = llama_n_ctx(ctx);
    const size_t state_size_current = llama_state_get_size(ctx);
    LOGI("[DEBUG] PRE-LOAD CHECK: Current context size (n_ctx) is %d.", n_ctx_current);
    LOGI("[DEBUG] PRE-LOAD CHECK: Current state size is %zu bytes.", state_size_current);

    const char *path = env->GetStringUTFChars(filePath, nullptr);
    if (path == nullptr) {
        LOGE("loadKVCache received a null file path.");
        return JNI_FALSE;
    }
    LOGI("Loading KV Cache from %s", path);

    // 1. Read the file into a buffer
    std::ifstream file(path, std::ios::binary | std::ios::ate);
    if (!file) {
        LOGE("Failed to open file for reading: %s", path);
        env->ReleaseStringUTFChars(filePath, path);
        return JNI_FALSE;
    }

    std::streamsize size = file.tellg();
    file.seekg(0, std::ios::beg);
    std::vector<uint8_t> buffer(size);
    if (!file.read(reinterpret_cast<char*>(buffer.data()), size)) {
        LOGE("Failed to read file into buffer: %s", path);
        env->ReleaseStringUTFChars(filePath, path);
        return JNI_FALSE;
    }
    file.close();

    LOGI("[DEBUG] PRE-LOAD CHECK: File size of cache to be loaded is %td bytes.", size);
    // 2. Set the state using the new function name
    const size_t bytes_read = llama_state_set_data(ctx, buffer.data(), buffer.size());

    if (bytes_read != buffer.size()) {
        LOGE("Failed to load state from buffer. Bytes read mismatch. Expected %zu, got %zu", buffer.size(), bytes_read);
        env->ReleaseStringUTFChars(filePath, path);
        return JNI_FALSE;
    }

    const llama_pos max_pos = llama_memory_seq_pos_max(llama_get_memory(ctx), g_seq_id);
    // The state data we loaded already contains the correct token count.
    // We just need to update our global tracker variables from the restored context.
    n_past = (max_pos == -1) ? 0 : (max_pos + 1);

    // We can't know g_n_keep for sure from a loaded state. The safest action is
    // to assume the entire loaded context is now the "base" to keep.
    // This simplifies logic greatly and is more robust than trying to guess.
    g_n_keep = n_past;

    env->ReleaseStringUTFChars(filePath, path);
    LOGI("KV Cache loaded successfully. n_past is now %d.", n_past);
    return JNI_TRUE;
}


extern "C" JNIEXPORT void JNICALL
Java_com_example_priscilla_LlamaBridge_resetContext(JNIEnv* env, jobject) {
    if (ctx == nullptr) {
        LOGE("softResetContext called but context not loaded.");
        return;
    }

    // g_n_keep holds the number of tokens in the initial system prompt.
    // If it's 0, it means no prompt was ever processed, so there's nothing to do.
    if (g_n_keep == 0) {
        LOGI("g_n_keep is 0, nothing to reset beyond the system prompt.");
        return;
    }

    // Get a handle to the context's memory.
    llama_memory_t memory = llama_get_memory(ctx);
    if (memory == nullptr) {
        LOGE("Could not get memory handle from context.");
        return;
    }

    // Surgically remove all tokens from the KV cache that came *after* the system prompt.
    // The -1 for p1 means "to the end of the sequence".
    llama_memory_seq_rm(memory, g_seq_id, g_n_keep, -1);

    // Reset the token counter back to the size of the system prompt.
    n_past = g_n_keep;

    // Reset the sampler to its initial state to clear any penalties.
    if (smpl) {
        llama_sampler_reset(smpl);
    }

    LOGI("Llama context soft-reset. KV cache preserved for system prompt (%d tokens).", g_n_keep);
}