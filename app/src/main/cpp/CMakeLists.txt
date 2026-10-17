cmake_minimum_required(VERSION 3.22.1)
project(vivostretch C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(vivostretch SHARED stretch_jni.cpp)
target_include_directories(vivostretch PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}
        ${CMAKE_CURRENT_SOURCE_DIR}/third_party)
target_compile_options(vivostretch PRIVATE -O3 -ffast-math -fno-finite-math-only)

# ---------------- Sottotitoli: whisper.cpp (riconoscimento vocale sul telefono) ----------------
# Il codice si scarica alla compilazione (versione fissa); il modello lo scarica l'app al primo uso.
include(FetchContent)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(WHISPER_ALL_WARNINGS OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(GGML_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
if (ANDROID_ABI STREQUAL "arm64-v8a")
    set(GGML_CPU_ARM_ARCH "armv8.2-a+dotprod+fp16" CACHE STRING "" FORCE)
endif ()
# Anche nella versione di prova (emulatore) il riconoscimento va ottimizzato, altrimenti è lentissimo.
set(CMAKE_C_FLAGS_DEBUG "-O2 -g0")
set(CMAKE_CXX_FLAGS_DEBUG "-O2 -g0")
FetchContent_Declare(whisper URL https://github.com/ggml-org/whisper.cpp/archive/refs/tags/v1.9.5.tar.gz)
FetchContent_MakeAvailable(whisper)

add_library(vivowhisper SHARED whisper_jni.cpp)
target_link_libraries(vivowhisper whisper log)
target_compile_options(vivowhisper PRIVATE -O3)
