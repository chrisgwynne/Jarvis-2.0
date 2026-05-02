cmake_minimum_required(VERSION 3.22.1)
project(whisper_jni)

# Build the JNI bridge as a shared library.
# When whisper.cpp source is present, add its files to whisper_src below.
set(whisper_src
    whisper_jni.cpp
    # whisper.cpp          # uncomment once whisper.cpp source is added
    # ggml.c
    # ggml-alloc.c
    # ggml-backend.c
    # ggml-quants.c
)

add_library(whisper_jni SHARED ${whisper_src})

target_compile_options(whisper_jni PRIVATE
    -O3
    -ffast-math
    -DNDEBUG
)

find_library(log-lib log)
target_link_libraries(whisper_jni ${log-lib})
