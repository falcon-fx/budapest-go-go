package com.falconfx.gtfsviewer.data.db

enum class RouteTypes(val typeInt: Int, val displayName: String) {
    TRAM(0, "Villamos"),
    METRO(1, "Metró"),
    BUS(3, "Busz"),
    FERRY(4, "Hajó"),
    TROLLEYBUS(11, "Trolibusz"),
    SUBURBANRAIL(109, "HÉV"),
    UNKNOWN(999, "Ismeretlen")
}