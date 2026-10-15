cmake_minimum_required(VERSION 3.22.1)
project(vivostretch CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(vivostretch SHARED stretch_jni.cpp)
target_include_directories(vivostretch PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}
        ${CMAKE_CURRENT_SOURCE_DIR}/third_party)
target_compile_options(vivostretch PRIVATE -O3 -ffast-math -fno-finite-math-only)
