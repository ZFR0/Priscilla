# Set the minimum version of CMake required
cmake_minimum_required(VERSION 3.18.1)

# Define the project name and language
project("priscilla" CXX)

# --- Import ALL of your OLD pre-compiled libraries ---
# This section defines every single one of the old .so files.

add_library(llama SHARED IMPORTED)
set_target_properties(llama PROPERTIES IMPORTED_LOCATION
        "${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libllama.so")

add_library(ggml SHARED IMPORTED)
set_target_properties(ggml PROPERTIES IMPORTED_LOCATION
        "${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libggml.so")

add_library(ggml-base SHARED IMPORTED)
set_target_properties(ggml-base PROPERTIES IMPORTED_LOCATION
        "${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libggml-base.so")

add_library(ggml-cpu SHARED IMPORTED)
set_target_properties(ggml-cpu PROPERTIES IMPORTED_LOCATION
        "${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libggml-cpu.so")

add_library(llama-android SHARED IMPORTED)
set_target_properties(llama-android PROPERTIES IMPORTED_LOCATION
        "${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libllama-android.so")

add_library(omp SHARED IMPORTED)
set_target_properties(omp PROPERTIES IMPORTED_LOCATION
        "${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libomp.so")


# --- Build Our JNI Bridge ---
add_library(
        llama_jni
        SHARED
        llama_jni.cpp
)

# --- Configure Include Directories ---
# We can keep using the clean, portable header structure we created.
target_include_directories(llama_jni PRIVATE
        "${CMAKE_SOURCE_DIR}/llama_api"
        "${CMAKE_SOURCE_DIR}/llama_api/ggml/include"
)


# --- Link Libraries ---
# Notice that we are linking ALL of the old libraries, as the old system required it.
target_link_libraries(
        llama_jni
        llama
        ggml
        ggml-base
        ggml-cpu
        llama-android
        omp
        log
)