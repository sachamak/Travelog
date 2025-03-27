package com.example.travelog.ui.viewmodel
import kotlinx.coroutines.delay
import android.net.Uri
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.AndroidViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID
import com.example.travelog.model.TravelPost
import android.graphics.Bitmap
import android.provider.MediaStore
import android.util.Base64
import android.app.Application
import java.io.ByteArrayOutputStream
import androidx.lifecycle.ViewModelProvider
import com.google.firebase.firestore.ListenerRegistration
import com.example.travelog.data.repository.FirebaseRepository

data class Post(
    val id: String,
    val title: String,
    val description: String,
    val location: String,
    val imageUri: String,
    val userId: String,
    val username: String = "Anonymous",
    val timestamp: Long,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
) {
    val formattedDate: String
        get() {
            val date = java.util.Date(timestamp)
            return android.text.format.DateFormat.format("MMM dd, yyyy", date).toString()
        }
}


class PostViewModel(application: Application) : AndroidViewModel(application) {
    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage

    private val _operationSuccessful = MutableLiveData<Boolean>()
    val operationSuccessful: LiveData<Boolean> = _operationSuccessful

    private val _currentPost = MutableLiveData<Post?>()
    val currentPost: LiveData<Post?> = _currentPost

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _posts = MutableLiveData<List<Post>>()
    val posts: LiveData<List<Post>> = _posts

    private val _navigateBack = MutableLiveData<Boolean>()
    val navigateBack: LiveData<Boolean> = _navigateBack

    private var postsListenerRegistration: ListenerRegistration? = null

    fun loadPostDetails(postId: String) {
        _isLoading.value = true

        viewModelScope.launch {
            try {
                val document = withContext(Dispatchers.IO) {
                    FirebaseFirestore.getInstance().collection("posts")
                        .document(postId)
                        .get()
                        .await()
                }

                if (document.exists()) {
                    try {

                        val docPostId = document.getString("postId") ?: ""
                        val title = document.getString("title") ?: ""
                        val description = document.getString("description") ?: ""
                        val location = document.getString("location") ?: ""
                        val imageUri = document.getString("imageUri") ?: ""
                        val userId = document.getString("userId") ?: ""
                        val createdAt = document.getLong("createdAt") ?: System.currentTimeMillis()
                        val latitude = document.getDouble("latitude") ?: 0.0
                        val longitude = document.getDouble("longitude") ?: 0.0

                        val post = Post(
                            id = docPostId,
                            title = title,
                            description = description,
                            location = location,
                            imageUri = imageUri,
                            userId = userId,
                            username = document.getString("username") ?: "Anonymous",
                            timestamp = createdAt,
                            latitude = latitude,
                            longitude = longitude
                        )

                        _currentPost.value = post
                    } catch (e: Exception) {
                        _errorMessage.value = "Error parsing post data: ${e.message}"
                    }
                } else {
                    _errorMessage.value = "Post not found"
                }
                _isLoading.value = false
            } catch (exception: Exception) {
                _errorMessage.value = "Error loading post: ${exception.message}"
                _isLoading.value = false
            }
        }
    }

    fun updatePost(postId: String, title: String, description: String, location: String, imageUri: Uri?) {
        _isLoading.value = true

        viewModelScope.launch {
            try {
                val currentPost = _currentPost.value ?: return@launch

                var base64Image = currentPost.imageUri

                if (imageUri != null) {
                    try {
                        base64Image = withContext(Dispatchers.IO) {
                            val bitmap = MediaStore.Images.Media.getBitmap(getApplication<Application>().contentResolver, imageUri)
                            val compressedBitmap = compressImage(bitmap)
                            bitmapToBase64(compressedBitmap)
                        }
                    } catch (e: Exception) {
                        _errorMessage.value = "Failed to process image: ${e.message}"
                        _isLoading.value = false
                        return@launch
                    }
                }

                val document = withContext(Dispatchers.IO) {
                    FirebaseFirestore.getInstance()
                        .collection("posts")
                        .document(postId)
                        .get()
                        .await()
                }

                if (!document.exists()) {
                    _errorMessage.value = "Post not found in database"
                    _isLoading.value = false
                    return@launch
                }

                val username = document.getString("username") ?: "Anonymous"

                val updatedPostMap = hashMapOf(
                    "postId" to postId,
                    "title" to title,
                    "description" to description,
                    "location" to location,
                    "imageUri" to base64Image,
                    "userId" to currentPost.userId,
                    "username" to username,
                    "createdAt" to (document.getLong("createdAt") ?: currentPost.timestamp),
                    "updatedAt" to System.currentTimeMillis(),
                    "latitude" to currentPost.latitude,
                    "longitude" to currentPost.longitude
                )

                withContext(Dispatchers.IO) {
                    FirebaseFirestore.getInstance()
                        .collection("posts")
                        .document(postId)
                        .set(updatedPostMap)
                        .await()
                }

                val updatedPost = Post(
                    id = postId,
                    title = title,
                    description = description,
                    location = location,
                    imageUri = base64Image,
                    userId = currentPost.userId,
                    username = username,
                    timestamp = currentPost.timestamp,
                    latitude = currentPost.latitude,
                    longitude = currentPost.longitude
                )

                _currentPost.value = updatedPost

                refreshPosts()

                _operationSuccessful.value = true
            } catch (e: Exception) {
                Log.e("PostViewModel", "Error updating post", e)
                _errorMessage.value = "Error updating post: ${e.message}"
                _operationSuccessful.value = false
            } finally {
                _isLoading.value = false
                viewModelScope.launch {
                    delay(100)
                    _operationSuccessful.value = false
                }
            }
        }
    }

