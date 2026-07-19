package com.example.myapplication.ui.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.myapplication.R
import com.example.myapplication.databinding.FragmentAuthBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AuthFragment: Fragment() {
    private var _binding: FragmentAuthBinding? = null
    private val binding get() = _binding!!

    private val authViewModel: AuthViewModel by viewModels()

    companion object {
        private const val REQUEST_CODE_PICK_FILE = 1001
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAuthBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Auto-skip if certs already exist
        if (authViewModel.hasCertificates()) {
            findNavController().navigate(R.id.action_nav_auth_to_nav_map)
            return
        }

        // Update cert status text
        updateCertStatus()

        // API key button
        binding.buttonEnterApi.setOnClickListener {
            authViewModel.apiKey.value = binding.editTextApiKey.text.toString()
            authViewModel.saveApiKey()
        }

        // Certificate import button
        binding.buttonImportCerts.setOnClickListener {
            openFilePicker()
        }

        // Observe navigation to map
        authViewModel.proceedToMap.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let {
                Log.i(authViewModel.logTag, "Navigating to MapFragment")
                findNavController().navigate(R.id.action_nav_auth_to_nav_map)
            }
        }

        // Observe certificate import success
        authViewModel.certImportSuccess.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let {
                Toast.makeText(requireContext(), "Certificates imported successfully", Toast.LENGTH_SHORT).show()
                updateCertStatus()
            }
        }

        // Observe restart required
        authViewModel.requireRestart.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let {
                AlertDialog.Builder(requireContext())
                    .setTitle("Restart Required")
                    .setMessage("Please restart the app to apply the new certificates.")
                    .setPositiveButton("Restart Now") { _, _ ->
                        requireActivity().recreate()
                    }
                    .setCancelable(false)
                    .show()
            }
        }

        // Observe certificate import errors
        authViewModel.certImportError.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { message ->
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.cert_error_title)
                    .setMessage(message)
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    private fun updateCertStatus() {
        if (authViewModel.hasCertificates()) {
            val certCount = authViewModel.getCertificateCount()
            binding.tViewCertStatus.text = getString(R.string.cert_status_ok, certCount)
        } else {
            binding.tViewCertStatus.text = getString(R.string.cert_status_missing)
        }
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "application/zip",
                "application/x-zip-compressed",
                "application/x-x509-ca-cert",
                "application/x-pem-file",
                "text/plain",
                "application/octet-stream"
            ))
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(Intent.createChooser(intent, "Select certificate ZIP or PEM"), REQUEST_CODE_PICK_FILE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_PICK_FILE && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                try {
                    requireContext().contentResolver.openInputStream(uri)?.use { stream ->
                        val fileBytes = stream.readBytes()
                        authViewModel.importCertificates(fileBytes)
                    }
                } catch (e: Exception) {
                    AlertDialog.Builder(requireContext())
                        .setTitle("Error")
                        .setMessage("Failed to read file: ${e.message}")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}