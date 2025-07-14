package com.example.myapplication.data.util

import androidx.room.TypeConverter
import com.example.myapplication.data.db.RouteTypes

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