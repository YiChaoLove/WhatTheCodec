//
// Created by Alexander Berezhnoi on 14/10/19.
//

#ifndef WHATTHECODEC_MEDIA_FILE_BUILDER_H
#define WHATTHECODEC_MEDIA_FILE_BUILDER_H

#include <jni.h>

#ifdef __GNUC__
#    define GCC_VERSION_AT_LEAST(x,y) (__GNUC__ > (x) || __GNUC__ == (x) && __GNUC_MINOR__ >= (y))
#else
#    define GCC_VERSION_AT_LEAST(x,y) 0
#endif

#if GCC_VERSION_AT_LEAST(3,1)
#    define attribute_deprecated __attribute__((deprecated))
#elif defined(_MSC_VER)
#    define attribute_deprecated __declspec(deprecated)
#else
#    define attribute_deprecated
#endif

/**
 * Use media_file_build_by_fd
 *
 * Creates an AVFormatContext struct according to the parameter. Notifies a MediaFileBuilder
 * object about each media stream found and passes all its metadata.
 *
 * @param jMediaFileBuilder a MediaFileBuilder java object
 * @param fileDescriptor a file descriptor to a local file
 * @param mediaStreamsMask a bit set of media streams to extract from the file
 */
attribute_deprecated
void media_file_build(jobject jMediaFileBuilder, int fileDescriptor, int mediaStreamsMask);

/**
 * Use media_file_build_by_fd
 *
 * Creates an AVFormatContext struct according to the parameter. Notifies a MediaFileBuilder
 * object about each media stream found and passes all its metadata.
 *
 * @param jMediaFileBuilder a MediaFileBuilder java object
 * @param assetFileDescriptor a file descriptor to a file in Assets
 * @param startOffset an amount of bytes to skip before actual data reading
 * @param shortFormatName the name of the media container, as there is a problem probing it this case
 * @param mediaStreamsMask a bit set of media streams to extract from the file
 */
attribute_deprecated
void media_file_build(jobject jMediaFileBuilder, int assetFileDescriptor, int64_t startOffset, const char *shortFormatName, int mediaStreamsMask);

/**
 * Creates an AVFormatContext struct according to the parameter. Notifies a MediaFileBuilder
 * object about each media stream found and passes all its metadata.
 *
 * @param jMediaFileBuilder a MediaFileBuilder java object
 * @param filePath a file path to a local file
 * @param mediaStreamsMask a bit set of media streams to extract from the file
 */
void media_file_build(jobject jMediaFileBuilder, const char *filePath, int mediaStreamsMask);

/**
 * FFmpeg for Android assets fd
 * @param jMediaFileBuilder
 * @param fileDescriptor
 * @param startOffset
 * @param mediaStreamsMask
 */
void media_file_build_by_fd(jobject jMediaFileBuilder, int fileDescriptor, int64_t startOffset,  const char *shortFormatName, int mediaStreamsMask);


void media_file_build_by_pipe(jobject jMediaFileBuilder, int outputFD, const char *shortFormatName, int mediaStreamsMask);

#endif //WHATTHECODEC_MEDIA_FILE_BUILDER_H
