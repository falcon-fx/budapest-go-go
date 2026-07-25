package com.falconfx.gtfsviewer.ui.root

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import com.falconfx.gtfsviewer.R
import com.falconfx.gtfsviewer.databinding.FragmentRootScreenBinding
import com.falconfx.gtfsviewer.ui.map.OsmMapFragment
import com.falconfx.gtfsviewer.ui.map.TransportLinesFragment
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class RootScreenFragment : Fragment() {
    private val viewModel: RootScreenViewModel by viewModels()
    private var _binding: FragmentRootScreenBinding? = null
    private val binding get() = _binding!!
    private var currentFragment: Fragment? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRootScreenBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentRootScreenBinding.bind(view)

        binding.bottomNavBar.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_map -> viewModel.switchTab(RootScreenViewModel.Tab.MAP)
                R.id.nav_routes -> viewModel.switchTab(RootScreenViewModel.Tab.TRANSPORT_LINES)
            }
            true
        }

        viewModel.currentTab.observe(viewLifecycleOwner) { tab ->
            navigateToTab(tab)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun navigateToTab(tab: RootScreenViewModel.Tab) {
        val tag = when (tab) {
            RootScreenViewModel.Tab.MAP -> "OsmMapFragment"
            RootScreenViewModel.Tab.TRANSPORT_LINES -> "TransportLinesFragment"
        }

        val existing = childFragmentManager.findFragmentByTag(tag)
        val target: Fragment = existing ?: when (tab) {
            RootScreenViewModel.Tab.MAP -> OsmMapFragment()
            RootScreenViewModel.Tab.TRANSPORT_LINES -> TransportLinesFragment()
        }

        childFragmentManager.beginTransaction().apply {
            currentFragment?.let { current ->
                hide(current)
                setMaxLifecycle(current, Lifecycle.State.STARTED)
            }

            if (existing != null) {
                show(target)
            } else {
                add(R.id.childFragmentContainer, target, tag)
            }
            setMaxLifecycle(target, Lifecycle.State.RESUMED)

            commitNow()
        }

        currentFragment = target
    }
}
