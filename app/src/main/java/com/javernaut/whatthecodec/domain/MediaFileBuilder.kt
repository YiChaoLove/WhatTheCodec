package com.javernaut.whatthecodec.domain

import android.content.res.AssetFileDescriptor
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.annotation.Keep
import java.io.InputStream

/**
 * Class that aggregates a creation process of a [MediaFile] object. Certain private methods are
 * called from JNI layer.
 */
class MediaFileBuilder(private val mediaType: MediaType) {

    private var error = false

    private var parcelFileDescriptor: ParcelFileDescriptor? = null

    private var fileFormatName: String? = null
    private var urlProtocolName: String? = null

    private var pipe: IntArray? = null

    private var videoStream: VideoStream? = null
    private var frameLoaderContextHandle: Long? = null
    private var audioStreams = mutableListOf<AudioStream>()
    private var subtitleStream = mutableListOf<SubtitleStream>()

    /**
     * Tries reading all metadata for a [MediaFile] object from a file path.
     */
    fun from(filePath: String) = apply {
        nativeCreateFromPath(filePath, mediaType.mediaStreamsMask)
    }

    /**
     * Tries reading all metadata for a [MediaFile] object from a file descriptor. The file descriptor is saved and
     * closed when [MediaFile.release] method is called.
     */
    fun from(descriptor: ParcelFileDescriptor) = apply {
        this.parcelFileDescriptor = descriptor
        nativeCreateFromFD(descriptor.fd, mediaType.mediaStreamsMask)
    }

    /**
     * Tries reading all metadata for a [MediaFile] object from a file from app's assets catalog.
     * The file descriptor is saved and closed when [MediaFile.release] method is called.
     *
     * @param shortFormatName a short name of a file format, as there is a problem in probing
     * certain formats (like mkv).
     * If a file comes from assets catalog, then its format should be known to a developer.
     * All default formats are listed here: https://ffmpeg.org/ffmpeg-formats.html
     */
    fun from(assetFileDescriptor: AssetFileDescriptor, shortFormatName: String) = apply {
        val descriptor = assetFileDescriptor.parcelFileDescriptor
        this.parcelFileDescriptor = descriptor
        nativeCreateFromFDWithOffset(descriptor.fd, assetFileDescriptor.startOffset, shortFormatName, mediaType.mediaStreamsMask)
//        nativeCreateFromAssetFD(descriptor.fd,
//                assetFileDescriptor.startOffset,
//                shortFormatName,
//                mediaType.mediaStreamsMask)
    }

    fun from(inputStream: InputStream, shortFormatName: String) = apply {
        nativeCreatePipe()?.let { pipe ->
            this.pipe = pipe
            PipeWriteThread(pipe[1], inputStream).start()
            nativeCreateFromPipe(pipe[0], shortFormatName, mediaType.mediaStreamsMask)
        }
    }

    inner class PipeWriteThread(val writePipeFD: Int, val inputStream: InputStream) : Thread() {
        override fun run() {
            var len = 0
            val buffer = ByteArray(1024 * 50)
            try {
                while (true) {
                    Log.i("PipeWriteThread", "read buffer")
                    len = inputStream.read(buffer)
                    if (len <= 0) {
                        Log.i("PipeWriteThread", "break. $len")
                        break
                    }
                    Log.i("PipeWriteThread", "write len:$len")
                    nativeWritePipeData(writePipeFD, buffer, len)
                }

            } catch (e: Exception) {
                Log.i("PipeWriteThread", "error")
                e.printStackTrace()
            }
            Log.i("PipeWriteThread", "write over, len:$len")
            inputStream.close()
        }
    }

    /**
     * Combines all data read from FFmpeg into a [MediaFile] object. If there was error during
     * metadata reading then null is returned.
     */
    fun create(): MediaFile? {
        return if (!error) {
            MediaFile(fileFormatName!!,
                    urlProtocolName,
                    videoStream,
                    audioStreams,
                    subtitleStream,
                    parcelFileDescriptor,
                    frameLoaderContextHandle
            ) {
                pipe?.let {
                    nativeClosePipe(it[0])
                    nativeClosePipe(it[1])
                }
            }
        } else {
            null
        }
    }

    @Keep
    /* Used from JNI */
    private fun onError() {
        this.error = true
    }

    @Keep
    /* Used from JNI */
    private fun onMediaFileFound(fileFormatName: String, urlProtocolName: String, seekable: Int) {
//        Log.i("onMediaFileFound", "seekable:$seekable")
        this.fileFormatName = fileFormatName
        this.urlProtocolName = urlProtocolName
    }

    @Keep
    /* Used from JNI */
    private fun onVideoStreamFound(
            basicStreamInfo: BasicStreamInfo,
            bitRate: Long,
            frameWidth: Int,
            frameHeight: Int,
            frameLoaderContext: Long) {
        if (videoStream == null) {
            videoStream = VideoStream(
                    basicStreamInfo,
                    bitRate,
                    frameWidth,
                    frameHeight)
            if (frameLoaderContext != -1L) {
                frameLoaderContextHandle = frameLoaderContext
            }
        }
    }

    @Keep
    /* Used from JNI */
    private fun onAudioStreamFound(
            basicStreamInfo: BasicStreamInfo,
            bitRate: Long,
            sampleFormat: String?,
            sampleRate: Int,
            channels: Int,
            channelLayout: String?) {
        audioStreams.add(
                AudioStream(basicStreamInfo, bitRate, sampleFormat, sampleRate, channels, channelLayout)
        )
    }

    @Keep
    /* Used from JNI */
    private fun onSubtitleStreamFound(basicStreamInfo: BasicStreamInfo) {
        subtitleStream.add(
                SubtitleStream(basicStreamInfo)
        )
    }

    @Keep
    /* Used from JNI */
    private fun createBasicInfo(index: Int,
                                title: String?,
                                codecName: String,
                                language: String?,
                                disposition: Int) =
            BasicStreamInfo(index, title, codecName, language, disposition)

    private external fun nativeCreateFromFD(fileDescriptor: Int, mediaStreamsMask: Int)

    private external fun nativeCreateFromFDWithOffset(fileDescriptor: Int, startOffset: Long, shortFormatName: String, mediaStreamsMask: Int)

    @Deprecated("use nativeCreateFromFDWithOffset")
    private external fun nativeCreateFromAssetFD(assetFileDescriptor: Int,
                                                 startOffset: Long,
                                                 shortFormatName: String,
                                                 mediaStreamsMask: Int)

    private external fun nativeCreateFromPath(filePath: String, mediaStreamsMask: Int)

    /**
     * Pipe protocol
     */
    private external fun nativeCreateFromPipe(outputFD: Int, shortFormatName: String, mediaStreamsMask: Int)

    private external fun nativeCreatePipe(): IntArray?

    private external fun nativeWritePipeData(fd: Int, data: ByteArray, size: Int)

    private external fun nativeClosePipe(fd: Int)

    init {
        // The order of importing is mandatory, because otherwise the app will crash on Android API 16 and 17.
        // See: https://android.googlesource.com/platform/bionic/+/master/android-changes-for-ndk-developers.md#changes-to-library-dependency-resolution
        System.loadLibrary("avutil")
        System.loadLibrary("avcodec")
        System.loadLibrary("avformat")
        System.loadLibrary("swscale")
        System.loadLibrary("media-file")
    }
}
