package com.example.myapplication.ui.map

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.myapplication.R
import com.example.myapplication.databinding.FragmentMapBinding
import com.example.myapplication.databinding.LayoutMapBinding
import com.example.myapplication.databinding.LayoutRoutesBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MapFragment: Fragment() {
    private val viewModel: MapViewModel by viewModels()
    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!
    private lateinit var mapLayoutBinding: LayoutMapBinding
    private lateinit var routesLayoutBinding: LayoutRoutesBinding

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

        viewModel.loading.observe(viewLifecycleOwner) { isLoading ->
            routesLayoutBinding.pbFetchingTimetable.visibility = if(isLoading) View.VISIBLE else View.GONE
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
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}