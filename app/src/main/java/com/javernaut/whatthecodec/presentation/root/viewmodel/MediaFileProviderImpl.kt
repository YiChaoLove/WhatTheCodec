package com.javernaut.whatthecodec.presentation.root.viewmodel

import android.content.Context
import android.net.Uri
import com.javernaut.whatthecodec.domain.MediaFile
import com.javernaut.whatthecodec.domain.MediaFileBuilder
import com.javernaut.whatthecodec.util.PathUtil
import java.io.FileNotFoundException

class MediaFileProviderImpl(context: Context) : MediaFileProvider {
    private val appContext = context.applicationContext

    override fun obtainMediaFile(argument: MediaFileArgument): MediaFile? {
        val androidUri = Uri.parse(argument.uri)
        var config: MediaFile? = null

        // First, try get a file:// path
        val path = PathUtil.getPath(appContext, androidUri)
        if (path != null) {
            config = MediaFileBuilder(argument.type).from(path).create()
        }

        // Second, try get a FileDescriptor.
        if (config == null) {
            try {
                val descriptor = appContext.contentResolver.openFileDescriptor(androidUri, "r")
                if (descriptor != null) {
                    config = MediaFileBuilder(argument.type).from(descriptor, 0, null).create()
                }
            } catch (e: FileNotFoundException) {
                e.printStackTrace()
            }
        }

        return config
    }

    override fun obtainMediaFile(argument: MediaAssetsArgument): MediaFile? =
            MediaFileBuilder(argument.type).from(argument.assetFileDescriptor, argument.shortFormatName).create()

    override fun obtainMediaFile(argument: MediaPipeArgument): MediaFile? =
            MediaFileBuilder(argument.type).from(argument.inputStream, argument.shortFormatName).create()

    override fun obtainMediaFile(argument: MediaFileDescriptorArgument): MediaFile? =
            MediaFileBuilder(argument.type).from(argument.parcelFileDescriptor, argument.offset, argument.shortFormatName).create()


}
