package com.javernaut.whatthecodec.presentation.root.ui

import android.content.Intent
import android.content.res.AssetFileDescriptor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.MimeTypeFilter
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.tabs.TabLayoutMediator
import com.javernaut.whatthecodec.R
import com.javernaut.whatthecodec.domain.MediaType
import com.javernaut.whatthecodec.presentation.root.viewmodel.*
import com.javernaut.whatthecodec.presentation.settings.SettingsActivity
import com.javernaut.whatthecodec.presentation.video.ui.view.PreviewView
import com.javernaut.whatthecodec.util.TinyActivityCompat
import com.javernaut.whatthecodec.util.isVisible
import com.javernaut.whatthecodec.util.setVisible
import kotlinx.android.synthetic.main.activity_root.*
import kotlinx.android.synthetic.main.inline_empty_root.*
import java.io.InputStream

class RootActivity : AppCompatActivity(R.layout.activity_root) {

    private val mediaFileViewModel by lazy(LazyThreadSafetyMode.NONE) {
        ViewModelProvider(
                this, MediaFileViewModelFactory(this, PreviewView.getDesiredFrameWidth(this))
        ).get(MediaFileViewModel::class.java)
    }

    private var intentActionViewConsumed = false

    private lateinit var pagerAdapter: RootPagerAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setSupportActionBar(toolbar)

        pagerAdapter = RootPagerAdapter(this)
        pager.adapter = pagerAdapter
        TabLayoutMediator(tabs, pager, pagerAdapter.tabConfigurationStrategy).attach()

        pickVideo.setOnClickListener { onPickVideoClicked() }
        pickAudio.setOnClickListener { onPickAudioClicked() }

        openVideoFromAsset.setOnClickListener {
            val assetsFD = resources.assets.openFd("Test_Video_AnimalCrossing.mp4")
            openMediaFile(assetsFD, MediaType.VIDEO, "mp4")
        }

        openVideoByPipe.setOnClickListener {
            //assets, file or byteArray -> inputStream
            val inputStream = resources.assets.open("Test_Video_AnimalCrossing.mp4")
            openMediaFile(inputStream, MediaType.VIDEO, "mp4")

            //normal fd, need read permission.
//            val fd = ParcelFileDescriptor.open(File("/sdcard/Test_Video_AnimalCrossing.mp4"), ParcelFileDescriptor.MODE_READ_ONLY)
//            openMediaFile(fd, 0,  MediaType.VIDEO, null)
        }



        mediaFileViewModel.availableTabsLiveData.observe(this, Observer {
            tabs.visibility = View.VISIBLE
            supportActionBar?.title = null

            pagerAdapter.availableTabs = it

            emptyRootPanel.setVisible(false)
            pager.setVisible(true)

            invalidateOptionsMenu()
        })

        mediaFileViewModel.errorMessageLiveEvent.observe(this, Observer {
            if (it) {
                toast(R.string.message_couldnt_open_file)
            }
        })

        intentActionViewConsumed = savedInstanceState?.getBoolean(EXTRA_INTENT_ACTION_VIEW_CONSUMED) == true
        if (!intentActionViewConsumed) {
            onCheckForActionView()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        intentActionViewConsumed = false
        onCheckForActionView()
    }

