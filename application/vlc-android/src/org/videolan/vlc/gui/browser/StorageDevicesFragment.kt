package org.videolan.vlc.gui.browser

import android.content.Intent
import android.os.Bundle
import android.view.*
import androidx.appcompat.view.ActionMode
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import kotlinx.android.synthetic.main.main.*
import kotlinx.coroutines.*
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.media.MediaLibraryItem
import org.videolan.resources.*
import org.videolan.tools.NetworkMonitor
import org.videolan.tools.isStarted
import org.videolan.tools.setGone
import org.videolan.tools.setVisible
import org.videolan.vlc.R
import org.videolan.vlc.gui.BaseFragment
import org.videolan.vlc.gui.SecondaryActivity
import org.videolan.vlc.gui.dialogs.CtxActionReceiver
import org.videolan.vlc.gui.dialogs.NetworkServerDialog
import org.videolan.vlc.gui.dialogs.showContext
import org.videolan.vlc.gui.helpers.UiTools.addToPlaylist
import org.videolan.vlc.gui.helpers.UiTools.addToPlaylistAsync
import org.videolan.vlc.gui.helpers.UiTools.showMediaInfo
import org.videolan.vlc.gui.helpers.hf.OTG_SCHEME
import org.videolan.vlc.gui.view.EmptyLoadingState
import org.videolan.vlc.gui.view.EmptyLoadingStateView
import org.videolan.vlc.gui.view.TitleListView
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.repository.BrowserFavRepository
import org.videolan.vlc.viewmodels.browser.*

@ObsoleteCoroutinesApi
@ExperimentalCoroutinesApi
class StorageDevicesFragment : BaseFragment(), View.OnClickListener, CtxActionReceiver {

    private var currentCtx: MainBrowserContainer? = null
    private lateinit var localEntry: TitleListView
    private lateinit var localViewModel: BrowserModel


    private var currentAdapterActionMode: BaseBrowserAdapter? = null

    private val containerAdapterAssociation = HashMap<MainBrowserContainer, Pair<BaseBrowserAdapter, ViewModel>>()

