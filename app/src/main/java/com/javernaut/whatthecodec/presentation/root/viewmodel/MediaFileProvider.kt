package com.javernaut.whatthecodec.presentation.root.viewmodel

import com.javernaut.whatthecodec.domain.MediaFile

interface MediaFileProvider {
    fun obtainMediaFile(argument: MediaFileArgument): MediaFile?

    fun obtainMediaFile(argument: MediaAssetsArgument): MediaFile?

    fun obtainMediaFile(argument: MediaPipeArgument): MediaFile?
}
