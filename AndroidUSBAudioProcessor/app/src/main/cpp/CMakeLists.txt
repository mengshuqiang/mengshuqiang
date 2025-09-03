cmake_minimum_required(VERSION 3.22.1)
project("usbaudioprocessor")

# 设置C++标准
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# 添加Oboe库
set(OBOE_DIR ${CMAKE_CURRENT_SOURCE_DIR}/oboe)
add_subdirectory(${OBOE_DIR} ./oboe-bin)
include_directories(${OBOE_DIR}/include)

# 查找系统库
find_library(log-lib log)
find_library(android-lib android)

# 添加源文件
add_library(${CMAKE_PROJECT_NAME} SHARED
    native-lib.cpp
    AudioEngine.cpp
    AudioEffect.cpp
    USBAudioProcessor.cpp
)

# 链接库
target_link_libraries(${CMAKE_PROJECT_NAME}
    oboe
    ${log-lib}
    ${android-lib}
    OpenSLES
)