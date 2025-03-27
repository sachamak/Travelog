package com.example.travelog.ui.profile

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.example.travelog.R
import com.example.travelog.data.local.AppDatabase
import com.example.travelog.data.local.entity.UserEntity
import com.example.travelog.data.repository.FirebaseRepository
import com.example.travelog.databinding.FragmentEditProfileBinding
import com.example.travelog.model.User
import com.example.travelog.ui.viewmodel.ProfileViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class EditProfileFragment : Fragment() {

    private var _binding: FragmentEditProfileBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: ProfileViewModel
    private val repository = FirebaseRepository()
    private var selectedImageUri: Uri? = null
    private var currentUser: User? = null
    private lateinit var appDatabase: AppDatabase

    private val getContent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data: Intent? = result.data
            selectedImageUri = data?.data
            selectedImageUri?.let {
                Glide.with(requireContext())
                    .load(it)
                    .circleCrop()
                    .into(binding.profileImage)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d("EditProfileFragment", "onCreateView called")
        _binding = FragmentEditProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Log.d("EditProfileFragment", "onViewCreated called")

        try {
            appDatabase = AppDatabase.getDatabase(requireContext())

            viewModel = ViewModelProvider(requireActivity())[ProfileViewModel::class.java]

            if (_binding == null) {
                Log.e("EditProfileFragment", "binding is null in onViewCreated")
                Toast.makeText(context, "Error: Unable to initialize interface", Toast.LENGTH_SHORT).show()
                findNavController().popBackStack()
                return
            }

            setupClickListeners()
            observeViewModel()

            view.post {
                if (!isAdded || _binding == null) {
                    Log.e("EditProfileFragment", "Fragment not attached or binding null before loading user")
                    return@post
                }
                loadCurrentUser()
            }
        } catch (e: Exception) {
            Log.e("EditProfileFragment", "Error during initialization", e)
            Toast.makeText(context, "Initialization error: ${e.message}", Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d("EditProfileFragment", "onResume called")
    }

    override fun onPause() {
        super.onPause()
        Log.d("EditProfileFragment", "onPause called")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("EditProfileFragment", "onDestroy called")
    }

    private fun loadCurrentUser() {
        val loadingProgress = binding.loadingProgress
        loadingProgress.visibility = View.VISIBLE
        val userId = FirebaseAuth.getInstance().currentUser?.uid

        if (userId != null) {
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val result = withContext(Dispatchers.IO) {
                        repository.getUserProfile(userId)
                    }

                    if (_binding == null) return@launch

                    if (result.isSuccess) {
                        currentUser = result.getOrNull()
                        currentUser?.let { user ->
                            binding.usernameEditText.setText(user.username)
                            binding.emailEditText.setText(user.email)

                            if (user.profileImageUrl.isNotEmpty()) {
                                if (user.profileImageUrl.startsWith("http")) {
                                    Glide.with(requireContext())
                                        .load(user.profileImageUrl)
                                        .placeholder(R.drawable.default_profile)
                                        .circleCrop()
                                        .into(binding.profileImage)
                                } else {
                                    try {
                                        val imageBytes = Base64.decode(user.profileImageUrl, Base64.DEFAULT)
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
                    } else {
                        Toast.makeText(
                            requireContext(),
                            "Error loading profile: ${result.exceptionOrNull()?.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: Exception) {
                    if (_binding != null) {
                        Toast.makeText(
                            requireContext(),
                            "Error: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } finally {
                    if (_binding != null) {
                        binding.loadingProgress.visibility = View.GONE
                    }
                }
            }
        } else {
            loadingProgress.visibility = View.GONE
            Toast.makeText(requireContext(), "User not logged in", Toast.LENGTH_SHORT).show()
            findNavController().navigate(R.id.loginFragment)
        }
    }

    private fun observeViewModel() {
        viewModel.operationStatus.observe(viewLifecycleOwner) { status ->
            binding.loadingProgress.visibility = View.GONE
            when (status) {
                is ProfileViewModel.OperationStatus.Success -> {
                    Log.d("EditProfileFragment", "ViewModel reports successful operation")
                }
                is ProfileViewModel.OperationStatus.Error -> {
                    Toast.makeText(requireContext(), status.message, Toast.LENGTH_SHORT).show()
                }
                else -> {}
            }
        }
    }

    private fun setupClickListeners() {
        if (_binding == null) return

        binding.backButton.setOnClickListener {
            try {
                findNavController().popBackStack()
            } catch (e: Exception) {
                Log.e("EditProfileFragment", "Error when returning", e)
                activity?.onBackPressed()
            }
        }

        binding.changePhotoButton.setOnClickListener {
            if (!isAdded || _binding == null) return@setOnClickListener

            try {
                val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                getContent.launch(intent)
            } catch (e: Exception) {
                Log.e("EditProfileFragment", "Error while selecting image", e)
                Toast.makeText(context, "Unable to select an image", Toast.LENGTH_SHORT).show()
            }
        }

        binding.saveButton.setOnClickListener {
            if (!isAdded || _binding == null) return@setOnClickListener

            try {
                binding.loadingProgress.visibility = View.VISIBLE

                val username = binding.usernameEditText.text.toString().trim()
                val email = binding.emailEditText.text.toString().trim()

                if (!validateInput(username, email)) {
                    binding.loadingProgress.visibility = View.GONE
                    return@setOnClickListener
                }

                val updates = HashMap<String, Any>()
                updates["username"] = username

                var base64Image: String? = null
                selectedImageUri?.let { uri ->
                    try {
                        base64Image = repository.encodeImageToBase64(requireContext(), uri)
                        base64Image?.let {
                            updates["profileImageUrl"] = it
                        }
                    } catch (e: Exception) {
                        Log.e("EditProfileFragment", "Error encoding image", e)
                        Toast.makeText(
                            requireContext(),
                            "Error processing image: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                repository.updateUserProfile(updates).addOnCompleteListener { task ->
                    if (isAdded) {
                        binding.loadingProgress.visibility = View.GONE

                        if (task.isSuccessful) {
                            val updatedUser = currentUser?.copy(
                                username = username,
                                profileImageUrl = if (selectedImageUri != null && base64Image != null) {
                                    base64Image ?: ""
                                } else {
                                    currentUser?.profileImageUrl ?: ""
                                }
                            )

                            updatedUser?.let { user ->
                                updateLocalDatabase(user)

                                val viewModelUser = com.example.travelog.ui.viewmodel.User(
                                    id = user.userId,
                                    username = user.username,
                                    email = user.email,
                                    profileImageUrl = user.profileImageUrl,
                                    createdAt = user.createdAt
                                )
                                viewModel.updateUserProfileLocal(viewModelUser)
                            }

                            Toast.makeText(requireContext(), "Profile successfully updated", Toast.LENGTH_SHORT).show()
                            Log.d("EditProfileFragment", "Profile updated successfully, navigating back")
                            findNavController().popBackStack()
                        } else {
                            val errorMessage = task.exception?.message ?: "Unknown error"
                            Toast.makeText(
                                requireContext(),
                                "Profile successfully updated: $errorMessage",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("EditProfileFragment", "Error updating profile", e)
                if (isAdded && _binding != null) {
                    binding.loadingProgress.visibility = View.GONE
                    Toast.makeText(
                        requireContext(),
                        "Error: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun updateLocalDatabase(user: User) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val userEntity = UserEntity(
                    userId = user.userId,
                    username = user.username,
                    email = user.email,
                    profileImageUrl = user.profileImageUrl,
                    createdAt = user.createdAt
                )

                appDatabase.userDao().insertUser(userEntity)

                Log.d("EditProfileFragment", "User profile updated in local database")
            } catch (e: Exception) {
                Log.e("EditProfileFragment", "Error updating local database", e)
            }
        }
    }

    private fun validateInput(username: String, email: String): Boolean {
        if (username.isEmpty()) {
            binding.usernameLayout.error = "Username cannot be empty"
            return false
        }
        if (email.isEmpty()) {
            binding.emailLayout.error = "Email cannot be empty"
            return false
        }
        binding.usernameLayout.error = null
        binding.emailLayout.error = null
        return true
    }

    private fun User.toViewModelUser(): com.example.travelog.ui.viewmodel.User {
        return com.example.travelog.ui.viewmodel.User(
            id = this.userId,
            username = this.username,
            email = this.email,
            profileImageUrl = this.profileImageUrl,
            createdAt = this.createdAt
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}