    override fun onResume() {
        super.onResume()
        mediaFileViewModel.applyPendingMediaFileIfNeeded()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(EXTRA_INTENT_ACTION_VIEW_CONSUMED, intentActionViewConsumed)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_CODE_PICK_VIDEO || requestCode == REQUEST_CODE_PICK_AUDIO) {
            if (resultCode == RESULT_OK && data?.data != null) {
                openMediaFile(data.data!!, if (requestCode == REQUEST_CODE_PICK_VIDEO) {
                    MediaType.VIDEO
                } else {
                    MediaType.AUDIO
                })
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        when (requestCode) {
            REQUEST_CODE_PERMISSION_ACTION_VIEW,
            REQUEST_CODE_PERMISSION_PICK_VIDEO,
            REQUEST_CODE_PERMISSION_PICK_AUDIO -> {
                if (TinyActivityCompat.wasReadStoragePermissionGranted(permissions, grantResults)) {
                    when (requestCode) {
                        REQUEST_CODE_PERMISSION_ACTION_VIEW -> actualDisplayFileFromActionView()
                        REQUEST_CODE_PERMISSION_PICK_VIDEO -> actualPickVideoFile()
                        else -> actualPickAudioFile()
                    }
                } else {
                    toast(R.string.message_permission_denied)
                }
            }
            else -> {
                super.onRequestPermissionsResult(requestCode, permissions, grantResults)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (!emptyRootPanel.isVisible()) {
            addMenuItem(menu, R.string.menu_pick_video, R.drawable.ic_menu_video, MenuItem.SHOW_AS_ACTION_ALWAYS) {
                onPickVideoClicked()
            }
            addMenuItem(menu, R.string.menu_pick_audio, R.drawable.ic_menu_audio, MenuItem.SHOW_AS_ACTION_ALWAYS) {
                onPickAudioClicked()
            }
        }
        addMenuItem(menu, R.string.menu_settings, 0, MenuItem.SHOW_AS_ACTION_NEVER) {
            SettingsActivity.start(this)
        }
        return true
    }

    private inline fun addMenuItem(menu: Menu,
                                   title: Int,
                                   icon: Int, actionEnum: Int,
                                   crossinline actualAction: () -> Unit) {
        menu.add(title).setIcon(icon)
                .setOnMenuItemClickListener {
                    actualAction()
                    true
                }.setShowAsAction(actionEnum)
    }

    private fun onPickVideoClicked() {
        checkPermissionAndTryOpenMedia(REQUEST_CODE_PERMISSION_PICK_VIDEO) {
            actualPickVideoFile()
        }
    }

    private fun onPickAudioClicked() {
        checkPermissionAndTryOpenMedia(REQUEST_CODE_PERMISSION_PICK_AUDIO) {
            actualPickAudioFile()
        }
    }

    private fun onCheckForActionView() {
        if (Intent.ACTION_VIEW == intent.action && intent.data != null) {
            checkPermissionAndTryOpenMedia(REQUEST_CODE_PERMISSION_ACTION_VIEW) {
                actualDisplayFileFromActionView()
            }
        }
    }

    private inline fun checkPermissionAndTryOpenMedia(requestCode: Int, actualAction: () -> Unit) {
        if (TinyActivityCompat.needRequestReadStoragePermission(this)) {
            TinyActivityCompat.requestReadStoragePermission(this, requestCode)
        } else {
            actualAction()
        }
    }

    private fun actualPickVideoFile() {
        startActivityForMediaFile(MIME_TYPE_VIDEO, REQUEST_CODE_PICK_VIDEO)
    }

    private fun actualPickAudioFile() {
        startActivityForMediaFile(MIME_TYPE_AUDIO, REQUEST_CODE_PICK_AUDIO)
    }

    private fun actualDisplayFileFromActionView() {
        intentActionViewConsumed = true
        openMediaFile(intent.data!!, if (MimeTypeFilter.matches(intent.type, MIME_TYPE_AUDIO)) {
            MediaType.AUDIO
        } else {
            MediaType.VIDEO
        })
    }

    private fun startActivityForMediaFile(type: String, requestCode: Int) {
        startActivityForResult(Intent(Intent.ACTION_GET_CONTENT)
                .setType(type)
                .putExtra(Intent.EXTRA_LOCAL_ONLY, true)
                .addCategory(Intent.CATEGORY_OPENABLE),
                requestCode)
    }

    private fun openMediaFile(uri: Uri, mediaType: MediaType) {
        mediaFileViewModel.openMediaFile(MediaFileArgument(uri.toString(), mediaType))
    }

    private fun openMediaFile(assetFileDescriptor: AssetFileDescriptor, mediaType: MediaType, shortFormatName: String?) {
        mediaFileViewModel.openMediaFile(MediaAssetsArgument(assetFileDescriptor, mediaType, shortFormatName))
    }

    private fun openMediaFile(inputStream: InputStream, mediaType: MediaType, shortFormatName: String?) {
        mediaFileViewModel.openMediaFile(MediaPipeArgument(inputStream, mediaType, shortFormatName))
    }

    private fun openMediaFile(parcelFileDescriptor: ParcelFileDescriptor, startOffset: Long, mediaType: MediaType, shortFormatName: String?) {
        mediaFileViewModel.openMediaFileDescriptor(MediaFileDescriptorArgument(parcelFileDescriptor, startOffset, mediaType, shortFormatName))
    }

    private fun toast(msg: Int) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    companion object {
        private const val REQUEST_CODE_PICK_VIDEO = 41
        private const val REQUEST_CODE_PICK_AUDIO = 42
        private const val REQUEST_CODE_PERMISSION_ACTION_VIEW = 43
        private const val REQUEST_CODE_PERMISSION_PICK_VIDEO = 44
        private const val REQUEST_CODE_PERMISSION_PICK_AUDIO = 45

        private const val EXTRA_INTENT_ACTION_VIEW_CONSUMED = "extra_intent_action_view_consumed"

        private const val MIME_TYPE_VIDEO = "video/*"
        private const val MIME_TYPE_AUDIO = "audio/*"
    }
}
