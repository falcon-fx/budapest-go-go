package com.falconfx.gtfsviewer.ui.map

sealed class MapEvent {
    data class LocationPermissionDenied(val permanentlyDenied: Boolean) : MapEvent()
}
