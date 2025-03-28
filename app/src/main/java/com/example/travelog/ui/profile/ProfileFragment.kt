package com.example.travelog.ui.profile

import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.travelog.R
import com.example.travelog.data.local.AppDatabase
import com.example.travelog.data.local.entity.TravelPostEntity
import com.example.travelog.data.local.entity.UserEntity
import com.example.travelog.data.repository.FirebaseRepository
import com.example.travelog.databinding.FragmentProfileBinding
import com.example.travelog.model.TravelPost
import com.example.travelog.ui.viewmodel.AuthViewModel
import com.example.travelog.ui.feed.TravelPostsAdapter
import com.example.travelog.ui.viewmodel.ProfileViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: ProfileViewModel
    private lateinit var authViewModel: AuthViewModel
    private lateinit var postAdapter: TravelPostsAdapter
    private val repository = FirebaseRepository()
    private val TAG = "ProfileFragment"
    private val firestore = FirebaseFirestore.getInstance()
    private lateinit var appDatabase: AppDatabase
    private var isOfflineMode = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[ProfileViewModel::class.java]
        authViewModel = ViewModelProvider(requireActivity())[AuthViewModel::class.java]

        appDatabase = AppDatabase.getDatabase(requireContext())

        Log.d(TAG, "ProfileFragment: onViewCreated")

        viewModel.setLoading(true)

        setupRecyclerView()
        setupClickListeners()
        setupObservers()

        try {
            loadCurrentUserFromFirebase()

            loadUserPostsFromFirebase()
        } catch (e: Exception) {
            Log.e(TAG, "Error loading from Firebase, falling back to local data", e)
            loadFromLocalDatabase()
        }

        binding.swipeRefreshLayout.setOnRefreshListener {
            isOfflineMode = false
            binding.offlineBar.visibility = View.GONE
            loadCurrentUserFromFirebase()
            loadUserPostsFromFirebase()
        }
    }

    private fun loadFromLocalDatabase() {
        isOfflineMode = true
        binding.offlineBar.visibility = View.VISIBLE

        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            findNavController().navigate(R.id.loginFragment)
            return
        }

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val userEntity = withContext(Dispatchers.IO) {
                    appDatabase.userDao().getUserById(userId).value
                }

                userEntity?.let {
                    val viewModelUser = com.example.travelog.ui.viewmodel.User(
                        id = it.userId,
                        username = it.username,
                        email = it.email,
                        profileImageUrl = it.profileImageUrl,
                        createdAt = it.createdAt
                    )

                    viewModel.updateUserProfileLocal(viewModelUser)
                    Log.d(TAG, "Loaded user profile from local database")
                } ?: run {
                    Log.e(TAG, "User not found in local database")
                }

                val postEntities = withContext(Dispatchers.IO) {
                    appDatabase.travelPostDao().getPostsByUserId(userId).value ?: listOf()
                }

                val posts = postEntities.map { entity ->
                    com.example.travelog.ui.viewmodel.Post(
                        id = entity.postId,
                        title = entity.title,
                        description = entity.description,
                        location = entity.location,
                        imageUri = entity.imageUri,
                        userId = entity.userId,
                        username = entity.username,
                        timestamp = entity.createdAt,
                        latitude = entity.latitude,
                        longitude = entity.longitude
                    )
                }

                viewModel.updateUserPostsLocal(posts)
                Log.d(TAG, "Loaded ${posts.size} posts from local database")

                if (isAdded) {
                    binding.progressBar.visibility = View.GONE
                    binding.swipeRefreshLayout.isRefreshing = false
                    viewModel.setLoading(false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading from local database", e)
                if (isAdded) {
                    Toast.makeText(
                        requireContext(),
                        "Error loading data: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    binding.progressBar.visibility = View.GONE
                    binding.swipeRefreshLayout.isRefreshing = false
                    viewModel.setLoading(false)
                }
            }
        }
    }

    private fun loadCurrentUserFromFirebase() {
        binding.progressBar.visibility = View.VISIBLE

        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            findNavController().navigate(R.id.loginFragment)
            return
        }

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val userResult = withContext(Dispatchers.IO) {
                    repository.getUserProfile(userId)
                }

                if (userResult.isSuccess) {
                    val user = userResult.getOrNull()
                    user?.let {
                        val viewModelUser = com.example.travelog.ui.viewmodel.User(
                            id = it.userId,
                            username = it.username,
                            email = it.email,
                            profileImageUrl = it.profileImageUrl,
                            createdAt = it.createdAt
                        )

                        viewModel.updateUserProfileLocal(viewModelUser)

                        withContext(Dispatchers.IO) {
                            val userEntity = UserEntity(
                                userId = it.userId,
                                username = it.username,
                                email = it.email,
                                profileImageUrl = it.profileImageUrl,
                                createdAt = it.createdAt
                            )
                            appDatabase.userDao().insertUser(userEntity)
                            Log.d(TAG, "Saved user profile to local database")
                        }
                    }
                } else {
                    Log.e(TAG, "Error loading profile: ${userResult.exceptionOrNull()?.message}")
                    Toast.makeText(
                        requireContext(),
                        "Error loading profile",
                        Toast.LENGTH_SHORT
                    ).show()

                    loadFromLocalDatabase()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception while loading profile", e)
                Toast.makeText(
                    requireContext(),
                    "Error: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()

                loadFromLocalDatabase()
            } finally {
                if (isAdded) {
                    binding.progressBar.visibility = View.GONE
                    binding.swipeRefreshLayout.isRefreshing = false
                    viewModel.setLoading(false)
                }
            }
        }
    }

    private fun loadUserPostsFromFirebase() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        try {
            viewModel.setLoading(true)
            binding.progressBar.visibility = View.VISIBLE
            Log.d(TAG, "Loading posts for user: $userId")

            firestore.collection("posts")
                .whereEqualTo("userId", userId)
                .get()
                .addOnSuccessListener { documents ->
                    if (!isAdded) return@addOnSuccessListener

                    try {
                        val userPosts = documents.documents.mapNotNull { doc ->
                            try {
                                TravelPost(
                                    postId = doc.getString("postId") ?: "",
                                    userId = doc.getString("userId") ?: "",
                                    username = doc.getString("username") ?: "",
                                    title = doc.getString("title") ?: "",
                                    description = doc.getString("description") ?: "",
                                    location = doc.getString("location") ?: "",
                                    latitude = doc.getDouble("latitude") ?: 0.0,
                                    longitude = doc.getDouble("longitude") ?: 0.0,
                                    imageUri = doc.getString("imageUri") ?: "",
                                    createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                                    updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()
                                )
                            } catch (e: Exception) {
                                Log.e(TAG, "Error converting document: ${e.message}")
                                null
                            }
                        }

                        Log.d(TAG, "Number of user posts retrieved: ${userPosts.size}")

                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                val entities = userPosts.map { post ->
                                    TravelPostEntity(
                                        postId = post.postId,
                                        userId = post.userId,
                                        username = post.username,
                                        title = post.title,
                                        description = post.description,
                                        location = post.location,
                                        latitude = post.latitude,
                                        longitude = post.longitude,
                                        imageUri = post.imageUri,
                                        createdAt = post.createdAt,
                                        updatedAt = post.updatedAt
                                    )
                                }

                                appDatabase.travelPostDao().insertPosts(entities)
                                Log.d(TAG, "Saved ${entities.size} user posts to local database")
                            } catch (e: Exception) {
                                Log.e(TAG, "Error saving posts to local database", e)
                            }
                        }

                        val posts = userPosts
                            .sortedByDescending { it.createdAt }
                            .map { travelPost ->
                                com.example.travelog.ui.viewmodel.Post(
                                    id = travelPost.postId,
                                    title = travelPost.title,
                                    description = travelPost.description,
                                    location = travelPost.location,
                                    imageUri = travelPost.imageUri,
                                    userId = travelPost.userId,
                                    timestamp = travelPost.createdAt,
                                    latitude = travelPost.latitude,
                                    longitude = travelPost.longitude,
                                    username = travelPost.username
                                )
                            }

                        if (isAdded) {
                            binding.noPostsText.visibility = if (posts.isEmpty()) View.VISIBLE else View.GONE
                            viewModel.updateUserPostsLocal(posts)
                            binding.progressBar.visibility = View.GONE
                            binding.swipeRefreshLayout.isRefreshing = false
                            viewModel.setLoading(false)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing posts", e)
                        if (isAdded) {
                            loadFromLocalDatabase()
                        }
                    }
                }
                .addOnFailureListener { e ->
                    if (!isAdded) return@addOnFailureListener

                    Log.e(TAG, "Failed to retrieve user posts", e)
                    Toast.makeText(
                        requireContext(),
                        "Error loading posts: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    loadFromLocalDatabase()
                }
        } catch (e: Exception) {
            Log.e(TAG, "Exception in loadUserPostsFromFirebase", e)
            if (isAdded) {
                Toast.makeText(
                    requireContext(),
                    "Error loading posts: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
                loadFromLocalDatabase()
            }
        }
    }

    private fun setupRecyclerView() {
        postAdapter = TravelPostsAdapter { travelPost ->
            val bundle = Bundle().apply {
                putString("postId", travelPost.postId)
            }
            findNavController().navigate(R.id.postDetailFragment, bundle)
        }
        binding.postsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = postAdapter
        }
    }

    private fun setupClickListeners() {
        binding.editProfileButton.setOnClickListener {
            Log.d(TAG, "Edit profile button clicked")
            try {
                if (repository.isUserLoggedIn()) {
                    Log.d(TAG, "Attempting to navigate to EditProfileFragment")

                    val navController = findNavController()
                    val destinationId = R.id.editProfileFragment
                    val navGraph = navController.graph
                    val destination = navGraph.findNode(destinationId)

                    if (destination != null) {
                        Log.d(TAG, "Found EditProfileFragment destination, navigating...")
                        navController.navigate(destinationId)
                        Log.d(TAG, "Navigation to EditProfileFragment requested")
                    } else {
                        Log.e(TAG, "EditProfileFragment destination not found in navigation graph")
                        Toast.makeText(requireContext(), "Destination not found in navigation graph", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(requireContext(), "You must be logged in to edit your profile", Toast.LENGTH_SHORT).show()
                    findNavController().navigate(R.id.action_global_loginFragment)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error navigating to EditProfile", e)
                Toast.makeText(
                    requireContext(),
                    "Navigation not possible: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        binding.logoutButton.setOnClickListener {
            try {
                binding.progressBar.visibility = View.VISIBLE
                viewModel.setLoading(true)

                Log.d(TAG, "Logout button clicked, attempting to logout user")

                try {
                    viewModel.updateUserPostsLocal(emptyList())
                    viewModel.updateUserProfileLocal(null)

                    FirebaseAuth.getInstance().signOut()
                    Log.d(TAG, "Firebase signOut completed successfully")

                    viewModel.setLoggedOut(false)

                    Toast.makeText(requireContext(), "Logout successful", Toast.LENGTH_SHORT).show()

                    Log.d(TAG, "Navigating directly to login screen")
                    findNavController().navigate(R.id.loginFragment, null,
                        androidx.navigation.NavOptions.Builder()
                            .setPopUpTo(R.id.nav_graph, true)
                            .build()
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error during Firebase logout", e)
                    Toast.makeText(
                        requireContext(),
                        "Logout error: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                } finally {
                    if (isAdded) {
                        binding.progressBar.visibility = View.GONE
                        viewModel.setLoading(false)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Fatal error during logout", e)
                if (isAdded) {
                    binding.progressBar.visibility = View.GONE
                    viewModel.setLoading(false)
                    Toast.makeText(
                        requireContext(),
                        "Logout error: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun setupObservers() {
        viewModel.userProfile.observe(viewLifecycleOwner) { user ->
            user?.let {
                binding.usernameText.text = it.username
                binding.emailText.text = it.email
                binding.joinDateText.text = "Member since: ${formatDate(it.createdAt)}"

                if (!it.profileImageUrl.isNullOrEmpty()) {
                    if (it.profileImageUrl.startsWith("http")) {
                        Glide.with(requireContext())
                            .load(it.profileImageUrl)
                            .placeholder(R.drawable.default_profile)
                            .error(R.drawable.default_profile)
                            .circleCrop()
                            .into(binding.profileImage)
                    } else {
                        try {
                            val imageBytes = Base64.decode(it.profileImageUrl, Base64.DEFAULT)
                            val bitmap = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                            binding.profileImage.setImageBitmap(bitmap)
                        } catch (e: Exception) {
                            binding.profileImage.setImageResource(R.drawable.default_profile)
                        }
                    }
                } else {
                    binding.profileImage.setImageResource(R.drawable.default_profile)
                }
            }
        }

        viewModel.userPosts.observe(viewLifecycleOwner) { posts ->
            binding.noPostsText.visibility = if (posts.isEmpty()) View.VISIBLE else View.GONE
            val travelPosts = posts.map { post ->
                TravelPost(
                    postId = post.id,
                    userId = post.userId,
                    username = post.username,
                    title = post.title,
                    description = post.description,
                    location = post.location,
                    latitude = post.latitude,
                    longitude = post.longitude,
                    imageUri = post.imageUri,
                    createdAt = post.timestamp,
                    updatedAt = post.timestamp
                )
            }

            postAdapter.submitList(travelPosts)
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
    }

    private fun formatDate(timestamp: Long): String {
        val date = java.util.Date(timestamp)
        return android.text.format.DateFormat.format("dd MMM yyyy", date).toString()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}