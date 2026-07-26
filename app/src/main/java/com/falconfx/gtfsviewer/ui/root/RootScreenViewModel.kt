package com.falconfx.gtfsviewer.ui.root

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class RootScreenViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    enum class Tab(val tag: String) {
        MAP("OsmMapFragment"),
        TRANSPORT_LINES("TransportLinesFragment")
    }

    companion object {
        const val CURRENT_TAB = "CURRENT_TAB"
    }

    private val _currentTab = MutableLiveData(
        savedStateHandle.get<Tab>(CURRENT_TAB) ?: Tab.MAP
    )
    val currentTab: LiveData<Tab> = _currentTab

    fun switchTab(tab: Tab) {
        savedStateHandle[CURRENT_TAB] = tab
        _currentTab.value = tab
    }
}