    override fun hasFAB() = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.storage_browser_fragment, container, false)
    }

    override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
        if (!isStarted()) return false
        val list = currentAdapterActionMode?.multiSelectHelper?.getSelection() as? List<MediaWrapper>
                ?: return false
        if (list.isNotEmpty()) {
            when (item?.itemId) {
                R.id.action_mode_file_play -> MediaUtils.openList(activity, list, 0)
                R.id.action_mode_file_append -> MediaUtils.appendMedia(activity, list)
                R.id.action_mode_file_add_playlist -> requireActivity().addToPlaylist(list)
                R.id.action_mode_file_info -> requireActivity().showMediaInfo(list[0])
                else -> {
                    stopActionMode()
                    return false
                }
            }
        }
        stopActionMode()
        return true
    }

    override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
        mode?.menuInflater?.inflate(R.menu.action_mode_browser_file, menu)
        return true
    }

    override fun onDestroyActionMode(mode: ActionMode?) {
        actionMode = null
        currentAdapterActionMode?.multiSelectHelper?.clearSelection()
        currentAdapterActionMode = null
    }

    override fun getTitle() = getString(R.string.browse)


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fabPlay.setGone()
        //local
        localEntry = view.findViewById(R.id.local_browser_entry)
        val storageBbrowserContainer = MainBrowserContainer(isNetwork = false, isFile = true)
        val storageBrowserAdapter = BaseBrowserAdapter(storageBbrowserContainer)
        localEntry.list.adapter = storageBrowserAdapter
        localViewModel = getBrowserModel(category = TYPE_FILE, url = null, showHiddenFiles = false)
        containerAdapterAssociation[storageBbrowserContainer] = Pair(storageBrowserAdapter, localViewModel)
        localViewModel.dataset.observe(viewLifecycleOwner, Observer<List<MediaLibraryItem>> { list ->
            list?.let {
                storageBrowserAdapter.update(it)
                localEntry.loading.state = when {
                    list.isNotEmpty() -> EmptyLoadingState.NONE
                    localViewModel.loading.value == true -> EmptyLoadingState.LOADING
                    else -> EmptyLoadingState.EMPTY
                }
            }
        })
        localViewModel.loading.observe(viewLifecycleOwner, Observer {
            if (it) localEntry.loading.state = EmptyLoadingState.LOADING
        })
        localViewModel.browseRoot()
        localViewModel.getDescriptionUpdate().observe(viewLifecycleOwner, Observer { pair ->
            if (pair != null) storageBrowserAdapter.notifyItemChanged(pair.first, pair.second)
        })

    }





    override fun onClick(v: View) {
        if (v.id == R.id.fab) showAddServerDialog(null)
    }

    private fun showAddServerDialog(mw: MediaWrapper?) {
        val fm = try {
            parentFragmentManager
        } catch (e: IllegalStateException) {
            return
        }
        val dialog = NetworkServerDialog()
        mw?.let { dialog.setServer(it) }
        dialog.show(fm, "fragment_add_server")
    }

    inner class MainBrowserContainer(
            override val scannedDirectory: Boolean = false,
            override val mrl: String? = null,
            override val isRootDirectory: Boolean = true,
            override val isNetwork: Boolean,
            override val isFile: Boolean,
            override val inCards: Boolean = true
    ) : BrowserContainer<MediaLibraryItem> by BrowserContainerImpl(scannedDirectory, mrl, isRootDirectory, isNetwork, isFile, inCards) {
        override fun containerActivity() = requireActivity()

        fun requireAdapter() = containerAdapterAssociation[this]?.first
                ?: throw IllegalStateException("Adapter not stored on the association map")

        private fun requireViewModel() = containerAdapterAssociation[this]?.second
                ?: throw IllegalStateException("ViewModel not stored on the association map")

        private fun checkAdapterForActionMode(): Boolean {
            val adapter = requireAdapter()
            if (currentAdapterActionMode == null) {
                currentAdapterActionMode = adapter
            } else if (currentAdapterActionMode != adapter) return false
            return true
        }

        override fun onClick(v: View, position: Int, item: MediaLibraryItem) {
            val mediaWrapper = item as MediaWrapper
            if (actionMode != null) {
                if (!checkAdapterForActionMode()) return
                val adapter = requireAdapter()
                if (mediaWrapper.type == MediaWrapper.TYPE_AUDIO ||
                        mediaWrapper.type == MediaWrapper.TYPE_VIDEO ||
                        mediaWrapper.type == MediaWrapper.TYPE_DIR) {
                    adapter.multiSelectHelper.toggleSelection(position)
                    if (adapter.multiSelectHelper.getSelection().isEmpty()) stopActionMode()
                    invalidateActionMode()
                }
            } else {
                val intent = Intent(requireActivity().applicationContext, SecondaryActivity::class.java)
                intent.putExtra(KEY_MEDIA, item)
                intent.putExtra("fragment", SecondaryActivity.FILE_BROWSER)
                startActivity(intent)
            }
        }

        override fun onLongClick(v: View, position: Int, item: MediaLibraryItem): Boolean {
            return false
        }

        override fun onImageClick(v: View, position: Int, item: MediaLibraryItem) {
            onClick(v, position, item)
        }

        override fun onCtxClick(v: View, position: Int, item: MediaLibraryItem) {

            if (actionMode == null && item.itemType == MediaLibraryItem.TYPE_MEDIA) lifecycleScope.launch {

                val mw = item as MediaWrapper
                if (mw.uri.scheme == "content" || mw.uri.scheme == OTG_SCHEME) return@launch


            }
        }
    }

    override fun onCtxAction(position: Int, option: Long) {
        val adapter = currentCtx?.requireAdapter() ?: return
        val mw = adapter.getItem(position) as? MediaWrapper
                ?: return
        when (option) {
            CTX_PLAY -> MediaUtils.openMedia(activity, mw)
            CTX_ADD_FOLDER_PLAYLIST -> requireActivity().addToPlaylistAsync(mw.uri.toString(), false)
            CTX_ADD_FOLDER_AND_SUB_PLAYLIST -> requireActivity().addToPlaylistAsync(mw.uri.toString(), true)
            CTX_FAV_EDIT -> showAddServerDialog(mw)
        }
    }
}