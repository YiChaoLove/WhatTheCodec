//
// Created by Alexander Berezhnoi on 24/03/19.
//

#include "media_file_builder.h"
#include "frame_loader_context.h"
#include "utils.h"

extern "C" {
#include <libavformat/avformat.h>
#include <libavcodec/avcodec.h>
#include <libavutil/bprint.h>
}
#include <android/log.h>

# define LOGE(...)  __android_log_print(ANDROID_LOG_ERROR, "moxie-video-X264SyncDecoder", __VA_ARGS__)


static jstring toJString(const char *cString) {
    jstring result = nullptr;
    if (cString != nullptr) {
        result = utils_get_env()->NewStringUTF(cString);
    }
    return result;
}

static jstring get_string(AVDictionary *metadata, const char *key) {
    jstring result = nullptr;
    AVDictionaryEntry *tag = av_dict_get(metadata, key, nullptr, 0);
    if (tag != nullptr) {
        result = utils_get_env()->NewStringUTF(tag->value);
    }
    return result;
}

static jstring get_title(AVDictionary *metadata) {
    return get_string(metadata, "title");
}

static jstring get_language(AVDictionary *metadata) {
    return get_string(metadata, "language");
}

static jobject createBasicStreamInfo(jobject jMediaFileBuilder,
                                     AVFormatContext *avFormatContext,
                                     int index) {

    AVStream *stream = avFormatContext->streams[index];
    AVCodecParameters *parameters = stream->codecpar;

    auto codecDescriptor = avcodec_descriptor_get(parameters->codec_id);
    jstring jCodecName = utils_get_env()->NewStringUTF(codecDescriptor->long_name);

    if (!jCodecName) {
        jCodecName = utils_get_env()->NewStringUTF(codecDescriptor->name);
    }

    return utils_call_instance_method_result(jMediaFileBuilder,
                                             fields.MediaFileBuilder.createBasicInfoID,
                                             index,
                                             get_title(stream->metadata),
                                             jCodecName,
                                             get_language(stream->metadata),
                                             stream->disposition);
}

static void onError(jobject jMediaFileBuilder) {
    utils_call_instance_method_void(jMediaFileBuilder,
                                    fields.MediaFileBuilder.onErrorID);
}

static void onMediaFileFound(jobject jMediaFileBuilder, AVFormatContext *avFormatContext, const char * protocolName, int seekable) {
    const char *fileFormatName = avFormatContext->iformat->long_name;

    jstring jFileFormatName = utils_get_env()->NewStringUTF(fileFormatName);
    jstring jProtocolName = utils_get_env()->NewStringUTF(protocolName);

    utils_call_instance_method_void(jMediaFileBuilder,
                                    fields.MediaFileBuilder.onMediaFileFoundID,
                                    jFileFormatName,
                                    jProtocolName,
                                    seekable);
}

static void onVideoStreamFound(jobject jMediaFileBuilder,
                               AVFormatContext *avFormatContext,
                               int index) {
    AVCodecParameters *parameters = avFormatContext->streams[index]->codecpar;

    jobject jBasicStreamInfo = createBasicStreamInfo(jMediaFileBuilder, avFormatContext, index);

    int64_t frameLoaderContextHandle = -1;
    auto *decoder = avcodec_find_decoder(parameters->codec_id);
    if (decoder != nullptr) {
        auto *videoStream = (FrameLoaderContext *) malloc(sizeof(FrameLoaderContext));;
        videoStream->avFormatContext = avFormatContext;
        videoStream->parameters = parameters;
        videoStream->avVideoCodec = decoder;
        videoStream->videoStreamIndex = index;
        frameLoaderContextHandle = frame_loader_context_to_handle(videoStream);
    }

    utils_call_instance_method_void(jMediaFileBuilder,
                                    fields.MediaFileBuilder.onVideoStreamFoundID,
                                    jBasicStreamInfo,
                                    parameters->bit_rate,
                                    parameters->width,
                                    parameters->height,
                                    frameLoaderContextHandle);
}

static void onAudioStreamFound(jobject jMediaFileBuilder,
                               AVFormatContext *avFormatContext,
                               int index) {
    AVStream *stream = avFormatContext->streams[index];
    AVCodecParameters *parameters = stream->codecpar;

    jobject jBasicStreamInfo = createBasicStreamInfo(jMediaFileBuilder, avFormatContext, index);

    auto avSampleFormat = static_cast<AVSampleFormat>(parameters->format);
    auto jSampleFormat = toJString(av_get_sample_fmt_name(avSampleFormat));

    jstring jChannelLayout = nullptr;
    if (parameters->channel_layout) {
        AVBPrint printBuffer;
        av_bprint_init(&printBuffer, 1, AV_BPRINT_SIZE_UNLIMITED);
        av_bprint_clear(&printBuffer);
        av_bprint_channel_layout(&printBuffer, parameters->channels, parameters->channel_layout);
        jChannelLayout = toJString(printBuffer.str);
        av_bprint_finalize(&printBuffer, NULL);
    }

    utils_call_instance_method_void(jMediaFileBuilder,
                                    fields.MediaFileBuilder.onAudioStreamFoundID,
                                    jBasicStreamInfo,
                                    parameters->bit_rate,
                                    jSampleFormat,
                                    parameters->sample_rate,
                                    parameters->channels,
                                    jChannelLayout);
}

