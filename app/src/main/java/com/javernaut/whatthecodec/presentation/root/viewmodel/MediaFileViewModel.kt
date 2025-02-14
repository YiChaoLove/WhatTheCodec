package com.javernaut.whatthecodec.presentation.root.viewmodel

import androidx.lifecycle.*
import com.hadilq.liveevent.LiveEvent
import com.javernaut.whatthecodec.domain.AudioStream
import com.javernaut.whatthecodec.domain.MediaFile
import com.javernaut.whatthecodec.domain.SubtitleStream
import com.javernaut.whatthecodec.presentation.root.viewmodel.model.*

class MediaFileViewModel(private val desiredFrameWidth: Int,
                         private val mediaFileProvider: MediaFileProvider,
                         private val savedStateHandle: SavedStateHandle) : ViewModel() {

    private var pendingMediaFileArgument: MediaFileArgument? = null

    private var mediaFile: MediaFile? = null
    private var frameLoaderHelper: FrameLoaderHelper? = null

    private var frameMetrics: FrameMetrics? = null

    private val _basicVideoInfoLiveData = MutableLiveData<BasicVideoInfo?>()
    private val _previewLiveData = MutableLiveData<Preview>()
    private val _errorMessageLiveEvent = LiveEvent<Boolean>()
    private val _availableTabsLiveData = MutableLiveData<List<AvailableTab>>()
    private val _audioStreamsLiveData = MutableLiveData<List<AudioStream>>()
    private val _subtitleStreamsLiveData = MutableLiveData<List<SubtitleStream>>()

    init {
        pendingMediaFileArgument = savedStateHandle[KEY_MEDIA_FILE_ARGUMENT]
    }

    /**
     * Basic info about video stream. See [BasicVideoInfo] for details.
     */
    val basicVideoInfoLiveData: LiveData<BasicVideoInfo?>
        get() = _basicVideoInfoLiveData

    /**
     * Notifies about error during opening a file.
     */
    val errorMessageLiveEvent: LiveData<Boolean>
        get() = _errorMessageLiveEvent

    /**
     * Exposes actual Bitmap objects that need to be shown as frames.
     */
    val previewLiveData: LiveData<Preview>
        get() = _previewLiveData

    /**
     * Tabs that should be visible in UI.
     */
    val availableTabsLiveData: LiveData<List<AvailableTab>>
        get() = _availableTabsLiveData

    /**
     * List of [AudioStream] object for displaying in UI.
     */
    val audioStreamsLiveData: LiveData<List<AudioStream>>
        get() = _audioStreamsLiveData

    /**
     * List of [SubtitleStream] object for displaying in UI.
     */
    val subtitleStreamsLiveData: LiveData<List<SubtitleStream>>
        get() = _subtitleStreamsLiveData

    override fun onCleared() {
        if (frameLoaderHelper == null) {
            mediaFile?.release()
        } else {
            frameLoaderHelper?.release()
        }
    }

    fun applyPendingMediaFileIfNeeded() {
        if (pendingMediaFileArgument != null) {
            openMediaFile(pendingMediaFileArgument!!)
        }
    }

    fun openMediaFile(argument: MediaFileArgument) {
        clearPendingUri()

        val newMediaFile = mediaFileProvider.obtainMediaFile(argument)
        if (newMediaFile != null) {
            savedStateHandle.set(KEY_MEDIA_FILE_ARGUMENT, argument)

            releasePreviousMediaFileAndFrameLoader(newMediaFile)

            mediaFile = newMediaFile
            applyMediaFile(newMediaFile)
        } else {
            _errorMessageLiveEvent.value = true
        }
    }

    fun openMediaFile(argument: MediaAssetsArgument) {
        clearPendingUri()

        val newMediaFile = mediaFileProvider.obtainMediaFile(argument)
        if (newMediaFile != null) {
//            savedStateHandle.set(KEY_MEDIA_FILE_ARGUMENT, argument)

            releasePreviousMediaFileAndFrameLoader(newMediaFile)

            mediaFile = newMediaFile
            applyMediaFile(newMediaFile)
        } else {
            _errorMessageLiveEvent.value = true
        }
    }

    fun openMediaFile(argument: MediaPipeArgument) {
        clearPendingUri()

        val newMediaFile = mediaFileProvider.obtainMediaFile(argument)
        if (newMediaFile != null) {
//            savedStateHandle.set(KEY_MEDIA_FILE_ARGUMENT, argument)

            releasePreviousMediaFileAndFrameLoader(newMediaFile)

            mediaFile = newMediaFile
            applyMediaFile(newMediaFile)
        } else {
            _errorMessageLiveEvent.value = true
        }
    }

    fun openMediaFileDescriptor(argument: MediaFileDescriptorArgument) {
        clearPendingUri()
        val newMediaFile = mediaFileProvider.obtainMediaFile(argument)
        if (newMediaFile != null) {
//            savedStateHandle.set(KEY_MEDIA_FILE_ARGUMENT, argument)
            releasePreviousMediaFileAndFrameLoader(newMediaFile)

            mediaFile = newMediaFile
            applyMediaFile(newMediaFile)
        } else {
            _errorMessageLiveEvent.value = true
        }
    }

    private fun applyMediaFile(mediaFile: MediaFile) {
        _basicVideoInfoLiveData.value = mediaFile.toBasicInfo()
        frameMetrics = computeFrameMetrics()

        if (mediaFile.supportsFrameLoading()) {
            frameLoaderHelper = FrameLoaderHelper(frameMetrics!!, viewModelScope, ::applyPreview)
        }

        _audioStreamsLiveData.value = mediaFile.audioStreams
        _subtitleStreamsLiveData.value = mediaFile.subtitleStreams

        setupTabsAvailable(mediaFile)
        tryLoadVideoFrames()
    }

    private fun releasePreviousMediaFileAndFrameLoader(newMediaFile: MediaFile) {
        if (frameLoaderHelper != null) {
            frameLoaderHelper?.release(newMediaFile.supportsFrameLoading())
        } else {
            // MediaFile is released only if there was no FrameLoaderHelper used.
            mediaFile?.release()
        }
        frameLoaderHelper = null
    }

    private fun setupTabsAvailable(mediaFile: MediaFile) {
        val tabs = mutableListOf<AvailableTab>()
        if (mediaFile.videoStream != null) {
            tabs.add(AvailableTab.VIDEO)
        }
        if (mediaFile.audioStreams.isNotEmpty()) {
            tabs.add(AvailableTab.AUDIO)
        }
        if (mediaFile.subtitleStreams.isNotEmpty()) {
            tabs.add(AvailableTab.SUBTITLES)
        }
        _availableTabsLiveData.value = tabs
    }

    private fun tryLoadVideoFrames() {
        if (frameLoaderHelper != null) {
            frameLoaderHelper?.loadFrames(mediaFile!!)
        } else {
            _previewLiveData.value = NoPreviewAvailable
        }
    }

    private fun applyPreview(preview: Preview) {
        _previewLiveData.value = (preview)
    }

    private fun clearPendingUri() {
        pendingMediaFileArgument = null
    }

    private fun computeFrameMetrics(): FrameMetrics? {
        val basicVideoInfo = _basicVideoInfoLiveData.value

        return basicVideoInfo?.let {
            val frameHeight = it.videoStream.frameHeight
            val frameWidth = it.videoStream.frameWidth.toDouble()
            val desiredFrameHeight = (desiredFrameWidth * frameHeight / frameWidth).toInt()

            FrameMetrics(desiredFrameWidth, desiredFrameHeight)
        }
    }

    private fun MediaFile.toBasicInfo(): BasicVideoInfo? {
        if (videoStream == null) {
            return null
        }
        return BasicVideoInfo(
                fileFormatName,
                urlProtocolName ?: "unknown",
                fullFeatured,
                videoStream
        )
    }

    companion object {
        const val KEY_MEDIA_FILE_ARGUMENT = "key_video_file_uri"
    }
}