    fun isCurrentUserPost(postUserId: String): Boolean {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        return currentUserId == postUserId
    }

    fun deletePost(postId: String) {
        viewModelScope.launch {
            try {
                val postRef = FirebaseFirestore.getInstance().collection("posts").document(postId)
                postRef.delete().await()

                _currentPost.value = null
            } catch (e: Exception) {
                Log.e("PostViewModel", "Error deleting post", e)
            }
        }
    }

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error

    fun createPost(title: String, description: String, location: String, imageUri: Uri?, latitude: Double, longitude: Double) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: "anonymous"
        val timestamp = System.currentTimeMillis()
        val newPostId = UUID.randomUUID().toString()

        _isLoading.value = true

        viewModelScope.launch {
            try {

                var base64Image = ""
                if (imageUri != null) {
                    try {
                        base64Image = withContext(Dispatchers.IO) {
                            Log.d("PostViewModel", "Starting image processing")
                            val bitmap = MediaStore.Images.Media.getBitmap(getApplication<Application>().contentResolver, imageUri)
                            Log.d("PostViewModel", "Original bitmap dimensions: ${bitmap.width}x${bitmap.height}, size ~${bitmap.byteCount / 1024} KB")

                            var compressedBitmap = compressImage(bitmap)
                            var base64String = bitmapToBase64(compressedBitmap, 60)

                            if (base64String.length > 500 * 1024) {
                                Log.d("PostViewModel", "Image still large (${base64String.length / 1024} KB), compressing more")
                                compressedBitmap = reduceImageQuality(compressedBitmap, 600)
                                base64String = bitmapToBase64(compressedBitmap, 50)
                            }

                            if (base64String.length > 300 * 1024) {
                                Log.d("PostViewModel", "Image still large (${base64String.length / 1024} KB), final compression")
                                compressedBitmap = reduceImageQuality(compressedBitmap, 400)
                                base64String = bitmapToBase64(compressedBitmap, 40)
                            }

                            Log.d("PostViewModel", "Final image size: ~${base64String.length / 1024} KB")
                            base64String
                        }
                    } catch (e: Exception) {
                        Log.e("PostViewModel", "Failed to process image", e)
                        _errorMessage.value = "Failed to process image: ${e.message}"
                        _isLoading.value = false
                        return@launch
                    }
                }

                var username = "Anonymous"
                try {
                    val user = FirebaseAuth.getInstance().currentUser
                    if (user != null) {
                        val userDoc = withContext(Dispatchers.IO) {
                            FirebaseFirestore.getInstance()
                                .collection("users")
                                .document(user.uid)
                                .get()
                                .await()
                        }

                        if (userDoc.exists()) {
                            username = userDoc.getString("username") ?: user.displayName ?: "Anonymous"
                        } else {
                            username = user.displayName ?: user.email?.substringBefore('@') ?: "Anonymous"
                        }
                    }
                } catch (e: Exception) {
                    Log.e("PostViewModel", "Error getting username: ${e.message}")
                }

                val travelPost = TravelPost(
                    postId = newPostId,
                    userId = userId,
                    username = username,
                    title = title,
                    description = description,
                    location = location,
                    latitude = latitude,
                    longitude = longitude,
                    imageUri = base64Image,
                    createdAt = timestamp,
                    updatedAt = timestamp
                )

                Log.d("PostViewModel", "Creating post with Repository: $newPostId")
                val repository = FirebaseRepository()
                val result = withContext(Dispatchers.IO) {
                    repository.createPost(travelPost)
                }

                if (result.isSuccess) {
                    Log.d("PostViewModel", "Post created successfully: $newPostId")

                    val post = Post(
                        id = newPostId,
                        title = title,
                        description = description,
                        location = location,
                        imageUri = base64Image,
                        userId = userId,
                        username = username,
                        timestamp = timestamp,
                        latitude = latitude,
                        longitude = longitude
                    )

                    _currentPost.value = post
                    _isLoading.value = false
                    _navigateBack.value = true
                    refreshPosts()
                } else {
                    Log.e("PostViewModel", "Failed to create post: ${result.exceptionOrNull()?.message}")
                    _errorMessage.value = "Failed to create post: ${result.exceptionOrNull()?.message}"
                    _isLoading.value = false
                }
            } catch (exception: Exception) {
                Log.e("PostViewModel", "Unknown error creating post", exception)
                _errorMessage.value = "Unknown error creating post: ${exception.message}"
                _isLoading.value = false
            }
        }
    }

    private fun reduceImageQuality(bitmap: Bitmap, maxDimension: Int = 600): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        val scaleFactor = when {
            width > height && width > maxDimension -> maxDimension.toFloat() / width
            height > width && height > maxDimension -> maxDimension.toFloat() / height
            else -> 0.8f
        }

        val newWidth = (width * scaleFactor).toInt()
        val newHeight = (height * scaleFactor).toInt()

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun compressImage(bitmap: Bitmap): Bitmap {
        val maxDimension = 800
        val width = bitmap.width
        val height = bitmap.height

        val scaleFactor = when {
            width > height && width > maxDimension -> maxDimension.toFloat() / width
            height > width && height > maxDimension -> maxDimension.toFloat() / height
            else -> 1.0f
        }

        val newWidth = (width * scaleFactor).toInt()
        val newHeight = (height * scaleFactor).toInt()

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun bitmapToBase64(bitmap: Bitmap, quality: Int = 50): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.DEFAULT)
    }

    fun getAllPosts(): LiveData<List<TravelPost>> {
        val postsLiveData = MutableLiveData<List<TravelPost>>()

        postsListenerRegistration?.remove()

        postsListenerRegistration = FirebaseFirestore.getInstance().collection("posts")
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, exception ->
                if (exception != null) {
                    Log.e("PostViewModel", "Error getting posts", exception)
                    return@addSnapshotListener
                }

                val postsList = snapshot?.documents?.mapNotNull { doc ->
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
                        Log.e("PostViewModel", "Error parsing post", e)
                        null
                    }
                } ?: emptyList()

                postsLiveData.value = postsList
            }

        return postsLiveData
    }

    fun refreshPosts() {
        _isLoading.value = true

        viewModelScope.launch {
            try {
                // Récupérer les posts depuis Firestore sur un thread d'I/O, triés par createdAt
                val result = withContext(Dispatchers.IO) {
                    FirebaseFirestore.getInstance().collection("posts")
                        .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                        .get()
                        .await()
                }

                val postList = mutableListOf<Post>()
                for (document in result) {
                    try {
                        // Récupérer les champs du document avec les noms corrects
                        val postId = document.getString("postId") ?: ""
                        val title = document.getString("title") ?: ""
                        val description = document.getString("description") ?: ""
                        val location = document.getString("location") ?: ""
                        val imageUri = document.getString("imageUri") ?: ""
                        val userId = document.getString("userId") ?: ""
                        val createdAt = document.getLong("createdAt") ?: System.currentTimeMillis()
                        val latitude = document.getDouble("latitude") ?: 0.0
                        val longitude = document.getDouble("longitude") ?: 0.0

                        val post = Post(
                            id = postId,
                            title = title,
                            description = description,
                            location = location,
                            imageUri = imageUri,
                            userId = userId,
                            username = document.getString("username") ?: "Anonymous",
                            timestamp = createdAt, // Utiliser createdAt comme timestamp pour la cohérence
                            latitude = latitude,
                            longitude = longitude
                        )
                        postList.add(post)
                    } catch (e: Exception) {
                        Log.e("PostViewModel", "Error parsing post: ${e.message}")
                    }
                }
                _posts.value = postList
                _isLoading.value = false
            } catch (exception: Exception) {
                _errorMessage.value = "Error loading posts: ${exception.message}"
                _isLoading.value = false
            }
        }
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(PostViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return PostViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    override fun onCleared() {
        super.onCleared()
        postsListenerRegistration?.remove()
    }
}

