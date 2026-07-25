package com.falconfx.gtfsviewer.ui.map

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.falconfx.gtfsviewer.databinding.LayoutMapBinding
import dagger.hilt.android.AndroidEntryPoint
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

@AndroidEntryPoint
class OsmMapFragment : Fragment() {
    private var _binding: LayoutMapBinding? = null
    private val binding get() = _binding!!
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
        _binding = LayoutMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = LayoutMapBinding.bind(view)

        mapView = binding.mapView
        setupPermissionDeniedButton()
        checkLocationPermission()
    }

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
        checkLocationPermission()
    }

    override fun onPause() {
        super.onPause()
        mapView?.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mapView?.onDetach()
        mapView = null
        locationOverlay = null
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
        binding.permissionDeniedLayout.visibility = View.GONE
        mapView?.visibility = View.VISIBLE

        mapView?.apply {
            setMultiTouchControls(true)
            setTileSource(TileSourceFactory.MAPNIK)
            controller.setZoom(12.0)
            controller.setCenter(GeoPoint(47.4979, 19.0402))
        }

        if (locationOverlay == null) {
            val locationProvider = GpsMyLocationProvider(requireContext()).apply {
                addLocationSource(LocationManager.GPS_PROVIDER)
            }
            locationOverlay = MyLocationNewOverlay(locationProvider, mapView).apply {
                enableMyLocation()
            }
            locationOverlay?.let { mapView?.overlays?.add(it) }
        }
        mapView?.invalidate()
    }

    private fun showPermissionDenied() {
        mapView?.visibility = View.GONE
        binding.permissionDeniedLayout.visibility = View.VISIBLE
    }

    private fun setupPermissionDeniedButton() {
        binding.btnOpenSettings.setOnClickListener {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", requireContext().packageName, null)
            }
            startActivity(intent)
        }
    }
}
