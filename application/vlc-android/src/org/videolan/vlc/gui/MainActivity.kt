package org.videolan.vlc.gui

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.ActionBar
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.KeyEvent
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toolbar
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.view.ActionMode
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import com.google.android.material.navigation.NavigationView
import com.qh.mplayer.utils.LogUtils
import kotlinx.android.synthetic.main.toolbar.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.resources.ACTIVITY_RESULT_OPEN
import org.videolan.resources.ACTIVITY_RESULT_PREFERENCES
import org.videolan.resources.ACTIVITY_RESULT_SECONDARY
import org.videolan.resources.EXTRA_TARGET
import org.videolan.tools.*
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.R
import org.videolan.vlc.StartActivity
import org.videolan.vlc.extensions.ExtensionManagerService
import org.videolan.vlc.extensions.ExtensionsManager
import org.videolan.vlc.gui.audio.AudioBrowserFragment
import org.videolan.vlc.gui.browser.BaseBrowserFragment
import org.videolan.vlc.gui.browser.ExtensionBrowser
import org.videolan.vlc.gui.browser.MainBrowserFragment
import org.videolan.vlc.gui.browser.NetworkBrowserFragment
import org.videolan.vlc.gui.helpers.INavigator
import org.videolan.vlc.gui.helpers.Navigator
import org.videolan.vlc.gui.helpers.UiTools
import org.videolan.vlc.gui.preferences.PreferencesActivity
import org.videolan.vlc.gui.video.VideoGridFragment
import org.videolan.vlc.interfaces.Filterable
import org.videolan.vlc.interfaces.IRefreshable
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.reloadLibrary
import org.videolan.vlc.util.Permissions
import org.videolan.vlc.util.Util

private const val TAG = "MPlayer/MainActivity"

@ExperimentalCoroutinesApi
@ObsoleteCoroutinesApi
class MainActivity : ContentActivity(),ExtensionManagerService.ExtensionManagerActivity,INavigator by Navigator(),NavigationView.OnNavigationItemSelectedListener
{
    lateinit var toolbarTitle:TextView
    var  isVideoByName=true
    lateinit var drawerLayout:DrawerLayout
    lateinit var naviMenu:NavigationView
    var refreshing: Boolean = false
        set(value) {
            mainLoading.visibility = if (value) View.VISIBLE else View.GONE
            field = value
        }
    private lateinit var mediaLibrary: Medialibrary
    private var scanNeeded = false

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Util.checkCpuCompatibility(this)
        /*** Start initializing the UI  */
        setContentView(R.layout.main)
        initAudioPlayerContainerActivity()
        setupNavigation(savedInstanceState)//======================================================================================================
        if (savedInstanceState == null) Permissions.checkReadStoragePermission(this)

        /* Set up the action bar */
        prepareActionBar()
        /* Reload the latest preferences */
        scanNeeded = savedInstanceState == null && settings.getBoolean(KEY_MEDIALIBRARY_AUTO_RESCAN, true)
        if (BuildConfig.DEBUG) extensionsManager = ExtensionsManager.getInstance()//====================================================================================================
        mediaLibrary = Medialibrary.getInstance()

