cmake_minimum_required(VERSION 3.22.1)

project("terminal")

add_library(${CMAKE_PROJECT_NAME} SHARED
    jni_bridge.cpp)

# TODO: Link libghostty-vt when available
# target_link_libraries(${CMAKE_PROJECT_NAME} ghostty-vt)

target_link_libraries(${CMAKE_PROJECT_NAME}
    android
    log)
