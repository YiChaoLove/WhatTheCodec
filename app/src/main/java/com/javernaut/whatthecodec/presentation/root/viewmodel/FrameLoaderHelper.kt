package com.javernaut.whatthecodec.presentation.root.viewmodel

import android.graphics.Bitmap
import android.graphics.Color
import androidx.palette.graphics.Palette
import com.javernaut.whatthecodec.domain.FrameLoader
import com.javernaut.whatthecodec.domain.MediaFile
import com.javernaut.whatthecodec.presentation.root.viewmodel.model.*
import kotlinx.coroutines.*

class FrameLoaderHelper(
        private val metrics: FrameMetrics,
        private val scope: CoroutineScope,
        private val previewApplier: (ActualPreview) -> Unit
) {

    private var cachedFrameBitmaps = HashMap<Int, Bitmap>()

    private var isWorking = false

    private lateinit var framesLoadingJob: Job

    /**
     *  Because the pipe protocol does not support seek, all frames are decoded at once.
     *  Get seekable by MediaFileBuilder's onMediaFileFound callback.
     */
    fun loadFrames(mediaFile: MediaFile) {
        isWorking = true

        framesLoadingJob = scope.launch(Dispatchers.Default) {
            val frames = mutableListOf<Frame>(PlaceholderFrame, PlaceholderFrame, PlaceholderFrame, PlaceholderFrame)

            // Initial state where all cells are empty
            var actualPreview = ActualPreview(
                    metrics,
                    frames,
                    Color.TRANSPARENT
            )

            tryApplyPreview(previewApplier, actualPreview)

            if (mediaFile.urlProtocolName == "pipe") {
                for (index in 0 until FrameLoader.TOTAL_FRAMES_TO_LOAD) {
                    if (!isWorking) {
                        break
                    }
                    // First, marking a cell as 'loading'
                    frames[index] = LoadingFrame
                    tryApplyPreview(previewApplier, actualPreview)
                }
                // Loading all frame in a separate dispatcher
                val result = withContext(Dispatchers.IO) {
                    loadAllFrames(mediaFile)
                }

                for ((index, res) in result.withIndex()) {
                    // Depending on the actual result, displaying either an actual frame or an error
                    frames[index] = if (res.success) {
                        ActualFrame(res.frame)
                    } else {
                        DecodingErrorFrame
                    }
                    tryApplyPreview(previewApplier, actualPreview)
                }
            } else {
                for (index in 0 until FrameLoader.TOTAL_FRAMES_TO_LOAD) {
                    if (!isWorking) {
                        break
                    }

                    // First, marking a cell as 'loading'
                    frames[index] = LoadingFrame
                    tryApplyPreview(previewApplier, actualPreview)

                    // Loading a frame in a separate dispatcher
                    val result = withContext(Dispatchers.IO) {
                        loadSingleFrame(mediaFile, index)
                    }

                    // Depending on the actual result, displaying either an actual frame or an error
                    frames[index] = if (result.success) {
                        ActualFrame(result.frame)
                    } else {
                        DecodingErrorFrame
                    }

                    tryApplyPreview(previewApplier, actualPreview)
                }
            }


            if (isWorking) {
                // In the end, we compute the background for frames
                val frameBackground = withContext(Dispatchers.Default) {
                    computeBackground(getFrameBitmap(0))
                }
                actualPreview = actualPreview.copy(backgroundColor = frameBackground)
                tryApplyPreview(previewApplier, actualPreview)
            }

            mediaFile.release()
        }
    }

    private suspend fun tryApplyPreview(previewApplier: (ActualPreview) -> Unit, preview: ActualPreview) {
        if (isWorking) {
            withContext(Dispatchers.Main) {
                previewApplier(preview)
            }
        }
    }

    fun release(needPreviewReset: Boolean = false) {
        isWorking = false

        if (needPreviewReset) {
            previewApplier(ActualPreview(
                    metrics,
                    listOf(PlaceholderFrame, PlaceholderFrame, PlaceholderFrame, PlaceholderFrame),
                    Color.TRANSPARENT)
            )
        }

        scope.launch(Dispatchers.Default) {
            framesLoadingJob.join()

            cachedFrameBitmaps.values.forEach { it.recycle() }
            cachedFrameBitmaps.clear()
        }
    }

    private fun loadSingleFrame(mediaFile: MediaFile, index: Int): FrameLoadingResult {
        val frameBitmap = getFrameBitmap(index)

        val successfulLoading = mediaFile.frameLoader?.loadNextFrameInto(frameBitmap) == true

        return FrameLoadingResult(frameBitmap, successfulLoading)
    }

    private fun loadAllFrames(mediaFile: MediaFile) : Array<FrameLoadingResult> {
        val frameBitmps = Array<Bitmap>(4) {
            getFrameBitmap(it)
        }
        val successfulLoading = mediaFile.frameLoader?.loadFramesInto(frameBitmps) == true
        return Array<FrameLoadingResult>(4) {
            FrameLoadingResult(frameBitmps[it], successfulLoading)
        }
    }

    private fun getFrameBitmap(index: Int): Bitmap {
        if (!cachedFrameBitmaps.containsKey(index)) {
            cachedFrameBitmaps[index] = Bitmap.createBitmap(metrics.width, metrics.height, Bitmap.Config.ARGB_8888)
        }
        return cachedFrameBitmaps[index]!!
    }

    private fun computeBackground(bitmap: Bitmap): Int {
        val palette = Palette.from(bitmap).generate()
        // Pick the first swatch in this order that isn't null and use its color
        return listOf(
                palette::getDarkMutedSwatch,
                palette::getMutedSwatch,
                palette::getDominantSwatch
        ).firstOrNull {
            it() != null
        }?.invoke()?.rgb ?: Color.TRANSPARENT
    }

    private class FrameLoadingResult(
            val frame: Bitmap,
            val success: Boolean
    )
}
