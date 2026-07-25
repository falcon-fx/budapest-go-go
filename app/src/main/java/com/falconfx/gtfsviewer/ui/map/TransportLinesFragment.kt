package com.falconfx.gtfsviewer.ui.map

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
import com.falconfx.gtfsviewer.R
import com.falconfx.gtfsviewer.databinding.LayoutRoutesBinding
import com.falconfx.gtfsviewer.data.db.repo.LoadingProgress.Companion.getMessageResId
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

        setupRoutesRecyclerView()
        viewModel.loadRoutes()

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

        binding.btnFetchTimetable.setOnClickListener {
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