        val color = TypedValue().run {
            theme.resolveAttribute(R.attr.progress_indeterminate_tint, this, true)
            data
        }
        mainLoadingProgress.indeterminateDrawable.setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN)
        var subView=findViewById<View>(R.id.appbar)
        toolbarTitle= subView.findViewById(R.id.toolbar_vlc_title)
        LogUtils.loge("======$subView")
        LogUtils.loge("======$title")
        toolbarTitle.setText(getString(R.string.video))
        drawerLayout=findViewById<DrawerLayout>(R.id.main_drawer)
        naviMenu=findViewById<NavigationView>(R.id.menu_drawer)
        val toggle=ActionBarDrawerToggle(this,
                drawerLayout,
                toolbar,
                R.string.navigation_drawer_open,
                R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        naviMenu.setNavigationItemSelectedListener(this)
    }


    private fun prepareActionBar() {
        supportActionBar?.run {
            setDisplayHomeAsUpEnabled(false)
            setHomeButtonEnabled(false)
            setDisplayShowTitleEnabled(false)
        }
    }

    override fun onStart() {
        super.onStart()
        if (mediaLibrary.isInitiated) {
            /* Load media items from database and storage */
            if (scanNeeded && Permissions.canReadStorage(this)) this.reloadLibrary()
        }
    }

    override fun onStop() {
        super.onStop()
        if (changingConfigurations == 0) {
            /* Check for an ongoing scan that needs to be resumed during onResume */
            scanNeeded = mediaLibrary.isWorking
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        val current = currentFragment
        if (current !is ExtensionBrowser) supportFragmentManager.putFragment(outState, "current_fragment", current!!)
        outState.putInt(EXTRA_TARGET, currentFragmentId)//===============================================================================================
        super.onSaveInstanceState(outState)
    }

    override fun onRestart() {
        super.onRestart()
        /* Reload the latest preferences */
        reloadPreferences()//=========================================================================================================
    }

    @TargetApi(Build.VERSION_CODES.N)
    override fun onBackPressed() {/* Close playlist search if open or Slide down the audio player if it is shown entirely. */
        if (isAudioPlayerReady && (audioPlayer.backPressed() || slideDownAudioPlayer()))
            return

        // If it's the directory view, a "backpressed" action shows a parent.
        val fragment = currentFragment
        if (fragment is BaseBrowserFragment && fragment.goBack()) {
            return
        } else if (fragment is ExtensionBrowser) {
            fragment.goBack()
            return
        }
        if (AndroidUtil.isNougatOrLater && isInMultiWindowMode) {
            UiTools.confirmExit(this)
            return
        }

        finish()
    }

    override fun startSupportActionMode(callback: ActionMode.Callback): ActionMode? {
        appBarLayout.setExpanded(true)
        return super.startSupportActionMode(callback)
    }

    /**
     * Handle onClick form menu buttons
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId != R.id.ml_menu_filter) UiTools.setKeyboardVisibility(appBarLayout, false)

        // Handle item selection
        return when (item.itemId) {
            // Refresh
            R.id.ml_menu_refresh -> {
                forceRefresh()
                true
            }
            android.R.id.home ->
                // Slide down the audio player or toggle the sidebar
                slideDownAudioPlayer()
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onMenuItemActionExpand(item: MenuItem): Boolean {
        return if (currentFragment is Filterable) {
            (currentFragment as Filterable).allowedToExpand()
        } else false
    }

    fun forceRefresh() {
        forceRefresh(currentFragment)
    }

    private fun forceRefresh(current: Fragment?) {
        if (!mediaLibrary.isWorking) {
            if (current != null && current is IRefreshable)
                (current as IRefreshable).refresh()
            else
                reloadLibrary()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        //if (VLCBilling.getInstance(this.application).iabHelper.handleActivityResult(requestCode, resultCode, data)) return
        if (requestCode == ACTIVITY_RESULT_PREFERENCES) {
            when (resultCode) {
                RESULT_RESCAN -> this.reloadLibrary()
                RESULT_RESTART, RESULT_RESTART_APP -> {
                    val intent = Intent(this@MainActivity, if (resultCode == RESULT_RESTART_APP) StartActivity::class.java else MainActivity::class.java)
                    finish()
                    startActivity(intent)
                }
                RESULT_UPDATE_SEEN_MEDIA -> for (fragment in supportFragmentManager.fragments)
                    if (fragment is VideoGridFragment)
                        fragment.updateSeenMediaMarker()
                RESULT_UPDATE_ARTISTS -> {
                    val fragment = currentFragment
                    if (fragment is AudioBrowserFragment) fragment.viewModel.refresh()
                }
            }
        } else if (requestCode == ACTIVITY_RESULT_OPEN && resultCode == Activity.RESULT_OK) {
            MediaUtils.openUri(this, data!!.data)
        } else if (requestCode == ACTIVITY_RESULT_SECONDARY) {
            if (resultCode == RESULT_RESCAN) {
                forceRefresh(currentFragment)
            }
        }
    }

    // Note. onKeyDown will not occur while moving within a list
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_SEARCH) {
            toolbar.menu.findItem(R.id.ml_menu_filter).expandActionView()
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        LogUtils.loge("=========item:.itemId${item.itemId}          fragementId${currentFragment?.id}")
        if(!isOpeningCurrentFragment(item))
        {
            showFragment(item.itemId)
        }
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    private fun isOpeningCurrentFragment(item:MenuItem):Boolean{
       var fragmentName=when(item.itemId)
        {
            R.id.nav_audio -> "AudioBrowserFragment"
            R.id.nav_directories -> "MainBrowserFragment"
            R.id.nav_network -> "NetworkBrowserFragment"
           R.id.nav_playlists->"PlaylistFragment"
           R.id.nav_favorites->{
               val i = Intent(this, SecondaryActivity::class.java)
               i.putExtra("fragment", SecondaryActivity.FAVORITES)
               startActivityForResult(i, SecondaryActivity.ACTIVITY_RESULT_SECONDARY)
               return true
           }
           R.id.nav_history->{
               val i = Intent(this, SecondaryActivity::class.java)
               i.putExtra("fragment", SecondaryActivity.HISTORY)
               startActivityForResult(i, SecondaryActivity.ACTIVITY_RESULT_SECONDARY)
               return true
           }
           R.id.nav_settings->{
               startActivityForResult(Intent(this, PreferencesActivity::class.java), ACTIVITY_RESULT_PREFERENCES)
               return true
           }
           R.id.nav_stream->{
               val i = Intent(this, SecondaryActivity::class.java)
               i.putExtra("fragment", SecondaryActivity.STREAMS)
               startActivityForResult(i, SecondaryActivity.ACTIVITY_RESULT_SECONDARY)
               return true
           }
           /* R.id.nav_more -> "MoreFragment"*/
          /* R.id.nav_stream->""*/
            else -> "VideoGridFragment"
        }
        return fragmentName.equals(currentFragment!!::class.simpleName)
    }



    fun  getVideoOrFolders():String{
       return if(isVideoByName)getString(R.string.video) else getString(R.string.folders)
    }

}
