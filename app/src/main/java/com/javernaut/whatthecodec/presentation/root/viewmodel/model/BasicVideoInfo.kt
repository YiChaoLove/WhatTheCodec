package com.javernaut.whatthecodec.presentation.root.viewmodel.model

import com.javernaut.whatthecodec.domain.VideoStream

data class BasicVideoInfo(
        val fileFormat: String,
        val protocolName: String,
        @Deprecated("use protocolName")
        val fullFeatured: Boolean,
        val videoStream: VideoStream
)
