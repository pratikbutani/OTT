package com.isuncloud.ott.ui.main

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings.Secure
import android.support.v17.leanback.app.VerticalGridSupportFragment
import android.support.v17.leanback.widget.*
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.isuncloud.ott.BuildConfig
import com.isuncloud.ott.R
import com.isuncloud.ott.presenter.CardItemPresenter
import com.isuncloud.ott.repository.model.LauncherAppItem
import com.isuncloud.ott.repository.model.api.AppItem
import java.util.*
import kotlin.collections.ArrayList

class MainFragment: VerticalGridSupportFragment() {

    companion object {
        private const val NUM_COLUMNS = 5
    }

    private lateinit var viewModel: MainViewModel
    private lateinit var packageManager: PackageManager
    private lateinit var intent: Intent
    private lateinit var launcherStatusTimer: Timer
    private lateinit var launcherAppUpdateTimer: Timer

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        setupView()
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = ViewModelProviders.of(this).get(MainViewModel::class.java)
        onItemViewClickedListener = ItemViewClickedListener()
        setupData()
        observeData()
    }

    override fun onResume() {
        startJobs()

        if(viewModel.isClickApp) {
            viewModel.makeLightTx()
        }
        viewModel.isClickApp = false

        super.onResume()
    }

    override fun onPause() {
        stopJobs()
        super.onPause()
    }

    private fun setupView() {
        val gridPresenter = VerticalGridPresenter()
        gridPresenter.numberOfColumns = MainFragment.NUM_COLUMNS
        setGridPresenter(gridPresenter)
        title = getString(R.string.main_name)
    }

    private fun setupData() {
        var cardRowAdapter = ArrayObjectAdapter(CardItemPresenter())

        viewModel.deviceId = Secure.getString(activity?.contentResolver, Secure.ANDROID_ID)

        packageManager = activity!!.packageManager
        intent = Intent(Intent.ACTION_MAIN, null)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)

        val apps = packageManager.queryIntentActivities(intent, 0)
        apps.forEach {
            if(it.activityInfo.packageName != BuildConfig.APPLICATION_ID) {
                val appInfo = LauncherAppItem(
                        it.activityInfo.packageName,
                        it.loadLabel(packageManager).toString(),
                        it.activityInfo.loadIcon(packageManager))
                cardRowAdapter.add(appInfo)
            }
        }

        adapter = cardRowAdapter
    }

    private fun observeData() {
        viewModel.lightTxJson.observe(this, Observer {
            viewModel.updateAppExecRecord(it.toString())
        })
    }

    private fun startJobs() {
        val launcherStatusTimerTask = object: TimerTask() {
            override fun run() {
                if(viewModel.isInitRn) {
                    viewModel.launcherStatusResp()
                }
            }
        }
        launcherStatusTimer = Timer()
        launcherStatusTimer.schedule(launcherStatusTimerTask, 0, 60000)

        val launcherAppUpdateTimerTask = object: TimerTask() {
            override fun run() {
                if(viewModel.isInitRn) {
                    viewModel.launcherAppUpdate(getLauncherApp())
                }
            }
        }
        launcherAppUpdateTimer = Timer()
        launcherAppUpdateTimer.schedule(launcherAppUpdateTimerTask, 0, 120000)
    }

    private fun stopJobs() {
        launcherStatusTimer.cancel()
        launcherAppUpdateTimer.cancel()
    }

    private fun getLauncherApp(): ArrayList<AppItem> {
        val appItems = arrayListOf<AppItem>()
        val resolveInfos = packageManager.queryIntentActivities(intent, 0)
        resolveInfos.forEach {
            val appItem = AppItem(
                    it.activityInfo.packageName,
                    it.loadLabel(packageManager).toString())
            appItems.add(appItem)
        }
        return appItems
    }

    private inner class ItemViewClickedListener: OnItemViewClickedListener {
        override fun onItemClicked(
                itemViewHolder: Presenter.ViewHolder?,
                item: Any?,
                rowViewHolder: RowPresenter.ViewHolder?,
                row: Row?) {
            if(item is LauncherAppItem) {
                viewModel.insertAppExecRecord(item.appId, item.appName)
                viewModel.isClickApp = true
                val intent = packageManager.getLaunchIntentForPackage(item.appId)
                startActivity(intent)
            }
        }
    }

}