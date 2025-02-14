# What the Codec

Forked from [What the Codec](https://github.com/Javernaut/WhatTheCodec)

## Change
* Use AssetFileDescriptor with ffmpeg in Android, fix the problem that `mov.c` cannot handle `skip_initial_bytes` correctly
    1. Refer to [FFmpegForAndroidAssetFileDescriptor](https://github.com/YiChaoLove/FFmpegForAndroidAssetFileDescriptor)

* Use pipe protocol with ffmpeg in Android
    1. When using the pipe protocol, we need to create a thread to write data to the pipe, because the pipe buffer has a size limit. According to the Linux system, we can know that the pipe size is 16 physical pages, and each physical page is 4KB. So We need to create a separate thread to write data to the pipe.
    2. When demuxing an mp4 file, ffmpeg will first search for the `moov` atom. If the `moov` atom is at the end of the video file, the problem of demuxing failure may occur. You can use the `faststart` to move the `moov`  to the head of the video file.
    ```c
    ffmpeg -i video.mp4 -c copy -movflags +faststart output.mp4
    ```
* Fix `loadNextFrameInto(bitmap: Bitmap)` when the video contains B/P frames.

## Run
1. Add ffmpeg
```
git submodule update --init

cd FFmpegForAndroidAssetFileDescriptor

chmod u+x ffmpeg_build_android.sh

sudo ./ffmpeg_build_android.sh

```
2. Run `app`

 <img src="images/screens/device-2021-01-06-111010.png" width="300">
