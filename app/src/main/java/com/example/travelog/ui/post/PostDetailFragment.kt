package com.example.travelog.ui.post

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.bumptech.glide.Glide
import com.example.travelog.R
import com.example.travelog.databinding.FragmentPostDetailBinding
import com.example.travelog.ui.viewmodel.PostViewModel
import android.util.Base64

class PostDetailFragment : Fragment() {

    private var _binding: FragmentPostDetailBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: PostViewModel
    private val args: PostDetailFragmentArgs by navArgs()
    private val TAG = "PostDetailFragment"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPostDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "PostDetailFragment: onViewCreated for postId: ${args.postId}")

        viewModel = ViewModelProvider(
            requireActivity(),
            PostViewModel.Factory(requireActivity().application)
        )[PostViewModel::class.java]

        setupObservers()
        setupClickListeners()

        viewModel.loadPostDetails(args.postId)
    }

    private fun setupObservers() {
        viewModel.currentPost.observe(viewLifecycleOwner) { post ->
            post?.let {
                binding.postTitle.text = it.title
                Log.d(TAG, "Post username: ${it.username}")
                binding.authorUsername.text = it.username ?: "Anonymous"
                binding.postLocation.text = it.location
                binding.postDate.text = it.formattedDate
                binding.postDescription.text = it.description

                if (it.latitude != 0.0 || it.longitude != 0.0) {
                    val formattedCoordinates = String.format("Lat: %.6f, Long: %.6f", it.latitude, it.longitude)
                    binding.postCoordinates.text = formattedCoordinates
                    binding.viewOnMapButton.isEnabled = true
                } else {
                    binding.postCoordinates.text = "Not available"
                    binding.viewOnMapButton.isEnabled = false
                }

                if (it.imageUri.isNotEmpty()) {
                    if (it.imageUri.startsWith("http")) {
                        Glide.with(requireContext())
                            .load(it.imageUri)
                            .placeholder(R.drawable.placeholder_image)
                            .centerCrop()
                            .into(binding.postImage)
                    } else {
                        try {
                            val imageBytes = Base64.decode(it.imageUri, Base64.DEFAULT)
                            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                            binding.postImage.setImageBitmap(bitmap)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error loading image: ${e.message}")
                            binding.postImage.setImageResource(R.drawable.placeholder_image)
                        }
                    }
                } else {
                    binding.postImage.setImageResource(R.drawable.placeholder_image)
                }

                val isCurrentUserPost = viewModel.isCurrentUserPost(it.userId)
                binding.editButton.visibility = if (isCurrentUserPost) View.VISIBLE else View.GONE
                binding.deleteButton.visibility = if (isCurrentUserPost) View.VISIBLE else View.GONE
                Log.d(TAG, "Post details: id=${it.id}, title=${it.title}, username=${it.username}, userId=${it.userId}")
                binding.authorLabel.visibility = View.VISIBLE
                binding.authorUsername.visibility = View.VISIBLE
            } ?: run {
                Log.e(TAG, "Failed to load post - null post data")
                Toast.makeText(context, "Failed to load post details", Toast.LENGTH_SHORT).show()
                findNavController().popBackStack()
            }
        }
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
        }
        viewModel.errorMessage.observe(viewLifecycleOwner) { errorMessage ->
            if (!errorMessage.isNullOrEmpty()) {
                Log.e(TAG, "Error: $errorMessage")
                Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupClickListeners() {
        binding.editButton.setOnClickListener {
            val action = PostDetailFragmentDirections.actionPostDetailFragmentToEditPostFragment(args.postId)
            findNavController().navigate(action)
        }

        binding.deleteButton.setOnClickListener {
            showDeleteConfirmation()
        }

        binding.backButton.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.viewOnMapButton.setOnClickListener {
            try {
                val post = viewModel.currentPost.value
                if (post != null && (post.latitude != 0.0 || post.longitude != 0.0)) {
                    Log.d(TAG, "Navigating to map for post location: lat=${post.latitude}, long=${post.longitude}")

                    val bundle = Bundle().apply {
                        putString("postId", post.id)
                        putDouble("latitude", post.latitude)
                        putDouble("longitude", post.longitude)
                        putString("title", post.title)
                        putBoolean("focusOnPost", true)
                    }

                    findNavController().navigate(R.id.mapFragment, bundle)

                    Toast.makeText(context, "Showing post location on map", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "No location data available for this post", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error navigating to map: ${e.message}")
                Toast.makeText(context, "Unable to open map view", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showDeleteConfirmation() {
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Delete Post")
            .setMessage("Are you sure you want to delete this post?")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deletePost(args.postId)
                Toast.makeText(requireContext(), "Post deleted", Toast.LENGTH_SHORT).show()
                findNavController().popBackStack()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}