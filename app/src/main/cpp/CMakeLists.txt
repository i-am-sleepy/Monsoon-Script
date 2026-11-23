# Native voice engine (G5, D66): transcribe.cpp (ggml) + a small JNI layer. Built only when third_party/transcribe.cpp is checked out
# (see app/build.gradle.kts); arm64-v8a only. Models are separate files the owner downloads or imports, nothing is bundled.
cmake_minimum_required(VERSION 3.22.1)
project(monsoonvoice LANGUAGES C CXX)

set(TRANSCRIBE_DIR "${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/transcribe.cpp")
set(TRANSCRIBE_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(TRANSCRIBE_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(TRANSCRIBE_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(TRANSCRIBE_BUILD_SHARED OFF CACHE BOOL "" FORCE)
set(TRANSCRIBE_USE_SYSTEM_BLAS OFF CACHE BOOL "" FORCE)
set(TRANSCRIBE_INSTALL OFF CACHE BOOL "" FORCE)
add_subdirectory(${TRANSCRIBE_DIR} transcribe EXCLUDE_FROM_ALL)

add_library(monsoonvoice SHARED voice_jni.cpp)
target_link_libraries(monsoonvoice PRIVATE transcribe log)
target_link_options(monsoonvoice PRIVATE -Wl,--exclude-libs,ALL -Wl,--gc-sections)
