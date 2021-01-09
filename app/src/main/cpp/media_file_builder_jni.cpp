#include <jni.h>

#include "media_file_builder.h"
#include "frame_extractor.h"
#include <unistd.h>
#include <android/log.h>

// File with JNI bindings for MediaFileBuilder java class.

# define LOGE(...)  __android_log_print(ANDROID_LOG_ERROR, "media_file_builder_jni", __VA_ARGS__)

extern "C" {
JNIEXPORT void JNICALL
Java_com_javernaut_whatthecodec_domain_MediaFileBuilder_nativeCreateFromFDWithOffset(JNIEnv *env,
                                                                                     jobject instance,
                                                                                     jint fileDescriptor,
                                                                                     jlong startOffset,
                                                                                     jstring jShortFormatName,
                                                                                     jint mediaStreamsMask) {
    if (jShortFormatName) {
        const char *cShortFormatName = env->GetStringUTFChars(jShortFormatName, nullptr);
        media_file_build_by_fd(instance, fileDescriptor, startOffset, cShortFormatName,
                               mediaStreamsMask);
        env->ReleaseStringUTFChars(jShortFormatName, cShortFormatName);
    } else {
        media_file_build_by_fd(instance, fileDescriptor, startOffset, nullptr,
                               mediaStreamsMask);
    }

}

JNIEXPORT void JNICALL
Java_com_javernaut_whatthecodec_domain_MediaFileBuilder_nativeCreateFromPath(JNIEnv *env,
                                                                             jobject instance,
                                                                             jstring jFilePath,
                                                                             jint mediaStreamsMask) {
    const char *cFilePath = env->GetStringUTFChars(jFilePath, nullptr);

    media_file_build(instance, cFilePath, mediaStreamsMask);

    env->ReleaseStringUTFChars(jFilePath, cFilePath);
}
}


extern "C"
JNIEXPORT void JNICALL
Java_com_javernaut_whatthecodec_domain_MediaFileBuilder_nativeCreateFromPipe(JNIEnv *env,
                                                                             jobject thiz,
                                                                             jint output_fd,
                                                                             jstring short_format_name,
                                                                             jint media_streams_mask) {
    if (short_format_name) {
        const char *cShortFormatName = env->GetStringUTFChars(short_format_name, nullptr);
        media_file_build_by_pipe(thiz, output_fd, cShortFormatName, media_streams_mask);
        env->ReleaseStringUTFChars(short_format_name, cShortFormatName);
    } else {
        media_file_build_by_pipe(thiz, output_fd, nullptr, media_streams_mask);
    }
}

extern "C"
JNIEXPORT jintArray JNICALL
Java_com_javernaut_whatthecodec_domain_MediaFileBuilder_nativeCreatePipe(JNIEnv *env,
                                                                         jobject thiz) {
    int pipes[2];
    int error_code;
    if ((error_code = pipe(pipes)) < 0) {
        LOGE("pipe(pipes) error, %d", error_code);
        return nullptr;
    }
    LOGE("pipe(pipes) succ, pipe[0]:%d, pipe[1]:%d", pipes[0], pipes[1]);
    jintArray res = env->NewIntArray(2);
    env->SetIntArrayRegion(res, 0, 2, pipes);
    return res;
}


extern "C"
JNIEXPORT void JNICALL
Java_com_javernaut_whatthecodec_domain_MediaFileBuilder_nativeWritePipeData(JNIEnv *env,
                                                                            jobject thiz, jint fd,
                                                                            jbyteArray data,
                                                                            jint size) {
    jbyte *c_data = env->GetByteArrayElements(data,JNI_FALSE);
    write(fd, c_data, size);
    env->ReleaseByteArrayElements(data, c_data, JNI_ABORT);
}


extern "C"
JNIEXPORT void JNICALL
Java_com_javernaut_whatthecodec_domain_MediaFileBuilder_nativeClosePipe(JNIEnv *env, jobject thiz,
                                                                        jint fd) {
    close(fd);
}

