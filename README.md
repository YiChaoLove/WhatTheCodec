# What the Codec

Forked from [What the Codec](https://github.com/Javernaut/WhatTheCodec)

当前分支为测试分支，主要用于测试FFmpeg在读取带有startOffset的mov格式的媒体文件时是否能够正常的解码

### 测试
工程中已经自带编译好的so库：
```
ffmpeg4_3_1 目录下为正常未经修改的代码所编译的so库
ffmpeg_for_android_asset_fd 目录下为按照FFmpegForAndroidAssetFileDescriptor修改过的代码所编译的so库
```
##### 测试正常so库是否能够正确处理`skip_initial_bytes`

修改`CMakeLists.txt`文件后运行
```
#set(ffmpeg_dir ${CMAKE_SOURCE_DIR}/../ffmpeg_for_android_asset_fd/android)
set(ffmpeg_dir ${CMAKE_SOURCE_DIR}/../ffmpeg4_3_1/android)
```
##### 测试经由FFmpegForAndroidAssetFileDescriptor修改是否可以正常处理`skip_initial_bytes`

修改`CMakeLists.txt`文件后运行
```
set(ffmpeg_dir ${CMAKE_SOURCE_DIR}/../ffmpeg_for_android_asset_fd/android)
#set(ffmpeg_dir ${CMAKE_SOURCE_DIR}/../ffmpeg4_3_1/android)
```
