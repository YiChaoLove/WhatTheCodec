package com.javernaut.whatthecodec.presentation.root.viewmodel

import com.javernaut.whatthecodec.domain.MediaType
import java.io.InputStream

/**
 * Created by YICHAO on 2021/1/5.
 */
class MediaPipeArgument(val inputStream: InputStream, val type: MediaType, val shortFormatName: String?)