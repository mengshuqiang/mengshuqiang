cmake_minimum_required(VERSION 3.22.1)

project("virtualaudiocard")

# 设置C++标准
set(CMAKE_CXX_STANDARD 17)

# 添加头文件目录
include_directories(${CMAKE_CURRENT_SOURCE_DIR})

# 创建native库
add_library(
    virtualaudiocard
    SHARED
    native-lib.cpp
    audio_processing.cpp
    virtual_audio_driver.cpp
)

# 查找并链接所需的库
find_library(
    log-lib
    log
)

find_library(
    android-lib
    android
)

find_library(
    aaudio-lib
    aaudio
)

find_library(
    opensles-lib
    OpenSLES
)

# 链接库
target_link_libraries(
    virtualaudiocard
    ${log-lib}
    ${android-lib}
    ${aaudio-lib}
    ${opensles-lib}
)