static void onSubtitleStreamFound(jobject jMediaFileBuilder,
                                  AVFormatContext *avFormatContext,
                                  int index) {

    jobject jBasicStreamInfo = createBasicStreamInfo(jMediaFileBuilder, avFormatContext, index);

    utils_call_instance_method_void(jMediaFileBuilder,
                                    fields.MediaFileBuilder.onSubtitleStreamFoundID,
                                    jBasicStreamInfo);
}

static int STREAM_VIDEO = 1;
static int STREAM_AUDIO = 1 << 1;
static int STREAM_SUBTITLE = 1 << 2;

static void ffp_log_callback_brief(void *ptr, int level, const char *fmt, va_list vl)
{
    char buf[4096];
    vsnprintf(buf, sizeof(buf), fmt, vl);
    LOGE("[FFMPEG]--:%s", buf);
}

static void media_file_build(jobject jMediaFileBuilder, const char *uri, int mediaStreamsMask, AVFormatContext *avFormatContext) {
    int ret = 0;
    av_log_set_level(AV_LOG_ERROR);
    av_log_set_callback(ffp_log_callback_brief);
    if ((ret = avformat_open_input(&avFormatContext, uri, nullptr, nullptr)) < 0) {
        av_log(avFormatContext, AV_LOG_ERROR, "avformat_open_input error, %s:", av_err2str(ret));
        onError(jMediaFileBuilder);
        return;
    }

    if (avformat_find_stream_info(avFormatContext, nullptr) < 0) {
        avformat_free_context(avFormatContext);
        onError(jMediaFileBuilder);
        return;
    };

    const char* protocolName = avio_find_protocol_name(uri);
    onMediaFileFound(jMediaFileBuilder, avFormatContext, protocolName, avFormatContext->pb->seekable);

    for (int pos = 0; pos < avFormatContext->nb_streams; pos++) {
        AVCodecParameters *parameters = avFormatContext->streams[pos]->codecpar;
        AVMediaType type = parameters->codec_type;
        switch (type) {
            case AVMEDIA_TYPE_VIDEO:
                if (mediaStreamsMask & STREAM_VIDEO) {
                    onVideoStreamFound(jMediaFileBuilder, avFormatContext, pos);
                }
                break;
            case AVMEDIA_TYPE_AUDIO:
                if (mediaStreamsMask & STREAM_AUDIO) {
                    onAudioStreamFound(jMediaFileBuilder, avFormatContext, pos);
                }
                break;
            case AVMEDIA_TYPE_SUBTITLE:
                if (mediaStreamsMask & STREAM_SUBTITLE) {
                    onSubtitleStreamFound(jMediaFileBuilder, avFormatContext, pos);
                }
                break;
        }
    }
}

void media_file_build(jobject jMediaFileBuilder, const char *filePath, int mediaStreamsMask) {
    media_file_build(jMediaFileBuilder, filePath, mediaStreamsMask, nullptr);
}

void media_file_build(jobject jMediaFileBuilder, int fileDescriptor, int mediaStreamsMask) {
    char pipe[32];
    sprintf(pipe, "pipe:%d", fileDescriptor);

    media_file_build(jMediaFileBuilder, pipe, mediaStreamsMask, nullptr);
}

void media_file_build(jobject jMediaFileBuilder, int assetFileDescriptor, int64_t startOffset, const char *shortFormatName, int mediaStreamsMask) {
    char str[32];
    sprintf(str, "pipe:%d", assetFileDescriptor);

    AVFormatContext *predefinedContext = avformat_alloc_context();
    predefinedContext->skip_initial_bytes = startOffset;
    if (shortFormatName) {
        predefinedContext->iformat = av_find_input_format(shortFormatName);
    }
    media_file_build(jMediaFileBuilder, str, mediaStreamsMask, predefinedContext);
}

void media_file_build_by_fd(jobject jMediaFileBuilder, int fileDescriptor, int64_t startOffset,  const char *shortFormatName, int mediaStreamsMask){
    char str[32];
    sprintf(str, "/proc/self/fd/%d", fileDescriptor);
    AVFormatContext *predefinedContext = nullptr;
    predefinedContext = avformat_alloc_context();
    predefinedContext->skip_initial_bytes = startOffset;
    if (shortFormatName) {
        predefinedContext->iformat = av_find_input_format(shortFormatName);
    }
    media_file_build(jMediaFileBuilder, str, mediaStreamsMask, predefinedContext);
}

void media_file_build_by_pipe(jobject jMediaFileBuilder, int outputFD, const char *shortFormatName, int mediaStreamsMask) {
    char str[32];
    sprintf(str, "pipe:%d", outputFD);
    AVFormatContext *predefinedContext = nullptr;
    if (shortFormatName) {
        predefinedContext = avformat_alloc_context();
        predefinedContext->iformat = av_find_input_format(shortFormatName);
    }
    media_file_build(jMediaFileBuilder, str, mediaStreamsMask, predefinedContext);
}


