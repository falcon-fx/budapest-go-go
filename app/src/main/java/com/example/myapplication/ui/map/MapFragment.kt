package com.example.myapplication.ui.map

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myapplication.R
import com.example.myapplication.databinding.FragmentMapBinding
import com.example.myapplication.databinding.LayoutMapBinding
import com.example.myapplication.databinding.LayoutRoutesBinding
import com.example.myapplication.data.db.repo.LoadingProgress.Companion.getMessageResId
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

@AndroidEntryPoint
class MapFragment: Fragment(), RoutesAdapter.ToggleListener {
    private var currentDirection: Boolean = false
    private val LOGTAG = "MAP_FRAGMENT"
    private val viewModel: MapViewModel by viewModels()
    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!
    private lateinit var mapLayoutBinding: LayoutMapBinding
    private lateinit var routesLayoutBinding: LayoutRoutesBinding
    private lateinit var routesAdapter: RoutesAdapter
    private var progressDialog: AlertDialog? = null
    private var mapView: MapView? = null
    private var locationOverlay: MyLocationNewOverlay? = null

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 100
    }

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

        mapView = mapLayoutBinding.mapView
        setupRoutesRecyclerView()
        setupPermissionDeniedButton()

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
                    checkLocationPermission()
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
        mapView?.onResume()
        if (viewModel.currentScreen.value == MapViewModel.Screen.MAP) {
            checkLocationPermission()
        }
    }

    override fun onPause() {
        super.onPause()
        mapView?.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mapView?.onDetach()
        dismissProgressDialog()
        _binding = null
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showMap()
            } else {
                showPermissionDenied()
            }
        }
    }

    private fun checkLocationPermission() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                showMap()
            }
            ActivityCompat.shouldShowRequestPermissionRationale(
                requireActivity(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) -> {
                showPermissionDenied()
            }
            else -> {
                requestPermissions(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    LOCATION_PERMISSION_REQUEST_CODE
                )
            }
        }
    }

    private fun showMap() {
        Configuration.getInstance().userAgentValue = requireContext().packageName
        Configuration.getInstance().cacheMapTileCount = 12.toShort()
        Configuration.getInstance().cacheMapTileOvershoot = 4.toShort()

        mapLayoutBinding.permissionDeniedLayout.visibility = View.GONE
        mapView?.visibility = View.VISIBLE

        mapView?.apply {
            setMultiTouchControls(true)
            setTileSource(TileSourceFactory.MAPNIK)
            controller.setZoom(12.0)
            controller.setCenter(GeoPoint(47.4979, 19.0402))
        }

        val locationProvider = GpsMyLocationProvider(requireContext()).apply {
            addLocationSource(LocationManager.GPS_PROVIDER)
        }

        locationOverlay = MyLocationNewOverlay(locationProvider, mapView).apply {
            enableMyLocation()
        }

        locationOverlay?.let { mapView?.overlays?.add(it) }
        mapView?.invalidate()
    }

    private fun showPermissionDenied() {
        mapView?.visibility = View.GONE
        mapLayoutBinding.permissionDeniedLayout.visibility = View.VISIBLE
    }

    private fun setupPermissionDeniedButton() {
        mapLayoutBinding.btnOpenSettings.setOnClickListener {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", requireContext().packageName, null)
            }
            startActivity(intent)
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

        currentDirection = false
        loadStopsForRoute(routeId)
    }

    private fun setupRoutesRecyclerView() {
        val routesRecycler = routesLayoutBinding.transportLinesRecyclerView
        routesAdapter = RoutesAdapter(this)
        routesRecycler.adapter = routesAdapter
        routesRecycler.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun loadStopsForRoute(routeId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            Log.i(LOGTAG, "loadStopsForRoute called for $routeId, direction=$currentDirection")
            val (stops, terminusName) = withContext(Dispatchers.IO) {
                val s = viewModel.getStopsOfRoute(routeId, currentDirection, reverse = false)
                val t = viewModel.getFinalStopNameOfRoute(routeId, currentDirection)
                s to t
            }
            Log.i(LOGTAG, "mapFragment: fetched stops.size=${stops.size} for routeId=$routeId; terminus=$terminusName")
            withContext(Dispatchers.Main) {
                routesAdapter.insertStopsForRoute(routeId, stops, currentDirection, terminusName)
                val pos = routesAdapter.findRoutePosition(routeId)
                if(pos >= 0) {
                    (routesLayoutBinding.transportLinesRecyclerView.layoutManager)?.scrollToPosition(pos)
                }
            }
        }
    }

    override fun onDirectionToggle(routeId: String, directionId: Boolean) {
        Log.i(LOGTAG, "onDirectionToggle called for $routeId, switching from direction=$currentDirection")
        currentDirection = !directionId
        routesAdapter.removeStopsForRoute(routeId)
        loadStopsForRoute(routeId)
    }
}
