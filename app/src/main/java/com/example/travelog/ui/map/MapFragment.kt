package com.example.travelog.ui.map

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.lifecycle.lifecycleScope
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
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MapFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!
    private val TAG = "MapFragment"

    private lateinit var postViewModel: PostViewModel
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var googleMap: GoogleMap? = null
    private var allPosts: List<TravelPost> = emptyList()
    private var showOnlyUserPosts = false
    private val markers = mutableListOf<Marker>()

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
        Log.d(TAG, "MapFragment: onViewCreated")

        postViewModel = ViewModelProvider(
            requireActivity(),
            PostViewModel.Factory(requireActivity().application)
        )[PostViewModel::class.java]
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        val focusOnPost = arguments?.getBoolean("focusOnPost", false) ?: false
        val focusLatitude = arguments?.getDouble("latitude", 0.0) ?: 0.0
        val focusLongitude = arguments?.getDouble("longitude", 0.0) ?: 0.0
        val focusPostId = arguments?.getString("postId")

        if (focusOnPost && focusLatitude != 0.0 && focusLongitude != 0.0) {
            Log.d(TAG, "Will focus on post location: lat=$focusLatitude, long=$focusLongitude, postId=$focusPostId")
        }

        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync { map ->
            googleMap = map
            onMapReady(map)

            if (focusOnPost && focusLatitude != 0.0 && focusLongitude != 0.0) {
                focusOnPostLocation(focusLatitude, focusLongitude, focusPostId)
            }
        }

        // Initialize the filter button with the correct icon
        binding.filterFab.setImageResource(R.drawable.ic_public)
        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.currentLocationFab.setOnClickListener {
            checkLocationPermissions()
        }

        // Add long press to zoom to show all markers
        binding.currentLocationFab.setOnLongClickListener {
            zoomToShowAllMarkers()
            return@setOnLongClickListener true
        }

        binding.filterFab.setOnClickListener {
            showOnlyUserPosts = !showOnlyUserPosts
            updateFilterButton()
            updateMapMarkersWithFilter()
        }
    }

    private fun updateFilterButton() {
        if (showOnlyUserPosts) {
            binding.filterFab.setImageResource(R.drawable.ic_person)
            Toast.makeText(context, "Showing only your posts", Toast.LENGTH_SHORT).show()
        } else {
            binding.filterFab.setImageResource(R.drawable.ic_public)
            Toast.makeText(context, "Showing all posts", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        Log.d(TAG, "Map is ready")

        binding.loadingIndicator.visibility = View.VISIBLE

        try {
            googleMap?.apply {
                uiSettings.isZoomControlsEnabled = true
                uiSettings.isCompassEnabled = true
                uiSettings.isMapToolbarEnabled = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error customizing map: ${e.message}")
        }

        postViewModel.getAllPosts().observe(viewLifecycleOwner) { posts ->
            binding.loadingIndicator.visibility = View.GONE

            if (posts.isEmpty()) {
                Toast.makeText(context, "No posts with location data found", Toast.LENGTH_SHORT).show()
            } else {
                Log.d(TAG, "Received ${posts.size} posts")
                allPosts = posts
                updateMapMarkersWithFilter()
            }
        }

        googleMap?.setOnMarkerClickListener { marker ->
            val postId = marker.tag as? String
            if (postId != null) {
                Log.d(TAG, "Marker clicked, navigating to post detail: $postId")
                navigateToPostDetail(postId)
                return@setOnMarkerClickListener true
            }
            false
        }

        googleMap?.setOnInfoWindowClickListener { marker ->
            val postId = marker.tag as? String
            if (postId != null) {
                navigateToPostDetail(postId)
            }
        }

        googleMap?.setInfoWindowAdapter(object : GoogleMap.InfoWindowAdapter {
            override fun getInfoWindow(marker: Marker): View? {
                return null
            }

            override fun getInfoContents(marker: Marker): View? {
                return layoutInflater.inflate(R.layout.custom_marker_info_window, null).apply {
                    findViewById<android.widget.TextView>(R.id.title).text = marker.title
                    findViewById<android.widget.TextView>(R.id.snippet).text = "Created by: ${marker.snippet}"
                }
            }
        })
    }

    private fun updateMapMarkersWithFilter() {
        googleMap?.clear()

        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        val postsToShow = if (showOnlyUserPosts && currentUserId != null) {
            allPosts.filter { it.userId == currentUserId }
        } else {
            allPosts
        }

        if (postsToShow.isEmpty()) {
            Toast.makeText(context, "No posts to display with current filter", Toast.LENGTH_SHORT).show()
        }

        updateMapMarkers(postsToShow)
    }

    private fun updateMapMarkers(posts: List<TravelPost>) {
        var validMarkerCount = 0

        markers.clear()
        googleMap?.clear()

        posts.forEach { post ->
            if (post.latitude != 0.0 && post.longitude != 0.0) {
                val position = LatLng(post.latitude, post.longitude)
                val isCurrentUserPost = post.userId == FirebaseAuth.getInstance().currentUser?.uid

                val markerOptions = MarkerOptions()
                    .position(position)
                    .title(post.title)
                    .snippet(post.username)

                if (isCurrentUserPost) {
                    markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                } else {
                    markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                }

                val marker = googleMap?.addMarker(markerOptions)
                marker?.tag = post.postId

                if (marker != null) {
                    markers.add(marker)
                }

                validMarkerCount++
            }
        }

        if (validMarkerCount > 0) {
            zoomToShowAllMarkers()
        } else {
            Toast.makeText(
                context,
                "No posts with valid location data found",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun zoomToShowAllMarkers() {
        val builder = LatLngBounds.Builder()
        var hasValidMarkers = false

        allPosts.forEach { post ->
            if (post.latitude != 0.0 && post.longitude != 0.0) {
                if (showOnlyUserPosts) {
                    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
                    if (post.userId == currentUserId) {
                        builder.include(LatLng(post.latitude, post.longitude))
                        hasValidMarkers = true
                    }
                } else {
                    builder.include(LatLng(post.latitude, post.longitude))
                    hasValidMarkers = true
                }
            }
        }

        if (hasValidMarkers) {
            try {
                val padding = 100
                val bounds = builder.build()
                val cameraUpdate = CameraUpdateFactory.newLatLngBounds(bounds, padding)
                googleMap?.animateCamera(cameraUpdate)
            } catch (e: Exception) {
                Log.e(TAG, "Error zooming to markers: ${e.message}")
                val defaultLocation = LatLng(0.0, 0.0)
                googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 2f))
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
            googleMap?.isMyLocationEnabled = true

            val locationTask = fusedLocationClient.lastLocation
            locationTask.addOnSuccessListener { location ->
                if (location != null) {
                    val currentLatLng = LatLng(location.latitude, location.longitude)
                    googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 14f))
                } else {
                    Toast.makeText(context, "Could not get current location", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Error getting current location: ${e.message}")
            Toast.makeText(requireContext(), R.string.location_permission_required, Toast.LENGTH_SHORT).show()
        }
    }

    private fun focusOnPostLocation(latitude: Double, longitude: Double, postId: String?) {
        val postLocation = LatLng(latitude, longitude)

        googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(postLocation, 15f))

        if (postId != null) {
            lifecycleScope.launch {
                delay(500)

                markers.forEach { marker ->
                    if (marker.tag == postId) {
                        marker.showInfoWindow()
                        return@forEach
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 