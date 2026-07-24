package com.falconfx.gtfsviewer.data.util

import androidx.room.TypeConverter
import com.falconfx.gtfsviewer.data.db.RouteTypes

class BkkTypeConverters {
    @TypeConverter
    fun fromIntToRouteTypes(typeInt: Int): RouteTypes {
        return RouteTypes.entries.first() { it.typeInt == typeInt }
    }

    @TypeConverter
    fun fromRouteTypesToInt(routeType: RouteTypes): Int {
        return routeType.typeInt
    }
}