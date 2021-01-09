package com.javernaut.whatthecodec.presentation.root.viewmodel

import android.content.res.AssetFileDescriptor
import com.javernaut.whatthecodec.domain.MediaType

/**
 * Created by YICHAO on 2021/1/5.
 */
class MediaAssetsArgument(val assetFileDescriptor: AssetFileDescriptor, val type: MediaType, val shortFormatName: String?)