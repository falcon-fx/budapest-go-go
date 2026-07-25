package com.falconfx.gtfsviewer.ui.map

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.falconfx.gtfsviewer.R
import com.falconfx.gtfsviewer.databinding.LayoutRoutesBinding
import com.falconfx.gtfsviewer.data.db.RouteTypes
import com.falconfx.gtfsviewer.data.db.repo.LoadingProgress.Companion.getMessageResId
import com.falconfx.gtfsviewer.data.util.DataParsers
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class TransportLinesFragment : Fragment(), RoutesAdapter.ToggleListener {
    private var currentDirection: Boolean = false
    private val LOGTAG = "TRANSPORT_FRAGMENT"
    private val viewModel: TransportLinesViewModel by viewModels()
    private var _binding: LayoutRoutesBinding? = null
    private val binding get() = _binding!!
    private lateinit var routesAdapter: RoutesAdapter
    private var progressDialog: AlertDialog? = null
    private val selectedPills = mutableSetOf<RouteTypes>()
    private var pillCornerRadiusPx = 0f
    private var pillStrokePx = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = LayoutRoutesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = LayoutRoutesBinding.bind(view)

        pillCornerRadiusPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 18f, resources.displayMetrics
        )
        pillStrokePx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 1.5f, resources.displayMetrics
        ).toInt()

        setupRoutesRecyclerView()
        setupSearch()
        viewModel.loadRoutes()

        viewModel.typeColors.observe(viewLifecycleOwner) { colors ->
            if (colors.isNotEmpty()) {
                binding.pillLayout.removeAllViews()
                createPills(colors)
            }
        }

        viewModel.loading.observe(viewLifecycleOwner) { isLoading ->
            if (isLoading) {
                showProgressDialog()
            } else {
                dismissProgressDialog()
            }
        }

        viewModel.loadingProgress.observe(viewLifecycleOwner) { progress ->
            updateProgressDialog(progress)
        }

        viewModel.searchResult.observe(viewLifecycleOwner) { result ->
            Log.i(LOGTAG, "searchResult observer: size = ${result.routes.size}")
            routesAdapter.setRoutes(result.routes)
            result.preExpandRouteId?.let { routeId ->
                loadStopsForRoute(routeId)
            }
        }

        binding.btnFetchTimetable.setOnClickListener {
            val hasData = viewModel.routes.value?.isNotEmpty() == true
            val title = if (hasData) R.string.dialog_sync_title else R.string.dialog_download_title

            AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setMessage(R.string.dialog_action_warning)
                .setPositiveButton(android.R.string.yes) { _, _ ->
                    viewModel.fetchTimetable(requireContext().cacheDir)
                }
                .setNegativeButton(android.R.string.no, null)
                .show()
        }

        viewModel.certError.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { message ->
                AlertDialog.Builder(requireContext())
                    .setTitle("Certificate Required")
                    .setMessage(message)
                    .setPositiveButton("Go to Setup") { _, _ ->
                        findNavController().navigate(R.id.action_nav_map_to_nav_auth)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (viewModel.loading.value == true) {
            showProgressDialog()
        }
    }

    override fun onPause() {
        super.onPause()
        dismissProgressDialog()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        dismissProgressDialog()
        selectedPills.clear()
        _binding = null
    }

    override fun onRouteToggle(routeId: String, currentlyExpanded: Boolean) {
        Log.i(LOGTAG, "onRouteToggle called. id: $routeId, expanded? $currentlyExpanded")
        if (currentlyExpanded) {
            routesAdapter.removeStopsForRoute(routeId)
            Log.i(LOGTAG, "onRouteToggle, removed stops for $routeId, dump: ${routesAdapter.debugDump()}")
            return
        }

        currentDirection = false
        loadStopsForRoute(routeId)
    }

    override fun onDirectionToggle(routeId: String, directionId: Boolean) {
        Log.i(LOGTAG, "onDirectionToggle called for $routeId, switching from direction=$currentDirection")
        currentDirection = !directionId
        routesAdapter.removeStopsForRoute(routeId)
        loadStopsForRoute(routeId)
    }

    private fun setupRoutesRecyclerView() {
        routesAdapter = RoutesAdapter(this)
        binding.transportLinesRecyclerView.adapter = routesAdapter
        binding.transportLinesRecyclerView.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun setupSearch() {
        binding.btnSearch.setOnClickListener {
            val query = binding.routesSearchBar.text.toString()
            viewModel.search(query)
        }

        binding.routesSearchBar.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = binding.routesSearchBar.text.toString()
                viewModel.search(query)
                true
            } else {
                false
            }
        }
    }

    private fun createPills(colors: Map<RouteTypes, Pair<String, String>>) {
        val types = RouteTypes.entries.filter { it != RouteTypes.UNKNOWN }
        val pillLayout = binding.pillLayout

        val px = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 14f, resources.displayMetrics
        ).toInt()
        val py = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 6f, resources.displayMetrics
        ).toInt()
        val marginEnd = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 6f, resources.displayMetrics
        ).toInt()

        for (type in types) {
            val routeColors = colors[type]
            val bgColor = if (routeColors != null) {
                DataParsers.parseStringToColor(routeColors.first, false)
            } else Color.GRAY
            val textColor = if (routeColors != null) {
                DataParsers.parseStringToColor(routeColors.second, true)
            } else Color.WHITE

            val button = Button(requireContext(), null, android.R.attr.buttonStyleSmall).apply {
                text = type.displayName
                isAllCaps = false
                setPadding(px, py, px, py)
                minWidth = 0
                minHeight = 0
                textSize = 13f
                setOnClickListener {
                    val nowSelected = !selectedPills.contains(type)
                    if (nowSelected) selectedPills.add(type) else selectedPills.remove(type)
                    updatePillStyle(this, bgColor, textColor, nowSelected)
                    viewModel.toggleType(type)
                }
                updatePillStyle(this, bgColor, textColor, false)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    this.marginEnd = marginEnd
                }
            }
            pillLayout.addView(button)
        }
    }

    private fun updatePillStyle(button: Button, bgColor: Int, textColor: Int, selected: Boolean) {
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = pillCornerRadiusPx
            if (selected) {
                setColor(bgColor)
            } else {
                setColor(Color.TRANSPARENT)
                setStroke(pillStrokePx, bgColor)
            }
        }
        button.background = bg
        button.setTextColor(if (selected) textColor else bgColor)
    }

    private fun loadStopsForRoute(routeId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            Log.i(LOGTAG, "loadStopsForRoute called for $routeId, direction=$currentDirection")
            val (stops, terminusName) = withContext(Dispatchers.IO) {
                val s = viewModel.getStopsOfRoute(routeId, currentDirection, reverse = false)
                val t = viewModel.getFinalStopNameOfRoute(routeId, currentDirection)
                s to t
            }
            Log.i(LOGTAG, "transportFragment: fetched stops.size=${stops.size} for routeId=$routeId; terminus=$terminusName")
            withContext(Dispatchers.Main) {
                routesAdapter.insertStopsForRoute(routeId, stops, currentDirection, terminusName)
                val pos = routesAdapter.findRoutePosition(routeId)
                if (pos >= 0) {
                    binding.transportLinesRecyclerView.layoutManager?.scrollToPosition(pos)
                }
            }
        }
    }

    private fun showProgressDialog() {
        if (progressDialog == null) {
            val dialogView = layoutInflater.inflate(R.layout.dialog_loading_progress, null)
            progressDialog = AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .setCancelable(false)
                .create()
        }
        progressDialog?.show()
    }

    private fun dismissProgressDialog() {
        progressDialog?.dismiss()
        progressDialog = null
    }

    private fun updateProgressDialog(progress: com.falconfx.gtfsviewer.data.db.repo.LoadingProgress) {
        progressDialog?.findViewById<android.widget.TextView>(R.id.tvProgress)?.text = getString(
            progress.phase.getMessageResId(),
            progress.percentage
        )
    }
}
