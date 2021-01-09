package com.javernaut.whatthecodec.presentation.root.viewmodel

import android.os.ParcelFileDescriptor
import com.javernaut.whatthecodec.domain.MediaType

/**
 * Created by YICHAO on 2021/1/9.
 */
data class MediaFileDescriptorArgument(val parcelFileDescriptor: ParcelFileDescriptor, val offset: Long, val type: MediaType, val shortFormatName: String?)