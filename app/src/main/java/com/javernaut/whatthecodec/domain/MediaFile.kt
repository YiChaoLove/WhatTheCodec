package com.javernaut.whatthecodec.domain

import android.os.ParcelFileDescriptor

/**
 * A structure that has metadata of a video or audio file and its media streams.
 */
class MediaFile(
        val fileFormatName: String,
        val urlProtocolName: String?,
        val videoStream: VideoStream?,
        val audioStreams: List<AudioStream>,
        val subtitleStreams: List<SubtitleStream>,
        private val parcelFileDescriptor: ParcelFileDescriptor?,
        frameLoaderContextHandle: Long?,
        private val closePipe: () -> Unit//Only used by pipe protocol
) {

    val fullFeatured = parcelFileDescriptor == null

    var frameLoader = frameLoaderContextHandle?.let { FrameLoader(frameLoaderContextHandle) }
        private set

    fun supportsFrameLoading() = videoStream != null && frameLoader != null

    fun release() {
        frameLoader?.release()
        frameLoader = null
        parcelFileDescriptor?.close()
        closePipe()//Only used by pipe protocol
    }
}
