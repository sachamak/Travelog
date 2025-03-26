package com.example.travelog.ui.post

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.bumptech.glide.Glide
import com.example.travelog.databinding.FragmentEditPostBinding
import com.example.travelog.ui.viewmodel.PostViewModel

class EditPostFragment : Fragment() {

    private var _binding: FragmentEditPostBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: PostViewModel
    private val args: EditPostFragmentArgs by navArgs()
    private var selectedImageUri: Uri? = null

    private val getContent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedImageUri = uri
                binding.postImage.setImageURI(uri)
                binding.postImage.visibility = View.VISIBLE
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEditPostBinding.inflate(inflater, container, false)
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
                binding.titleEditText.setText(it.title)
                binding.descriptionEditText.setText(it.description)
                binding.locationEditText.setText(it.location)

                if (it.imageUri.isNotEmpty()) {
                    if (it.imageUri.startsWith("http")) {
                        Glide.with(requireContext())
                            .load(it.imageUri)
                            .centerCrop()
                            .into(binding.postImage)
                        binding.postImage.visibility = View.VISIBLE
                    } else {
                        try {
                            val imageBytes = Base64.decode(it.imageUri, Base64.DEFAULT)
                            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                            binding.postImage.setImageBitmap(bitmap)
                            binding.postImage.visibility = View.VISIBLE
                        } catch (e: Exception) {
                            binding.postImage.visibility = View.GONE
                        }
                    }
                } else {
                    binding.postImage.visibility = View.GONE
                }
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.saveButton.isEnabled = !isLoading
        }
    }

    private fun setupClickListeners() {
        binding.backButton.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.selectImageButton.setOnClickListener {
            openImagePicker()
        }

        binding.saveButton.setOnClickListener {
            saveChanges()
        }
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "image/*"
        getContent.launch(intent)
    }

    private fun saveChanges() {
        val title = binding.titleEditText.text.toString().trim()
        val description = binding.descriptionEditText.text.toString().trim()
        val location = binding.locationEditText.text.toString().trim()

        if (title.isEmpty() || description.isEmpty()) {
            Toast.makeText(requireContext(), "Title and description are required", Toast.LENGTH_SHORT).show()
            return
        }

        viewModel.updatePost(
            postId = args.postId,
            title = title,
            description = description,
            location = location,
            imageUri = selectedImageUri
        )

        viewModel.operationSuccessful.observe(viewLifecycleOwner) { success ->
            if (success) {
                Toast.makeText(requireContext(), "Post updated successfully", Toast.LENGTH_SHORT).show()
                findNavController().popBackStack()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}