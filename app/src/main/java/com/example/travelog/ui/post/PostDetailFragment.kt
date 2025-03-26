package com.example.travelog.ui.post

import android.graphics.BitmapFactory
import android.os.Bundle
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

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPostDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
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
                binding.postDescription.text = it.description
                binding.postLocation.text = it.location
                binding.postDate.text = it.formattedDate

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
                            binding.postImage.setImageResource(R.drawable.placeholder_image)
                        }
                    }
                } else {
                    binding.postImage.setImageResource(R.drawable.placeholder_image)
                }

                val isCurrentUserPost = viewModel.isCurrentUserPost(it.userId)
                binding.editButton.visibility = if (isCurrentUserPost) View.VISIBLE else View.GONE
                binding.deleteButton.visibility = if (isCurrentUserPost) View.VISIBLE else View.GONE
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