package com.example.travelog.ui.post

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.example.travelog.R
import com.example.travelog.databinding.FragmentCreatePostBinding
import com.example.travelog.ui.viewmodel.PostViewModel
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth

class CreatePostFragment : Fragment() {

    private var _binding: FragmentCreatePostBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: PostViewModel
    private var selectedImageUri: Uri? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentLatitude: Double = 0.0
    private var currentLongitude: Double = 0.0

    private val getContent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedImageUri = uri
                binding.postImage.setImageURI(uri)
                binding.postImage.visibility = View.VISIBLE
            }
        }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val locationGranted = permissions.entries.all { it.value }
            if (locationGranted) {
                getCurrentLocation()
            } else {
                Toast.makeText(requireContext(), R.string.location_permission_required, Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCreatePostBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(
            requireActivity(),
            PostViewModel.Factory(requireActivity().application)
        )[PostViewModel::class.java]
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        // Check if user is signed in
        checkUserAuthentication()

        setupClickListeners()
        setupObservers()
    }

    private fun checkUserAuthentication() {
        if (FirebaseAuth.getInstance().currentUser == null) {
            Toast.makeText(requireContext(), "Please sign in to create a post", Toast.LENGTH_LONG).show()
            findNavController().navigate(R.id.action_global_loginFragment)
        }
    }

    private fun setupClickListeners() {
        binding.backButton.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.selectImageButton.setOnClickListener {
            openImagePicker()
        }

        binding.publishButton.setOnClickListener {
            if (validateInputs()) {
                createPost()
            }
        }

        binding.getLocationButton.setOnClickListener {
            checkLocationPermissions()
        }
    }

    private fun setupObservers() {
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.publishButton.isEnabled = !isLoading
            binding.selectImageButton.isEnabled = !isLoading
        }

        viewModel.operationSuccessful.observe(viewLifecycleOwner) { success ->
            if (success) {
                Toast.makeText(requireContext(), "Post created successfully", Toast.LENGTH_SHORT).show()
            }
        }

        viewModel.navigateBack.observe(viewLifecycleOwner) { shouldNavigate ->
            if (shouldNavigate) {
                try {
                    findNavController().navigate(R.id.action_createPostFragment_to_feedFragment)
                } catch (e: Exception) {
                    Log.e("CreatePostFragment", "Navigation error: ${e.message}")
                    findNavController().popBackStack()
                }
            }
        }

        viewModel.error.observe(viewLifecycleOwner) { errorMessage ->
            if (!errorMessage.isNullOrEmpty()) {
                Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun validateInputs(): Boolean {
        val title = binding.titleEditText.text.toString().trim()
        val description = binding.descriptionEditText.text.toString().trim()

        when {
            title.isEmpty() -> {
                binding.titleLayout.error = "Title is required"
                return false
            }
            description.isEmpty() -> {
                binding.descriptionLayout.error = "Description is required"
                return false
            }
            else -> {
                binding.titleLayout.error = null
                binding.descriptionLayout.error = null
                return true
            }
        }
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/jpeg", "image/png"))
        }
        getContent.launch(intent)
    }

    private fun checkLocationPermissions() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                getCurrentLocation()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                Toast.makeText(requireContext(), R.string.location_permission_required, Toast.LENGTH_SHORT).show()
                requestLocationPermissions()
            }
            else -> {
                requestLocationPermissions()
            }
        }
    }

    private fun requestLocationPermissions() {
        requestPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun checkGooglePlayServices(): Boolean {
        val googleApiAvailability = GoogleApiAvailability.getInstance()
        val resultCode = googleApiAvailability.isGooglePlayServicesAvailable(requireContext())
        return if (resultCode != ConnectionResult.SUCCESS) {
            if (googleApiAvailability.isUserResolvableError(resultCode)) {
                googleApiAvailability.getErrorDialog(requireActivity(), resultCode, 2404)?.show()
            } else {
                Log.e("CreatePostFragment", "This device is not supported.")
                Toast.makeText(requireContext(), "Google Play Services not available", Toast.LENGTH_SHORT).show()
            }
            false
        } else {
            true
        }
    }

    private fun getCurrentLocation() {
        if (checkGooglePlayServices()) {
            try {
                binding.locationProgressBar.visibility = View.VISIBLE
                val locationTask = fusedLocationClient.lastLocation
                locationTask.addOnSuccessListener { location ->
                    binding.locationProgressBar.visibility = View.GONE
                    if (location != null) {
                        currentLatitude = location.latitude
                        currentLongitude = location.longitude

                        val locationText = "Lat: ${String.format("%.4f", currentLatitude)}, Long: ${String.format("%.4f", currentLongitude)}"
                        binding.locationEditText.setText(locationText)

                        Toast.makeText(requireContext(), "Location updated", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "Unable to get location. Please try again.", Toast.LENGTH_SHORT).show()
                    }
                }.addOnFailureListener {
                    binding.locationProgressBar.visibility = View.GONE
                    Toast.makeText(requireContext(), "Failed to get location: ${it.message}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: SecurityException) {
                binding.locationProgressBar.visibility = View.GONE
                Toast.makeText(requireContext(), R.string.location_permission_required, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun createPost() {
        val title = binding.titleEditText.text.toString().trim()
        val description = binding.descriptionEditText.text.toString().trim()
        val location = binding.locationEditText.text.toString().trim()

        viewModel.createPost(
            title = title,
            description = description,
            location = location,
            latitude = currentLatitude,
            longitude = currentLongitude,
            imageUri = selectedImageUri
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}