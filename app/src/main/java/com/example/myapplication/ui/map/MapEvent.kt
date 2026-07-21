package com.example.myapplication.ui.map

sealed class MapEvent {
    data class LocationPermissionDenied(val permanentlyDenied: Boolean) : MapEvent()
}
