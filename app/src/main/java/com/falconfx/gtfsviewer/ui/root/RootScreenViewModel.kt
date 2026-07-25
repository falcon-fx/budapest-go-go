package com.falconfx.gtfsviewer.ui.root

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class RootScreenViewModel @Inject constructor() : ViewModel() {
    enum class Tab { MAP, TRANSPORT_LINES }

    private val _currentTab = MutableLiveData(Tab.MAP)
    val currentTab: LiveData<Tab> = _currentTab

    fun switchTab(tab: Tab) {
        _currentTab.value = tab
    }
}
