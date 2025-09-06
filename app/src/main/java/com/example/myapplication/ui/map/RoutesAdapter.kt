package com.example.myapplication.ui.map

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.data.db.RouteEntity
import com.example.myapplication.data.db.StopEntity
import com.example.myapplication.data.util.DataParsers

sealed class ListItem {
    data class RouteItem(val route: RouteEntity) : ListItem()
    data class StopItem(val stop: StopEntity, val parentRouteId: String) : ListItem()
}

class RoutesAdapter(
    private val toggleListener: ToggleListener
): RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val LOGTAG = "ROUTES_ADAPTER"
    companion object {
        private const val TYPE_ROUTE = 1
        private const val TYPE_STOP = 2
    }

    private val items = mutableListOf<ListItem>()
    private val expandedRoutesIds = linkedSetOf<String>()

    interface ToggleListener {
        fun onRouteToggle(routeId: String, currentlyExpanded: Boolean)
    }

    fun debugDump(): String {
        val routeCount = items.count { it is ListItem.RouteItem }
        val stopCount = items.count { it is ListItem.StopItem }
        return "items.size=${items.size} routes=$routeCount stops=$stopCount expanded=$expandedRoutesIds"
    }

    fun setRoutes(routes: List<RouteEntity>) {
        Log.i(LOGTAG, "setRoutes called, ${routes.size}")
        items.clear()
        items.addAll(routes.map { ListItem.RouteItem(it) })
        expandedRoutesIds.clear()
        notifyDataSetChanged()
    }

    fun findRoutePosition(routeId: String): Int {
        return items.indexOfFirst { it is ListItem.RouteItem && it.route.id == routeId }
    }

    fun insertStopsForRoute(routeId: String, stops: List<StopEntity>) {
        if(stops.isEmpty()) return
        if(expandedRoutesIds.contains(routeId)) return
        val routeIndex = findRoutePosition(routeId)
        if(routeIndex == -1) {
            Log.i(LOGTAG, "insertStops: could not find route $routeId")
            return
        }


        val stopItems = stops.map { ListItem.StopItem(it, routeId) }
        val insertAt = routeIndex + 1
        items.addAll(insertAt, stopItems)
        expandedRoutesIds.add(routeId)
        notifyItemRangeInserted(insertAt, stopItems.size)
        notifyItemChanged(routeIndex)
        Log.i(LOGTAG, "insertStops: inserted $routeId stops at $insertAt")
    }
    fun removeStopsForRoute(routeId: String) {
        val routeIndex = findRoutePosition(routeId)
        if(routeIndex == -1) {
            Log.i(LOGTAG, "removeStops: could not find route $routeId")
            return
        }
        if (!expandedRoutesIds.contains(routeId)) {
            Log.i(LOGTAG, "removeStops: expandedRoutesIds doesn't contain routeId: $expandedRoutesIds")
            return
        }
        val start = routeIndex + 1
        var end = start
        /**val iterator = items.listIterator()
        val toRemoveIndices = mutableListOf<Int>()*/
        while(end < items.size) {
            val it = items[end]
            if(it is ListItem.StopItem && it.parentRouteId == routeId) {
                end++
            } else {
                break
            }
        }

        val removedCount = end - start

        if(removedCount > 0) {
            items.subList(start, end).clear()
            expandedRoutesIds.remove(routeId)
            notifyItemRangeRemoved(start, removedCount)
            notifyItemChanged(routeIndex)
            Log.i(LOGTAG, "removeStops: removed $removedCount stops for route $routeId starting at $start")
        } else {
            Log.i(LOGTAG, "removeStops: no stops to remove for $routeId")
        }
        /**
        while(iterator.hasNext()) {
            val item = iterator.next()
            if(item is ListItem.StopItem && item.parentRouteId == routeId) {
                Log.i(LOGTAG, "removeStopsForRoute: item: $item")
                toRemoveIndices.add(index)
            }
            index++
        }

        if(toRemoveIndices.isEmpty()) {
            Log.i(LOGTAG, "removeStopsForRoute: toRemoveIndices is empty")
            return
        }

        for(i in toRemoveIndices.asReversed()) {
            Log.i(LOGTAG, "removeStopsForRoute: removing ${items[i]}")
            items.removeAt(i)
        }

        removedCount = toRemoveIndices.size
        expandedRoutesIds.remove(routeId)

        val first = toRemoveIndices.first()
        notifyItemRangeRemoved(first, removedCount)*/

    }

    fun isRouteExpanded(routeId: String) = expandedRoutesIds.contains(routeId)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_ROUTE -> {
                val view = inflater.inflate(R.layout.item_route, parent, false)
                RouteViewHolder(view)
            }
            TYPE_STOP -> {
                val view = inflater.inflate(R.layout.item_stop, parent, false)
                StopViewHolder(view)
            }
            else -> throw IllegalArgumentException("Unknown viewType $viewType")
        }
    }

    override fun getItemCount(): Int = items.size
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when(val item = items[position]) {
            is ListItem.RouteItem -> (holder as RouteViewHolder).bind(item.route)
            is ListItem.StopItem -> (holder as StopViewHolder).bind(item.stop)
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when(items[position]) {
            is ListItem.RouteItem -> TYPE_ROUTE
            is ListItem.StopItem -> TYPE_STOP
        }
    }

    inner class RouteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvShortName: TextView = itemView.findViewById(R.id.tvRouteShortName)
        private val tvHeadsign: TextView = itemView.findViewById(R.id.tvRouteHeadsign)
        private val btnExpand: ImageButton = itemView.findViewById(R.id.btnExpandRouteStops)

        private var currentRouteId: String? = null

        fun bind(route: RouteEntity) {
            currentRouteId = route.id
            tvShortName.apply {
                text = route.shortName
                setTextColor(DataParsers.parseStringToColor(route.textColor, true))
                setBackgroundColor(DataParsers.parseStringToColor(route.color, true))
            }
            tvHeadsign.text = route.desc

            val expanded = expandedRoutesIds.contains(route.id)
            btnExpand.rotation = if(expanded) 180f else 0f

            val clickAction = View.OnClickListener {
                val rId = currentRouteId ?: return@OnClickListener
                val isExpandedNow = expandedRoutesIds.contains(rId)
                Log.i(LOGTAG, "RouteViewHolder bind, isExpanded: $isExpandedNow")
                toggleListener.onRouteToggle(rId, isExpandedNow)
            }

            itemView.setOnClickListener(clickAction)
            btnExpand.setOnClickListener(clickAction)
        }
    }

    inner class StopViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvStopName: TextView = itemView.findViewById(R.id.tvStopName)
        private val ivCurrentStopIndicator: ImageView = itemView.findViewById(R.id.ivCurrentStopIndicator)

        fun bind(stop: StopEntity) {
            tvStopName.text = stop.name
        }
    }
}