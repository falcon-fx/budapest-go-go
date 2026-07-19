package com.example.myapplication.ui.map

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView.LayoutManager
import com.example.myapplication.R
import com.example.myapplication.databinding.FragmentMapBinding
import com.example.myapplication.databinding.LayoutMapBinding
import com.example.myapplication.databinding.LayoutRoutesBinding
import com.example.myapplication.data.db.repo.LoadingProgress.Companion.getMessageResId
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class MapFragment: Fragment(), RoutesAdapter.ToggleListener {
    private val LOGTAG = "MAP_FRAGMENT"
    private val viewModel: MapViewModel by viewModels()
    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!
    private lateinit var mapLayoutBinding: LayoutMapBinding
    private lateinit var routesLayoutBinding: LayoutRoutesBinding
    private lateinit var routesAdapter: RoutesAdapter
    private var progressDialog: AlertDialog? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.i(viewModel.logTag, "onCreateView called")
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Log.i(viewModel.logTag, "onViewCreated called")
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentMapBinding.bind(view)


        mapLayoutBinding = binding.mapLayout
        routesLayoutBinding = binding.routesLayout

        setupRoutesRecyclerView()

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

        viewModel.routes.observe(viewLifecycleOwner) { routes ->
            Log.i(LOGTAG, "routes observer: size = ${routes.size}")
            routesAdapter.setRoutes(routes)
        }

        viewModel.currentScreen.observe(viewLifecycleOwner) { screen ->
            when(screen) {
                MapViewModel.Screen.MAP -> {
                    mapLayoutBinding.layoutRoot.visibility = View.VISIBLE
                    routesLayoutBinding.layoutRoot.visibility = View.GONE
                }
                MapViewModel.Screen.TIMETABLE -> {
                    mapLayoutBinding.layoutRoot.visibility = View.GONE
                    routesLayoutBinding.layoutRoot.visibility = View.VISIBLE
                    Log.i(viewModel.logTag, "TIMETABLE screen shows")
                    viewModel.loadRoutes()
                }
                null -> {}
            }
        }
        binding.bottomNavBar.setOnItemSelectedListener { item ->
            when(item.itemId) {
                R.id.nav_map -> viewModel.switchScreens(MapViewModel.Screen.MAP)
                R.id.nav_routes -> viewModel.switchScreens(MapViewModel.Screen.TIMETABLE)
            }
            true
        }
        routesLayoutBinding.btnFetchTimetable.setOnClickListener {
            viewModel.fetchTimetable(requireContext().cacheDir)
        }

        // Observe certificate errors
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

    override fun onDestroyView() {
        super.onDestroyView()
        dismissProgressDialog()
        _binding = null
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

    private fun updateProgressDialog(progress: com.example.myapplication.data.db.repo.LoadingProgress) {
        progressDialog?.findViewById<android.widget.TextView>(R.id.tvProgress)?.text = getString(
            progress.phase.getMessageResId(),
            progress.percentage
        )
    }

    override fun onRouteToggle(routeId: String, currentlyExpanded: Boolean) {

        Log.i(LOGTAG, "onRouteToggle called. id: $routeId, expanded? $currentlyExpanded")
        if(currentlyExpanded) {
            routesAdapter.removeStopsForRoute(routeId)
            Log.i(LOGTAG, "onRouteToggle, removed stops for $routeId, dump: ${routesAdapter.debugDump()}")
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            Log.i(LOGTAG, "onRouteToggle getting stops")
            withContext(Dispatchers.IO) {Log.i(LOGTAG, "stops query: ${viewModel.getStopsOfRoute(routeId, reverse = false)}")}
            val stops = withContext(Dispatchers.IO) {

                viewModel.getStopsOfRoute(routeId, reverse = false)


            }
            Log.i(LOGTAG, "mapFragment: fetched stops.size=${stops.size} for routeId=$routeId; first=${stops.firstOrNull()?.let { it.id ?: it.name } ?: "none"}")
            withContext(Dispatchers.Main) {
                routesAdapter.insertStopsForRoute(routeId, stops)
                val pos = routesAdapter.findRoutePosition(routeId)
                if(pos >= 0) {
                    (routesLayoutBinding.transportLinesRecyclerView.layoutManager)?.scrollToPosition(pos)
                }
            }

        }
    }

    private fun setupRoutesRecyclerView() {
        val routesRecycler = routesLayoutBinding.transportLinesRecyclerView
        routesAdapter = RoutesAdapter(this)
        routesRecycler.adapter = routesAdapter
        routesRecycler.layoutManager = LinearLayoutManager(requireContext())
    }
}