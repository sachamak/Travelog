package com.example.travelog.ui.map

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
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
import com.example.travelog.databinding.FragmentMapBinding
import com.example.travelog.model.TravelPost
import com.example.travelog.ui.viewmodel.PostViewModel
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions

class MapFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!

    private lateinit var postViewModel: PostViewModel
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var googleMap: GoogleMap? = null

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
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        postViewModel = ViewModelProvider(
            requireActivity(),
            PostViewModel.Factory(requireActivity().application)
        )[PostViewModel::class.java]
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        binding.currentLocationFab.setOnClickListener {
            checkLocationPermissions()
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map

        // Observe posts and add markers
        postViewModel.getAllPosts().observe(viewLifecycleOwner) { posts ->
            updateMapMarkers(posts)
        }

        // Set up info window click listener to navigate to post detail
        googleMap?.setOnInfoWindowClickListener { marker ->
            val postId = marker.tag as? String
            if (postId != null) {
                navigateToPostDetail(postId)
            }
        }
    }

    private fun updateMapMarkers(posts: List<TravelPost>) {
        googleMap?.clear()

        posts.forEach { post ->
            if (post.latitude != 0.0 && post.longitude != 0.0) {
                val position = LatLng(post.latitude, post.longitude)
                val marker = googleMap?.addMarker(
                    MarkerOptions()
                        .position(position)
                        .title(post.title)
                        .snippet(post.username)
                )
                marker?.tag = post.postId
            }
        }
    }

    private fun navigateToPostDetail(postId: String) {
        val action = MapFragmentDirections.actionMapFragmentToPostDetailFragment(postId)
        findNavController().navigate(action)
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

    private fun getCurrentLocation() {
        try {
            val locationTask = fusedLocationClient.lastLocation
            locationTask.addOnSuccessListener { location ->
                if (location != null) {
                    val currentLatLng = LatLng(location.latitude, location.longitude)
                    googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 12f))
                }
            }
        } catch (e: SecurityException) {
            Toast.makeText(requireContext(), R.string.location_permission_required, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 